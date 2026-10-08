package com.goldenhour.dispatch.routing;

/**
 * OpenStreetMap road class. The ordinal matches the byte written by tools/build_road_graph.py.
 * Free-flow speeds are for an ambulance with siren on Mumbai roads, not legal speed limits.
 */
public enum RoadClass {
    MOTORWAY(60),
    TRUNK(50),
    PRIMARY(40),
    SECONDARY(32),
    TERTIARY(28),
    UNCLASSIFIED(22),
    RESIDENTIAL(18);

    private final double freeFlowKmph;

    RoadClass(double freeFlowKmph) {
        this.freeFlowKmph = freeFlowKmph;
    }

    public double freeFlowMetersPerSecond() {
        return freeFlowKmph / 3.6;
    }

    /** Arterial roads are the ones that clog at rush hour. */
    public boolean isArterial() {
        return ordinal() <= SECONDARY.ordinal();
    }

    private static final RoadClass[] VALUES = values();

    public static RoadClass fromOrdinal(int ordinal) {
        return VALUES[ordinal];
    }
}
