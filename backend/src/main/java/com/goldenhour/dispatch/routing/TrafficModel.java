package com.goldenhour.dispatch.routing;

/**
 * Converts road class + traffic band into travel time.
 *
 * <p>Arterial roads (where office traffic piles up) slow down the most at peak; small residential
 * roads are slow all day but don't degrade as much. Each signalised junction adds an expected wait,
 * which a green corridor almost removes. These numbers are assumptions, documented in the README.</p>
 */
public final class TrafficModel {

    /** Average wait at a signalised junction for an ambulance in normal traffic. */
    public static final double SIGNAL_DELAY_SECONDS = 25.0;
    /** Remaining wait when police/signals have been pre-cleared (green corridor). */
    public static final double CORRIDOR_SIGNAL_DELAY_SECONDS = 3.0;

    private TrafficModel() {
    }

    /** Fraction of free-flow speed achievable. */
    public static double speedFactor(TrafficBand band, RoadClass roadClass) {
        return switch (band) {
            case NIGHT -> 1.0;
            case OFF_PEAK -> roadClass.isArterial() ? 0.72 : 0.85;
            case PEAK -> roadClass.isArterial() ? 0.48 : 0.70;
        };
    }

    public static double signalDelay(boolean greenCorridor) {
        return greenCorridor ? CORRIDOR_SIGNAL_DELAY_SECONDS : SIGNAL_DELAY_SECONDS;
    }
}
