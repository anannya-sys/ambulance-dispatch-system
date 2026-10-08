package com.goldenhour.dispatch.hospital;

import com.goldenhour.dispatch.incident.Severity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * USP 2 - time-to-treatment routing.
 *
 * <p>The usual rule is "go to the nearest hospital". That fails when the nearest one has no cath lab,
 * no free ICU bed, a packed ER or no specialist on duty: the patient is turned away or transferred and
 * loses the time that matters most. We instead estimate, for every hospital, when treatment would
 * actually <i>start</i>:</p>
 *
 * <pre>
 *   time to treatment = travel + ER wait + specialist call-in delay + handover
 * </pre>
 *
 * <p>and pick the minimum among hospitals that can treat the patient and have a bed of the right type.</p>
 */
public final class HospitalSelector {

    /** Average minutes an ER bay needs per patient before the next one is seen. */
    static final double ER_MINUTES_PER_PATIENT = 12;
    /** Triage: critical patients jump most of the ER queue. */
    static final double CRITICAL_QUEUE_SHARE = 0.25;
    /** Calling an off-duty specialist in from home. */
    public static final double SPECIALIST_CALL_IN_SECONDS = 30 * 60;

    private HospitalSelector() {
    }

    public static TreatmentEstimate estimate(HospitalView h, CareRequirement need, Severity severity,
                                             double travelSeconds, LocalDateTime arrival) {
        return estimate(h, need, severity, travelSeconds, arrival, true);
    }

    /**
     * @param checkBeds false once the patient is already admitted (bed reserved or forced admission),
     *                  so only the ER wait and specialist delay matter
     */
    public static TreatmentEstimate estimate(HospitalView h, CareRequirement need, Severity severity,
                                             double travelSeconds, LocalDateTime arrival, boolean checkBeds) {
        if (need.capability() != null && !h.has(need.capability())) {
            return new TreatmentEstimate(h, travelSeconds, 0, 0, false,
                    "no " + need.capability().name().toLowerCase().replace('_', ' '));
        }
        if (checkBeds && h.freeBeds(need.bed()) <= 0) {
            return new TreatmentEstimate(h, travelSeconds, 0, 0, false,
                    "no free " + need.bed().name().toLowerCase() + " bed");
        }
        double queue = h.erQueue() * (severity == Severity.CRITICAL ? CRITICAL_QUEUE_SHARE : 1.0);
        double erWait = queue / Math.max(1, h.erBays()) * ER_MINUTES_PER_PATIENT * 60;
        double specialistDelay = 0;
        String reason = "ready";
        if (need.specialist() != null && !h.onDuty(need.specialist(), arrival)) {
            specialistDelay = SPECIALIST_CALL_IN_SECONDS;
            reason = need.specialist().name().toLowerCase().replace('_', ' ') + " on call (+30 min)";
        } else if (erWait > 10 * 60) {
            reason = "busy ER";
        }
        return new TreatmentEstimate(h, travelSeconds, erWait, specialistDelay, true, reason);
    }

    /**
     * All hospitals ranked best-first by time to treatment; infeasible ones are listed last (useful for
     * explaining the decision on the dashboard).
     */
    public static List<TreatmentEstimate> rank(List<? extends HospitalView> hospitals, CareRequirement need,
                                               Severity severity, ToDoubleFunction<HospitalView> travelSeconds,
                                               LocalDateTime now) {
        List<TreatmentEstimate> out = new ArrayList<>(hospitals.size());
        for (HospitalView h : hospitals) {
            double travel = travelSeconds.applyAsDouble(h);
            out.add(estimate(h, need, severity, travel, now.plusSeconds((long) travel)));
        }
        out.sort(Comparator.comparing((TreatmentEstimate e) -> !e.feasible())
                .thenComparingDouble(TreatmentEstimate::timeToTreatmentSeconds));
        return out;
    }

    /** Time-to-treatment choice. Falls back to the nearest capable hospital if every one is full. */
    public static TreatmentEstimate best(List<? extends HospitalView> hospitals, CareRequirement need,
                                         Severity severity, ToDoubleFunction<HospitalView> travelSeconds,
                                         LocalDateTime now) {
        List<TreatmentEstimate> ranked = rank(hospitals, need, severity, travelSeconds, now);
        if (ranked.get(0).feasible()) {
            return ranked.get(0);
        }
        // Everything is full: take the closest hospital with the right facility; it will make room.
        return ranked.stream()
                .filter(e -> need.capability() == null || e.hospital().has(need.capability()))
                .min(Comparator.comparingDouble(TreatmentEstimate::travelSeconds))
                .orElse(ranked.get(0));
    }

    /**
     * The traditional rule used as the baseline: the nearest hospital that has the right facility
     * (crews know which hospitals have a cath lab), but with no live view of beds, ER load or who is on duty.
     */
    public static HospitalView nearestCapable(List<? extends HospitalView> hospitals, CareRequirement need,
                                              ToDoubleFunction<HospitalView> travelSeconds) {
        HospitalView best = null;
        HospitalView bestAny = null;
        for (HospitalView h : hospitals) {
            double t = travelSeconds.applyAsDouble(h);
            if (bestAny == null || t < travelSeconds.applyAsDouble(bestAny)) {
                bestAny = h;
            }
            boolean capable = need.capability() == null || h.has(need.capability());
            if (capable && (best == null || t < travelSeconds.applyAsDouble(best))) {
                best = h;
            }
        }
        return best != null ? best : bestAny;
    }
}
