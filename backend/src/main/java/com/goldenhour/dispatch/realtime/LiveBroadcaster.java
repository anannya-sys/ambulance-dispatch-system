package com.goldenhour.dispatch.realtime;

import com.goldenhour.dispatch.api.dto.LiveSnapshot;
import com.goldenhour.dispatch.config.GoldenHourProperties;
import com.goldenhour.dispatch.corridor.SignalWindow;
import com.goldenhour.dispatch.fleet.Ambulance;
import com.goldenhour.dispatch.fleet.AmbulanceRepository;
import com.goldenhour.dispatch.fleet.AmbulanceStatus;
import com.goldenhour.dispatch.incident.DispatchPriority;
import com.goldenhour.dispatch.incident.IncidentService;
import com.goldenhour.dispatch.live.LiveState;
import com.goldenhour.dispatch.live.LiveTrip;
import com.goldenhour.dispatch.live.Motion;
import com.goldenhour.dispatch.live.SimClock;
import com.goldenhour.dispatch.routing.Route;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Builds the live snapshot (fleet, trips with routes and corridor signals, queue) and pushes it every second. */
@Component
public class LiveBroadcaster {

    static final int MAX_ROUTE_POINTS = 100;

    private final GoldenHourProperties props;
    private final SimClock clock;
    private final LiveState state;
    private final AmbulanceRepository ambulances;
    private final IncidentService incidents;
    private final LivePublisher publisher;
    private volatile LiveSnapshot last;

    public LiveBroadcaster(GoldenHourProperties props, SimClock clock, LiveState state, AmbulanceRepository ambulances,
                           IncidentService incidents, LivePublisher publisher) {
        this.props = props;
        this.clock = clock;
        this.state = state;
        this.ambulances = ambulances;
        this.incidents = incidents;
        this.publisher = publisher;
    }

    @Scheduled(fixedRate = 1000, initialDelay = 4000)
    public void broadcast() {
        if (props.live().enabled()) {
            last = build();
            publisher.snapshot(last);
        }
    }

    public LiveSnapshot latest() {
        return last != null ? last : build();
    }

    public LiveSnapshot build() {
        LocalDateTime now = clock.now();
        List<Ambulance> fleet = ambulances.findAll();
        fleet.sort(Comparator.comparing(Ambulance::getCallSign));
        List<LiveSnapshot.FleetUnit> units = new ArrayList<>();
        int available = 0;
        int busy = 0;
        int out = 0;
        for (Ambulance a : fleet) {
            Motion m = state.motion(a.getId());
            Integer eta = m == null ? null : (int) m.remainingSeconds(now);
            units.add(new LiveSnapshot.FleetUnit(a.getId(), a.getCallSign(), a.getKind(), a.getStatus().name(),
                    a.getLat(), a.getLon(), a.getHeading(), a.getCurrentTripId(), eta, a.getTargetPostId()));
            if (a.getStatus().isDispatchable()) {
                available++;
            } else if (a.getStatus() == AmbulanceStatus.OUT_OF_SERVICE) {
                out++;
            } else {
                busy++;
            }
        }
        List<LiveSnapshot.ActiveTrip> trips = state.trips().stream()
                .sorted(Comparator.comparing(LiveTrip::callAt))
                .map(t -> trip(t, now)).toList();
        List<LiveSnapshot.QueuedCall> queue = incidents.queue().stream().map(i -> {
            double waited = SimClock.secondsBetween(i.getCreatedAt(), now);
            return new LiveSnapshot.QueuedCall(i.getId(), i.getSeverity().name(), i.getPatientType().name(),
                    i.getAreaName(), i.getLat(), i.getLon(), (int) waited, DispatchPriority.score(i.getSeverity(), waited));
        }).toList();
        double util = fleet.isEmpty() ? 0 : 100.0 * busy / Math.max(1, fleet.size() - out);
        return new LiveSnapshot(now.withNano(0).toString(), clock.speed(),
                new LiveSnapshot.Kpis(trips.size(), queue.size(), available, busy, out, util), units, trips, queue);
    }

    private LiveSnapshot.ActiveTrip trip(LiveTrip t, LocalDateTime now) {
        Motion m = state.motion(t.ambulanceId());
        List<double[]> route = List.of();
        List<LiveSnapshot.CorridorSignal> signals = List.of();
        Integer eta = null;
        if (m != null && m.tripId() != null && m.tripId() == t.tripId()) {
            double elapsed = m.elapsed(now);
            route = remaining(m.route(), elapsed);
            eta = (int) m.remainingSeconds(now);
            signals = m.corridor().stream().map(w -> signal(w, now)).toList();
        }
        return new LiveSnapshot.ActiveTrip(t.tripId(), t.incidentId(), t.ambulanceId(), t.callSign(),
                t.severity().name(), t.patientType().name(), t.area(), t.phase().name(), t.incidentLat(),
                t.incidentLon(), t.hospitalId(), t.hospitalName(), t.hospitalReason(), eta,
                (int) SimClock.secondsBetween(t.callAt(), now), t.corridor(), route, signals,
                t.hospitalId() == null ? null : t.treatmentEtaMinutes());
    }

    private static LiveSnapshot.CorridorSignal signal(SignalWindow w, LocalDateTime now) {
        String s = now.isAfter(w.greenUntil()) ? "CLEARED" : now.isBefore(w.greenFrom()) ? "PENDING" : "GREEN";
        return new LiveSnapshot.CorridorSignal(w.lat(), w.lon(), s);
    }

    /** The part of the route still ahead, thinned to at most {@value #MAX_ROUTE_POINTS} points. */
    static List<double[]> remaining(Route r, double elapsed) {
        List<double[]> ahead = new ArrayList<>();
        ahead.add(r.positionAt(elapsed));
        double[] secs = r.secondsAt();
        for (int i = 0; i < secs.length; i++) {
            if (secs[i] > elapsed) {
                ahead.add(r.points().get(i));
            }
        }
        if (ahead.size() <= MAX_ROUTE_POINTS) {
            return ahead.stream().map(p -> new double[]{round(p[0]), round(p[1])}).toList();
        }
        List<double[]> thin = new ArrayList<>();
        double step = (ahead.size() - 1) / (double) (MAX_ROUTE_POINTS - 1);
        for (int i = 0; i < MAX_ROUTE_POINTS; i++) {
            double[] p = ahead.get((int) Math.round(i * step));
            thin.add(new double[]{round(p[0]), round(p[1])});
        }
        return thin;
    }

    private static double round(double v) {
        return Math.round(v * 1e5) / 1e5;
    }
}
