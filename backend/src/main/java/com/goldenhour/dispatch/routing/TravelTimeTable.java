package com.goldenhour.dispatch.routing;

import java.util.EnumMap;
import java.util.Map;

/**
 * Precomputed travel time from a fixed set of places (hospitals, standby posts) to <b>every</b> road
 * node, one table per traffic band.
 *
 * <p>Trade-off: 3 bands x 32 hospitals x 64k nodes x 4 bytes ≈ 25 MB of memory, built once in about
 * a second at startup, and in return "how far is every hospital from this patient?" becomes 32 array
 * reads instead of a graph search. This is what keeps hospital selection and the 30-day simulation fast.</p>
 */
public final class TravelTimeTable {

    private final Map<TrafficBand, float[][]> tables = new EnumMap<>(TrafficBand.class);
    private final int[] sourceNodes;

    public TravelTimeTable(Router router, int[] sourceNodes) {
        this.sourceNodes = sourceNodes.clone();
        for (TrafficBand band : TrafficBand.values()) {
            float[][] t = new float[sourceNodes.length][];
            // Roads are two-way, so time(source -> node) == time(node -> source).
            java.util.stream.IntStream.range(0, sourceNodes.length).parallel()
                    .forEach(i -> t[i] = router.fullTree(sourceNodes[i], band));
            tables.put(band, t);
        }
    }

    public int size() {
        return sourceNodes.length;
    }

    public int sourceNode(int index) {
        return sourceNodes[index];
    }

    /** Seconds between source {@code index} and road node {@code node}. */
    public double seconds(int index, int node, TrafficBand band) {
        return tables.get(band)[index][node];
    }
}
