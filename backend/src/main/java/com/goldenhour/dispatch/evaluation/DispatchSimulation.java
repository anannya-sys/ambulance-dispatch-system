package com.goldenhour.dispatch.evaluation;

import com.goldenhour.dispatch.forecast.DemandModel;
import com.goldenhour.dispatch.forecast.PrePositioningPlanner;
import com.goldenhour.dispatch.forecast.StandbyPost;
import com.goldenhour.dispatch.forecast.SyntheticCall;
import com.goldenhour.dispatch.hospital.CareRequirement;
import com.goldenhour.dispatch.hospital.HospitalSelector;
import com.goldenhour.dispatch.hospital.HospitalView;
import com.goldenhour.dispatch.hospital.TreatmentEstimate;
import com.goldenhour.dispatch.incident.DispatchPriority;
import com.goldenhour.dispatch.incident.Severity;
import com.goldenhour.dispatch.routing.CityModel;
import com.goldenhour.dispatch.routing.TrafficBand;
import com.goldenhour.dispatch.routing.TravelTimeTable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

/**
 * Discrete-event simulation of one city fleet over a period of calls.
 *
 * <p>Instead of ticking every second, we keep a priority queue of future events (call arrives, ambulance
 * reaches scene, leaves scene, reaches hospital, becomes free, rebalance) and jump straight from one to
 * the next. Two weeks of a 50-ambulance city run in a few seconds.</p>
 *
 * <p>Both policies get identical inputs: the same calls, the same hospital background load and the same
 * random scene times. Only the decisions differ, so the gap in the results is caused by the decisions.</p>
 */
public final class DispatchSimulation {

    /** Call taking: from the phone ringing to the dispatch decision. */
    static final double CALL_PROCESSING_SECONDS = 60;
    /** Crew gets into the vehicle. */
    static final double TURNOUT_SECONDS = 45;
    /** Handover and cleaning at the hospital before the ambulance is free. */
    static final double HANDOVER_SECONDS = 10 * 60;
    /** Wasted at a hospital that turns the patient away. */
    static final double TURN_AWAY_SECONDS = 10 * 60;
    /** Stabilising before a transfer from a hospital without the right facility. */
    static final double TRANSFER_PREP_SECONDS = 20 * 60;
    static final int MAX_HOPS = 3;
    static final double REBALANCE_EVERY_SECONDS = 10 * 60;

    private final CityModel city;
    private final SimConfig config;
    private final List<SyntheticCall> calls;
    private final DemandModel model;
    private final LocalDateTime epoch;
    private final long seed;
    private final List<SimHospital> hospitals = new ArrayList<>();
    private final List<Unit> fleet = new ArrayList<>();
    private final PriorityQueue<Event> events = new PriorityQueue<>();
    private final List<Integer> waiting = new ArrayList<>();
    private final Trip[] trips;
    private final Map<Long, List<DemandModel.CellForecast>> forecastCache = new HashMap<>();
    private long eventSeq;

    /** Per-run fleet member. */
    private static final class Unit {
        final long id;
        final StandbyPost home;
        int node;
        boolean busy;
        double busySeconds;
        int repoFrom = -1;
        int repoTo = -1;
        double repoDepart;
        double repoArrive;

        Unit(long id, StandbyPost home) {
            this.id = id;
            this.home = home;
            this.node = home.node();
        }

        int nodeAt(double t) {
            if (repoTo < 0) {
                return node;
            }
            if (t >= repoArrive) {
                node = repoTo;
                repoTo = -1;
                return node;
            }
            double f = (t - repoDepart) / Math.max(1, repoArrive - repoDepart);
            return f < 0.5 ? repoFrom : repoTo;
        }
    }

    /** Per-call bookkeeping. */
    private static final class Trip {
        Unit unit;
        int hospital = -1;
        boolean reserved;
        int hops;
        boolean refused;
        double dispatchedAt;
        double sceneAt;
        double treatmentAt;
        Set<Integer> visited = new HashSet<>();
    }

    private enum Kind { CALL, AT_SCENE, LEAVE_SCENE, AT_HOSPITAL, FREE, REBALANCE }

