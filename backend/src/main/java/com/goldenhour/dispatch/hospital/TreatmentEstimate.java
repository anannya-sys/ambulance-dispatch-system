package com.goldenhour.dispatch.hospital;

/**
 * How long until treatment would start if the patient went to {@code hospital}.
 *
 * @param feasible false when the hospital lacks the facility or has no bed of the right type
 * @param reason   human-readable note for the dashboard ("no cath lab", "cardiologist on call +30 min")
 */
public record TreatmentEstimate(HospitalView hospital, double travelSeconds, double erWaitSeconds,
                                double specialistDelaySeconds, boolean feasible, String reason) {

    public static final double HANDOVER_SECONDS = 5 * 60;

    public double timeToTreatmentSeconds() {
        return travelSeconds + erWaitSeconds + specialistDelaySeconds + HANDOVER_SECONDS;
    }
}
