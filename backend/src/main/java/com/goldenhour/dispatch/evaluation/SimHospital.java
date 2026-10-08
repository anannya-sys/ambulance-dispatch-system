package com.goldenhour.dispatch.evaluation;

import com.goldenhour.dispatch.hospital.BedType;
import com.goldenhour.dispatch.hospital.Capability;
import com.goldenhour.dispatch.hospital.DutyRoster;
import com.goldenhour.dispatch.hospital.HospitalSite;
import com.goldenhour.dispatch.hospital.HospitalView;
import com.goldenhour.dispatch.hospital.Specialist;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * In-memory hospital for the offline simulation.
 *
 * <p>Besides our ambulance patients, walk-in and referral patients keep arriving (a Poisson stream per
 * bed type) and stay for a random length of stay. That background load is driven by its own seeded
 * random stream, so every policy being compared sees exactly the same hospital crowding.</p>
 */
final class SimHospital implements HospitalView {

    /** Mean hours an emergency (receiving) bed stays occupied before the patient moves to a ward or home. */
    static final Map<BedType, Double> MEAN_STAY_HOURS = Map.of(
            BedType.ICU, 10.0, BedType.CARDIAC, 8.0, BedType.TRAUMA, 8.0, BedType.GENERAL, 4.0);
    /** Background load as a share of capacity. */
    static final double BACKGROUND_OCCUPANCY = 0.55;

    final HospitalSite site;
    private final Random rnd;
    private final Map<BedType, PriorityQueue<Double>> releases = new EnumMap<>(BedType.class);
    private final Map<BedType, Double> nextBackground = new EnumMap<>(BedType.class);
    private final Deque<Double> recentArrivals = new ArrayDeque<>();
    private final LocalDateTime epoch;
    private double now;

    SimHospital(HospitalSite site, long seed, LocalDateTime epoch) {
        this.site = site;
        this.rnd = new Random(seed * 1_000 + site.index());
        this.epoch = epoch;
        for (BedType t : BedType.values()) {
            PriorityQueue<Double> q = new PriorityQueue<>();
            int cap = site.beds().get(t);
            // start in steady state: ~55% full with staggered discharge times
            for (int i = 0; i < Math.round(cap * BACKGROUND_OCCUPANCY); i++) {
                q.add(rnd.nextDouble() * 2 * MEAN_STAY_HOURS.get(t) * 3600);
            }
            releases.put(t, q);
            nextBackground.put(t, interArrival(t));
        }
    }

    private double interArrival(BedType t) {
        int cap = site.beds().get(t);
        if (cap == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double perHour = cap * BACKGROUND_OCCUPANCY / MEAN_STAY_HOURS.get(t);
        return -Math.log(1 - rnd.nextDouble()) / perHour * 3600;
    }

    double stay(BedType t) {
        return MEAN_STAY_HOURS.get(t) * 3600 * (0.5 + rnd.nextDouble());
    }

    /** Move this hospital's clock to {@code t} seconds, admitting and discharging background patients. */
    void advance(double t) {
        for (BedType type : BedType.values()) {
            PriorityQueue<Double> q = releases.get(type);
            double next = nextBackground.get(type);
            while (next <= t) {
                while (!q.isEmpty() && q.peek() <= next) {
                    q.poll();
                }
                double los = stay(type);
                if (q.size() < site.beds().get(type)) {
                    q.add(next + los);
                }
                next += interArrival(type);
            }
            nextBackground.put(type, next);
            while (!q.isEmpty() && q.peek() <= t) {
                q.poll();
            }
        }
        while (!recentArrivals.isEmpty() && recentArrivals.peekFirst() < t - 1800) {
            recentArrivals.pollFirst();
        }
        now = t;
    }

    /** Admit one of our patients until {@code releaseAt}; may over-fill (patient on a trolley). */
    void occupy(BedType type, double releaseAt) {
        releases.get(type).add(releaseAt);
    }

    void recordArrival(double t) {
        recentArrivals.addLast(t);
    }

    @Override
    public long id() {
        return site.index();
    }

    @Override
    public String name() {
        return site.name();
    }

    @Override
    public int tableIndex() {
        return site.index();
    }

    @Override
    public boolean has(Capability capability) {
        return site.capabilities().contains(capability);
    }

    @Override
    public int freeBeds(BedType bed) {
        return site.beds().get(bed) - releases.get(bed).size();
    }

    /** Background ER crowding (varies by hour, deterministic) plus our own recent arrivals. */
    @Override
    public int erQueue() {
        long hour = (long) (now / 3600);
        long h = (site.index() * 2654435761L + hour * 40503L) & 0xffff;
        int background = (int) (h % (site.erBays() * 2 + 1));
        return background + recentArrivals.size();
    }

    @Override
    public int erBays() {
        return site.erBays();
    }

    @Override
    public boolean onDuty(Specialist specialist, LocalDateTime at) {
        return DutyRoster.onDuty(site.index(), specialist, at, site.specialists24x7());
    }

    LocalDateTime time(double seconds) {
        return epoch.plusSeconds((long) seconds);
    }
}