    private record Event(double t, long seq, Kind kind, int call, Unit unit) implements Comparable<Event> {
        @Override
        public int compareTo(Event o) {
            int c = Double.compare(t, o.t);
            return c != 0 ? c : Long.compare(seq, o.seq);
        }
    }

    public DispatchSimulation(CityModel city, SimConfig config, List<SyntheticCall> calls, DemandModel model,
                              List<StandbyPost> homePosts, int fleetSize, LocalDateTime epoch, long seed) {
        this.city = city;
        this.config = config;
        this.calls = calls;
        this.model = model;
        this.epoch = epoch;
        this.seed = seed;
        this.trips = new Trip[calls.size()];
        city.hospitals().forEach(h -> hospitals.add(new SimHospital(h, seed, epoch)));
        for (int i = 0; i < fleetSize; i++) {
            fleet.add(new Unit(i, homePosts.get(i % homePosts.size())));
        }
    }

    private double secs(LocalDateTime t) {
        return java.time.Duration.between(epoch, t).toMillis() / 1000.0;
    }

    private LocalDateTime time(double s) {
        return epoch.plusNanos((long) (s * 1e9));
    }

    private void push(double t, Kind k, int call, Unit u) {
        events.add(new Event(t, eventSeq++, k, call, u));
    }

    public SimResult run() {
        for (int i = 0; i < calls.size(); i++) {
            push(secs(calls.get(i).time()) + CALL_PROCESSING_SECONDS, Kind.CALL, i, null);
        }
        double horizon = calls.isEmpty() ? 0 : secs(calls.get(calls.size() - 1).time());
        if (config.prePositioning()) {
            push(0, Kind.REBALANCE, -1, null);
        }
        while (!events.isEmpty()) {
            Event e = events.poll();
            switch (e.kind()) {
                case CALL -> {
                    waiting.add(e.call());
                    dispatch(e.t());
                }
                case AT_SCENE -> atScene(e.t(), e.call());
                case LEAVE_SCENE -> leaveScene(e.t(), e.call());
                case AT_HOSPITAL -> atHospital(e.t(), e.call());
                case FREE -> {
                    e.unit().busy = false;
                    if (!config.prePositioning()) {
                        returnHome(e.unit(), e.t());
                    }
                    dispatch(e.t());
                }
                case REBALANCE -> {
                    rebalance(e.t());
                    if (e.t() < horizon) {
                        push(e.t() + REBALANCE_EVERY_SECONDS, Kind.REBALANCE, -1, null);
                    }
                }
            }
        }
        return metrics(horizon);
    }

    // ----------------------------------------------------------------- dispatch

    private void dispatch(double t) {
        while (!waiting.isEmpty()) {
            List<Unit> idle = fleet.stream().filter(u -> !u.busy).toList();
            if (idle.isEmpty()) {
                return;
            }
            int pick = nextCall(t);
            SyntheticCall c = calls.get(pick);
            boolean corridor = corridor(c.severity());
            int[] nodes = idle.stream().mapToInt(u -> u.nodeAt(t)).toArray();
            // One Dijkstra from the call outwards; the first ambulance it reaches is the closest by road.
            double[] times = city.router().timesFrom(c.node(), nodes, TrafficBand.at(time(t)), corridor, 1);
            int best = 0;
            for (int i = 1; i < times.length; i++) {
                if (times[i] < times[best]) {
                    best = i;
                }
            }
            Unit u = idle.get(best);
            waiting.remove(Integer.valueOf(pick));
            u.busy = true;
            u.repoTo = -1;
            Trip trip = new Trip();
            trip.unit = u;
            trip.dispatchedAt = t;
            trips[pick] = trip;
            double arrive = t + TURNOUT_SECONDS + times[best];
            u.node = c.node();
            push(arrive, Kind.AT_SCENE, pick, u);
        }
    }

