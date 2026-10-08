package com.goldenhour.dispatch.hospital;

import com.goldenhour.dispatch.config.GoldenHourProperties;
import com.goldenhour.dispatch.live.SimClock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;

/**
 * Plays the hospitals' own patients: walk-ins take beds, patients get discharged or moved to wards, ER
 * queues rise and fall. In a real deployment this feed would come from each hospital's bed-management
 * system (or a state bed dashboard) over an API.
 */
@Component
public class HospitalCensusSimulator {

    /** Mean hours an emergency bed stays occupied (same assumptions as the offline simulation). */
    static final Map<BedType, Double> STAY_HOURS = Map.of(
            BedType.ICU, 10.0, BedType.CARDIAC, 8.0, BedType.TRAUMA, 8.0, BedType.GENERAL, 4.0);
    static final double BACKGROUND_OCCUPANCY = 0.55;

    private final GoldenHourProperties props;
    private final HospitalRepository hospitals;
    private final SimClock clock;
    private final TransactionTemplate tx;
    private final Random rnd = new Random(3);
    private LocalDateTime last;

    public HospitalCensusSimulator(GoldenHourProperties props, HospitalRepository hospitals, SimClock clock,
                                   TransactionTemplate tx) {
        this.props = props;
        this.hospitals = hospitals;
        this.clock = clock;
        this.tx = tx;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void update() {
        LocalDateTime now = clock.now();
        if (last == null || !props.live().enabled()) {
            last = now;
            return;
        }
        double hours = SimClock.secondsBetween(last, now) / 3600;
        last = now;
        tx.executeWithoutResult(s -> hospitals.findAll().forEach(h -> {
            for (BedType t : BedType.values()) {
                int total = h.totalBeds(t);
                if (total == 0) {
                    continue;
                }
                int occupied = total - h.freeBeds(t);
                double stay = STAY_HOURS.get(t);
                int discharged = binomial(occupied, Math.min(1, hours / stay));
                int admitted = poisson(total * BACKGROUND_OCCUPANCY * hours / stay);
                h.setFree(t, h.freeBeds(t) + discharged - admitted);
            }
            // ER queue drifts towards a typical level for the hospital's size
            int target = h.erBays() + (rnd.nextInt(3) - 1);
            if (h.erQueue() > target && rnd.nextDouble() < Math.min(1, hours * 6)) {
                h.setErQueue(h.erQueue() - 1);
            } else if (h.erQueue() < target && rnd.nextDouble() < Math.min(1, hours * 4)) {
                h.setErQueue(h.erQueue() + 1);
            }
        }));
    }

    private int binomial(int n, double p) {
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (rnd.nextDouble() < p) {
                k++;
            }
        }
        return k;
    }

    private int poisson(double mean) {
        int k = 0;
        double l = Math.exp(-mean);
        double p = rnd.nextDouble();
        while (p > l) {
            k++;
            p *= rnd.nextDouble();
        }
        return k;
    }
}
