package com.goldenhour.dispatch.routing;

import com.goldenhour.dispatch.geo.GeoUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable road network of Mumbai held in flat primitive arrays.
 *
 * <p>Why arrays instead of Node/Edge objects? 64k nodes and 80k edges as objects would cost
 * tens of MB of headers and pointer chasing. Arrays in "compressed sparse row" (CSR) form keep the
 * whole graph in a few MB and make Dijkstra/A* cache friendly.</p>
 *
 * <ul>
 *   <li>{@code adjStart[n] .. adjStart[n+1]} indexes into {@code adjEdge}: the edges touching node n.</li>
 *   <li>Edges are undirected (two-way roads); {@link #otherEnd(int, int)} gives the neighbour.</li>
 *   <li>Each edge has shape points so moving ambulances follow the real curve of the road.</li>
 * </ul>
 */
public final class RoadGraph {

    final double[] lat;
    final double[] lon;
    final int[] edgeFrom;
    final int[] edgeTo;
    final byte[] edgeClass;
    final int[] edgeName;
    final float[] edgeLength;
    final int[] shapeStart;
    final double[] shapeLat;
    final double[] shapeLon;
    final int[] adjStart;
    final int[] adjEdge;
    final boolean[] signal;
    final String[] names;
    private final SpatialIndex index;

    RoadGraph(double[] lat, double[] lon, int[] edgeFrom, int[] edgeTo, byte[] edgeClass, int[] edgeName,
              float[] edgeLength, int[] shapeStart, double[] shapeLat, double[] shapeLon,
              boolean[] signal, String[] names) {
        this.lat = lat;
        this.lon = lon;
        this.edgeFrom = edgeFrom;
        this.edgeTo = edgeTo;
        this.edgeClass = edgeClass;
        this.edgeName = edgeName;
        this.edgeLength = edgeLength;
        this.shapeStart = shapeStart;
        this.shapeLat = shapeLat;
        this.shapeLon = shapeLon;
        this.signal = signal;
        this.names = names;

        // Build the CSR adjacency: count degrees, prefix-sum, then fill.
        int n = lat.length;
        adjStart = new int[n + 1];
        for (int e = 0; e < edgeFrom.length; e++) {
            adjStart[edgeFrom[e] + 1]++;
            adjStart[edgeTo[e] + 1]++;
        }
        for (int i = 0; i < n; i++) {
            adjStart[i + 1] += adjStart[i];
        }
        adjEdge = new int[edgeFrom.length * 2];
        int[] fill = adjStart.clone();
        for (int e = 0; e < edgeFrom.length; e++) {
            adjEdge[fill[edgeFrom[e]]++] = e;
            adjEdge[fill[edgeTo[e]]++] = e;
        }
        index = new SpatialIndex(lat, lon, degree());
    }

    private int[] degree() {
        int[] d = new int[lat.length];
        for (int i = 0; i < d.length; i++) {
            d[i] = adjStart[i + 1] - adjStart[i];
        }
        return d;
    }

    public int nodeCount() {
        return lat.length;
    }

    public int edgeCount() {
        return edgeFrom.length;
    }

    public double lat(int node) {
        return lat[node];
    }

    public double lon(int node) {
        return lon[node];
    }

    /** The most important road class touching {@code node} (lower ordinal = bigger road). */
    public RoadClass bestClassAt(int node) {
        int best = RoadClass.RESIDENTIAL.ordinal();
        for (int k = adjStart[node]; k < adjStart[node + 1]; k++) {
            best = Math.min(best, edgeClass[adjEdge[k]]);
        }
        return RoadClass.fromOrdinal(best);
    }

    public boolean isSignal(int node) {
        return signal[node];
    }

    public int otherEnd(int edge, int node) {
        return edgeFrom[edge] == node ? edgeTo[edge] : edgeFrom[edge];
    }

    public RoadClass roadClass(int edge) {
        return RoadClass.fromOrdinal(edgeClass[edge]);
    }

    public double edgeLength(int edge) {
        return edgeLength[edge];
    }

    public String edgeName(int edge) {
        int i = edgeName[edge];
        return i < 0 ? null : names[i];
    }

    /** Snap a coordinate to the closest junction that has at least one road. */
    public int nearestNode(double latitude, double longitude) {
        return index.nearest(latitude, longitude);
    }

    public double distanceMeters(int a, int b) {
        return GeoUtils.distanceMeters(lat[a], lon[a], lat[b], lon[b]);
    }

    public List<Integer> signalNodes() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < signal.length; i++) {
            if (signal[i]) {
                out.add(i);
            }
        }
        return out;
    }

    /** Bounding box [minLat, minLon, maxLat, maxLon]. */
    public double[] bounds() {
        double minLat = 90, minLon = 180, maxLat = -90, maxLon = -180;
        for (int i = 0; i < lat.length; i++) {
            minLat = Math.min(minLat, lat[i]);
            maxLat = Math.max(maxLat, lat[i]);
            minLon = Math.min(minLon, lon[i]);
            maxLon = Math.max(maxLon, lon[i]);
        }
        return new double[]{minLat, minLon, maxLat, maxLon};
    }

    /** Shape of an edge as [lat, lon] pairs, oriented starting at {@code fromNode}. */
    public List<double[]> edgeShape(int edge, int fromNode) {
        int s = shapeStart[edge];
        int e = shapeStart[edge + 1];
        List<double[]> pts = new ArrayList<>(e - s);
        if (edgeFrom[edge] == fromNode) {
            for (int i = s; i < e; i++) {
                pts.add(new double[]{shapeLat[i], shapeLon[i]});
            }
        } else {
            for (int i = e - 1; i >= s; i--) {
                pts.add(new double[]{shapeLat[i], shapeLon[i]});
            }
        }
        return pts;
    }

    /**
     * Polylines of the roads at or above {@code minimumClass} (e.g. TERTIARY), for drawing an offline
     * base map when map tiles can't be downloaded.
     */
    public List<double[][]> polylines(RoadClass minimumClass) {
        List<double[][]> out = new ArrayList<>();
        for (int e = 0; e < edgeFrom.length; e++) {
            if (edgeClass[e] <= minimumClass.ordinal()) {
                int s = shapeStart[e];
                int end = shapeStart[e + 1];
                double[][] line = new double[end - s][];
                for (int i = s; i < end; i++) {
                    line[i - s] = new double[]{round5(shapeLat[i]), round5(shapeLon[i])};
                }
                out.add(line);
            }
        }
        return out;
    }

    private static double round5(double v) {
        return Math.round(v * 1e5) / 1e5;
    }

    /** Uniform grid of buckets over the city for fast nearest-junction lookups. */
    private static final class SpatialIndex {
        private static final double CELL = 0.004; // ~450 m
        private final double minLat;
        private final double minLon;
        private final int rows;
        private final int cols;
        private final int[] cellStart;
        private final int[] cellNodes;
        private final double[] lat;
        private final double[] lon;

        SpatialIndex(double[] lat, double[] lon, int[] degree) {
            this.lat = lat;
            this.lon = lon;
            double mnLat = 90, mnLon = 180, mxLat = -90, mxLon = -180;
            for (int i = 0; i < lat.length; i++) {
                mnLat = Math.min(mnLat, lat[i]);
                mnLon = Math.min(mnLon, lon[i]);
                mxLat = Math.max(mxLat, lat[i]);
                mxLon = Math.max(mxLon, lon[i]);
            }
            minLat = mnLat;
            minLon = mnLon;
            rows = (int) ((mxLat - mnLat) / CELL) + 1;
            cols = (int) ((mxLon - mnLon) / CELL) + 1;
            cellStart = new int[rows * cols + 1];
            for (int i = 0; i < lat.length; i++) {
                if (degree[i] > 0) {
                    cellStart[cell(lat[i], lon[i]) + 1]++;
                }
            }
            for (int c = 0; c < rows * cols; c++) {
                cellStart[c + 1] += cellStart[c];
            }
            cellNodes = new int[cellStart[rows * cols]];
            int[] fill = cellStart.clone();
            for (int i = 0; i < lat.length; i++) {
                if (degree[i] > 0) {
                    cellNodes[fill[cell(lat[i], lon[i])]++] = i;
                }
            }
        }

        private int row(double la) {
            return Math.max(0, Math.min(rows - 1, (int) ((la - minLat) / CELL)));
        }

        private int col(double lo) {
            return Math.max(0, Math.min(cols - 1, (int) ((lo - minLon) / CELL)));
        }

        private int cell(double la, double lo) {
            return row(la) * cols + col(lo);
        }

        int nearest(double la, double lo) {
            int r0 = row(la);
            int c0 = col(lo);
            int best = -1;
            double bestD = Double.MAX_VALUE;
            // Grow the search ring until we have a hit and the ring is farther than the best hit.
            for (int ring = 0; ring < Math.max(rows, cols); ring++) {
                for (int r = r0 - ring; r <= r0 + ring; r++) {
                    for (int c = c0 - ring; c <= c0 + ring; c++) {
                        if (r < 0 || c < 0 || r >= rows || c >= cols) {
                            continue;
                        }
                        if (Math.max(Math.abs(r - r0), Math.abs(c - c0)) != ring) {
                            continue;
                        }
                        int cell = r * cols + c;
                        for (int k = cellStart[cell]; k < cellStart[cell + 1]; k++) {
                            int n = cellNodes[k];
                            double d = GeoUtils.distanceMeters(la, lo, lat[n], lon[n]);
                            if (d < bestD) {
                                bestD = d;
                                best = n;
                            }
                        }
                    }
                }
                if (best >= 0 && bestD < ring * CELL * 100_000) {
                    break;
                }
            }
            return best;
        }
    }
}