    /** FIFO for the baseline; severity score with aging when the priority queue is on. */
    private int nextCall(double t) {
        if (!config.priorityQueue()) {
            return waiting.get(0);
        }
        int best = waiting.get(0);
        double bestScore = -1;
        for (int i : waiting) {
            SyntheticCall c = calls.get(i);
            double score = DispatchPriority.score(c.severity(), t - secs(c.time()));
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private boolean corridor(Severity s) {
        return config.greenCorridor() && s == Severity.CRITICAL;
    }

    private void atScene(double t, int call) {
        trips[call].sceneAt = t;
        SyntheticCall c = calls.get(call);
        // deterministic per-call variation so every policy gets the same scene time
        double jitter = 0.7 + 0.6 * new Random(seed * 31 + call).nextDouble();
        push(t + c.severity().sceneMinutes() * 60 * jitter, Kind.LEAVE_SCENE, call, trips[call].unit);
    }

    // ----------------------------------------------------------------- hospital

    private void leaveScene(double t, int call) {
        SyntheticCall c = calls.get(call);
        Trip trip = trips[call];
        TrafficBand band = TrafficBand.at(time(t));
        CareRequirement need = CareRequirement.of(c.patientType(), c.severity());
        TravelTimeTable table = city.hospitalTable();
        hospitals.forEach(h -> h.advance(t));
        int target;
        if (config.timeToTreatment()) {
            TreatmentEstimate best = HospitalSelector.best(hospitals, need, c.severity(),
                    h -> table.seconds(h.tableIndex(), c.node(), band), time(t));
            target = best.hospital().tableIndex();
            if (best.feasible()) {
                // pre-alert reserves the bed before the patient arrives
                SimHospital h = hospitals.get(target);
                h.occupy(need.bed(), t + travel(c.node(), target, band, c.severity()) + h.stay(need.bed()));
                trip.reserved = true;
            }
        } else {
            target = HospitalSelector.nearestCapable(hospitals, need, h -> table.seconds(h.tableIndex(), c.node(), band)).tableIndex();
        }
        goTo(trip, call, c.node(), target, t, band, c.severity());
    }

    private double travel(int fromNode, int hospital, TrafficBand band, Severity s) {
        if (corridor(s)) {
            int[] target = {city.hospitals().get(hospital).node()};
            return city.router().timesFrom(fromNode, target, band, true, 1)[0];
        }
        return city.hospitalTable().seconds(hospital, fromNode, band);
    }

    private void goTo(Trip trip, int call, int fromNode, int hospital, double t, TrafficBand band, Severity s) {
        trip.hospital = hospital;
        trip.visited.add(hospital);
        push(t + travel(fromNode, hospital, band, s), Kind.AT_HOSPITAL, call, trip.unit);
    }

    private void atHospital(double t, int call) {
        SyntheticCall c = calls.get(call);
        Trip trip = trips[call];
        SimHospital h = hospitals.get(trip.hospital);
        h.advance(t);
        CareRequirement need = CareRequirement.of(c.patientType(), c.severity());
        boolean capable = need.capability() == null || h.has(need.capability());
        boolean bed = trip.reserved || h.freeBeds(need.bed()) > 0;
        if ((!capable || !bed) && trip.hops < MAX_HOPS) {
            // Hospital hopping: refused for lack of bed, or stabilised and transferred for lack of facility.
            trip.hops++;
            trip.refused = true;
            double delay = capable ? TURN_AWAY_SECONDS : TRANSFER_PREP_SECONDS;
            int next = nextHospital(h, need, trip.visited, t + delay);
            goTo(trip, call, h.site.node(), next, t + delay, TrafficBand.at(time(t + delay)), c.severity());
            return;
        }
        TreatmentEstimate e = HospitalSelector.estimate(h, need, c.severity(), 0, time(t), false);
        trip.treatmentAt = t + e.erWaitSeconds() + e.specialistDelaySeconds() + TreatmentEstimate.HANDOVER_SECONDS;
        if (!trip.reserved) {
            h.occupy(need.bed(), t + h.stay(need.bed()));
        }
        h.recordArrival(t);
        Unit u = trip.unit;
        u.node = h.site.node();
        double free = t + HANDOVER_SECONDS;
        u.busySeconds += free - trip.dispatchedAt;
        push(free, Kind.FREE, call, u);
    }

    /** Crew's next choice after a refusal: nearest unvisited hospital that has the right facility. */
    private int nextHospital(SimHospital from, CareRequirement need, Set<Integer> visited, double t) {
        TrafficBand band = TrafficBand.at(time(t));
        TravelTimeTable table = city.hospitalTable();
        return hospitals.stream()
                .filter(h -> !visited.contains(h.tableIndex()))
                .filter(h -> need.capability() == null || h.has(need.capability()))
                .min((a, b) -> Double.compare(table.seconds(a.tableIndex(), from.site.node(), band),
                        table.seconds(b.tableIndex(), from.site.node(), band)))
                .map(SimHospital::tableIndex)
                .orElse(from.tableIndex());
    }

    // ----------------------------------------------------------------- positioning

    private void returnHome(Unit u, double t) {
        startDrive(u, u.home.node(), city.postTable().seconds(u.home.index(), u.node, TrafficBand.at(time(t))), t);
    }

    private void startDrive(Unit u, int to, double seconds, double t) {
        if (u.node == to) {
            return;
        }
        u.repoFrom = u.node;
        u.repoTo = to;
        u.repoDepart = t;
        u.repoArrive = t + seconds;
    }

    private void rebalance(double t) {
        List<Unit> idle = fleet.stream().filter(u -> !u.busy).toList();
        if (idle.isEmpty()) {
            return;
        }
        LocalDateTime now = time(t);
        TrafficBand band = TrafficBand.at(now);
        long hourKey = (long) (t / 3600);
        List<DemandModel.CellForecast> demand = forecastCache.computeIfAbsent(hourKey,
                k -> model.forecast(now.withMinute(0).withSecond(0).withNano(0), 60));
        List<StandbyPost> posts = PrePositioningPlanner.choosePosts(demand, idle.size(), city.posts(),
                city.postTable(), city.graph(), band);
        List<PrePositioningPlanner.IdleUnit> units = idle.stream()
                .map(u -> new PrePositioningPlanner.IdleUnit(u.id, u.nodeAt(t))).toList();
        Map<Long, StandbyPost> plan = PrePositioningPlanner.assign(units, posts, city.postTable(), band);
        for (Unit u : idle) {
            StandbyPost p = plan.get(u.id);
            if (p != null && u.repoTo != p.node()) {
                u.nodeAt(t);
                startDrive(u, p.node(), city.postTable().seconds(p.index(), u.node, band), t);
            }
        }
    }

    // ----------------------------------------------------------------- metrics

    private SimResult metrics(double horizon) {
        List<Double> resp = new ArrayList<>();
        List<Double> crit = new ArrayList<>();
        List<Double> critQueue = new ArrayList<>();
        int within15 = 0;
        int golden = 0;
        double critTreat = 0;
        int refused = 0;
        for (int i = 0; i < calls.size(); i++) {
            Trip tr = trips[i];
            if (tr == null) {
                continue;
            }
            double callT = secs(calls.get(i).time());
            double r = (tr.sceneAt - callT) / 60;
            resp.add(r);
            if (tr.refused) {
                refused++;
            }
            if (calls.get(i).severity() == Severity.CRITICAL) {
                crit.add(r);
                critQueue.add((tr.dispatchedAt - callT - CALL_PROCESSING_SECONDS) / 60);
                if (r <= 15) {
                    within15++;
                }
                double treat = (tr.treatmentAt - callT) / 60;
                critTreat += treat;
                if (treat <= 60) {
                    golden++;
                }
            }
        }
        double busy = fleet.stream().mapToDouble(u -> u.busySeconds).sum();
        double[] critArr = crit.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return new SimResult(config.name(), seed, resp.size(),
                mean(resp), mean(crit), critArr.length == 0 ? 0 : critArr[(int) (critArr.length * 0.9)],
                100.0 * within15 / Math.max(1, crit.size()), critTreat / Math.max(1, crit.size()),
                100.0 * golden / Math.max(1, crit.size()), refused, mean(critQueue),
                100.0 * busy / (fleet.size() * Math.max(1, horizon)));
    }

    private static double mean(List<Double> xs) {
        return xs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
}
