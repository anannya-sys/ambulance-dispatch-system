package com.goldenhour.dispatch.routing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shortest-time routing over the {@link RoadGraph}.
 *
 * <ul>
 *   <li>{@link #route}: A* from one node to another, returns the full drivable {@link Route}.</li>
 *   <li>{@link #timesFrom}: one Dijkstra from an incident to many ambulances at once ("one-to-many"),
 *       stopping as soon as the closest {@code k} are settled. Much cheaper than k separate A* runs.</li>
 *   <li>{@link #fullTree}: travel time from one node to every node (used to precompute tables).</li>
 * </ul>
 *
 * <p>Roads are two-way, so time(a→b) = time(b→a) and a search from the incident gives ambulance→incident
 * times. Search buffers are reused per thread to avoid allocating 64k-element arrays per call.</p>
 */
public final class Router {

    private final RoadGraph g;
    /** Fastest possible speed in m/s, so the A* heuristic (straight-line / max speed) never overestimates. */
    private final double maxSpeed;
    private final ThreadLocal<Workspace> workspace;

    public Router(RoadGraph graph) {
        this.g = graph;
        this.maxSpeed = RoadClass.MOTORWAY.freeFlowMetersPerSecond();
        this.workspace = ThreadLocal.withInitial(() -> new Workspace(graph.nodeCount()));
    }

    public RoadGraph graph() {
        return g;
    }

    /** Seconds to drive one edge in the given traffic band. */
    public double edgeSeconds(int edge, TrafficBand band) {
        RoadClass c = g.roadClass(edge);
        return g.edgeLength[edge] / (c.freeFlowMetersPerSecond() * TrafficModel.speedFactor(band, c));
    }

    private double nodePenalty(int node, boolean corridor) {
        return g.signal[node] ? TrafficModel.signalDelay(corridor) : 0;
    }

    // ------------------------------------------------------------------ A*

    public Route route(int from, int to, TrafficBand band, boolean greenCorridor) {
        if (from == to) {
            return Route.stationary(g.lat[from], g.lon[from]);
        }
        Workspace w = workspace.get();
        w.reset();
        w.setDist(from, 0);
        w.heap.push(from, heuristic(from, to));
        while (!w.heap.isEmpty()) {
            int u = w.heap.pop();
            if (w.isSettled(u)) {
                continue;
            }
            w.settle(u);
            if (u == to) {
                break;
            }
            relax(w, u, band, greenCorridor, to);
        }
        if (!w.isSettled(to)) {
            throw new IllegalStateException("No road path between nodes " + from + " and " + to);
        }
        return buildRoute(w, from, to, band, greenCorridor);
    }

    private double heuristic(int a, int b) {
        return g.distanceMeters(a, b) / maxSpeed;
    }

    private void relax(Workspace w, int u, TrafficBand band, boolean corridor, int target) {
        double du = w.dist(u);
        for (int k = g.adjStart[u]; k < g.adjStart[u + 1]; k++) {
            int e = g.adjEdge[k];
            int v = g.otherEnd(e, u);
            if (w.isSettled(v)) {
                continue;
            }
            double nd = du + edgeSeconds(e, band) + nodePenalty(v, corridor);
            if (nd < w.dist(v)) {
                w.setDist(v, nd);
                w.parentEdge[v] = e;
                w.heap.push(v, target >= 0 ? nd + heuristic(v, target) : nd);
            }
        }
    }

    private Route buildRoute(Workspace w, int from, int to, TrafficBand band, boolean corridor) {
        // Walk parents back from the target to recover the edge sequence.
        List<Integer> edges = new ArrayList<>();
        for (int n = to; n != from; ) {
            int e = w.parentEdge[n];
            edges.add(e);
            n = g.otherEnd(e, n);
        }
        java.util.Collections.reverse(edges);

        List<double[]> pts = new ArrayList<>();
        List<Double> secs = new ArrayList<>();
        List<Route.SignalPass> signals = new ArrayList<>();
        Map<String, Double> nameMeters = new LinkedHashMap<>();
        pts.add(new double[]{g.lat[from], g.lon[from]});
        secs.add(0.0);
        double t = 0;
        double meters = 0;
        int node = from;
        for (int e : edges) {
            List<double[]> shape = g.edgeShape(e, node);
            double edgeTime = edgeSeconds(e, band);
            double edgeLen = Math.max(1e-6, g.edgeLength[e]);
            double[] prev = shape.get(0);
            double run = 0;
            for (int i = 1; i < shape.size(); i++) {
                double[] p = shape.get(i);
                run += com.goldenhour.dispatch.geo.GeoUtils.distanceMeters(prev[0], prev[1], p[0], p[1]);
                pts.add(p);
                secs.add(t + edgeTime * Math.min(1.0, run / edgeLen));
                prev = p;
            }
            t += edgeTime;
            meters += g.edgeLength[e];
            node = g.otherEnd(e, node);
            String name = g.edgeName(e);
            if (name != null) {
                nameMeters.merge(name, (double) g.edgeLength[e], Double::sum);
            }
            if (g.signal[node]) {
                signals.add(new Route.SignalPass(node, g.lat[node], g.lon[node], t));
                double wait = TrafficModel.signalDelay(corridor);
                t += wait;
                // a stationary point while waiting at the signal
                pts.add(new double[]{g.lat[node], g.lon[node]});
                secs.add(t);
            }
        }
        String via = nameMeters.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(2).map(Map.Entry::getKey)
                .reduce((a, b) -> a + ", " + b).orElse("local roads");
        double[] secArr = secs.stream().mapToDouble(Double::doubleValue).toArray();
        return new Route(pts, secArr, meters, t, signals, via);
    }

    // ------------------------------------------------------------------ Dijkstra one-to-many

    /**
     * Travel seconds from {@code origin} to each of {@code targets}. Stops once {@code stopAfter} targets
     * are settled; unreached targets get {@link Double#POSITIVE_INFINITY}.
     */
    public double[] timesFrom(int origin, int[] targets, TrafficBand band, boolean corridor, int stopAfter) {
        Workspace w = workspace.get();
        w.reset();
        Map<Integer, List<Integer>> targetSlots = new HashMap<>();
        for (int i = 0; i < targets.length; i++) {
            targetSlots.computeIfAbsent(targets[i], k -> new ArrayList<>()).add(i);
        }
        double[] out = new double[targets.length];
        Arrays.fill(out, Double.POSITIVE_INFINITY);
        int need = Math.min(stopAfter, targets.length);
        int found = 0;
        w.setDist(origin, 0);
        w.heap.push(origin, 0);
        while (!w.heap.isEmpty() && found < need) {
            int u = w.heap.pop();
            if (w.isSettled(u)) {
                continue;
            }
            w.settle(u);
            List<Integer> slots = targetSlots.get(u);
            if (slots != null) {
                for (int s : slots) {
                    out[s] = w.dist(u);
                    found++;
                }
            }
            relax(w, u, band, corridor, -1);
        }
        return out;
    }

    /** Travel seconds from {@code origin} to every node (unreachable = +inf). */
    public float[] fullTree(int origin, TrafficBand band) {
        Workspace w = workspace.get();
        w.reset();
        w.setDist(origin, 0);
        w.heap.push(origin, 0);
        while (!w.heap.isEmpty()) {
            int u = w.heap.pop();
            if (w.isSettled(u)) {
                continue;
            }
            w.settle(u);
            relax(w, u, band, false, -1);
        }
        float[] out = new float[g.nodeCount()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (float) w.dist(i);
        }
        return out;
    }

    // ------------------------------------------------------------------ buffers

    /**
     * Per-thread search state. A "generation" stamp marks which entries are valid for the current
     * search, so reset() is O(1) instead of clearing 64k-element arrays.
     */
    private static final class Workspace {
        final double[] dist;
        final int[] parentEdge;
        final int[] stamp;
        final boolean[] settled;
        final MinHeap heap;
        int generation;

        Workspace(int n) {
            dist = new double[n];
            parentEdge = new int[n];
            stamp = new int[n];
            settled = new boolean[n];
            heap = new MinHeap(1024);
        }

        void reset() {
            generation++;
            heap.clear();
        }

        double dist(int n) {
            return stamp[n] == generation ? dist[n] : Double.POSITIVE_INFINITY;
        }

        void setDist(int n, double d) {
            if (stamp[n] != generation) {
                stamp[n] = generation;
                settled[n] = false;
            }
            dist[n] = d;
        }

        boolean isSettled(int n) {
            return stamp[n] == generation && settled[n];
        }

        void settle(int n) {
            settled[n] = true;
        }
    }

    /** Binary min-heap of (node, priority) with lazy deletion (duplicates are skipped when popped). */
    private static final class MinHeap {
        private int[] nodes;
        private double[] keys;
        private int size;

        MinHeap(int capacity) {
            nodes = new int[capacity];
            keys = new double[capacity];
        }

        void clear() {
            size = 0;
        }

        boolean isEmpty() {
            return size == 0;
        }

        void push(int node, double key) {
            if (size == nodes.length) {
                nodes = Arrays.copyOf(nodes, size * 2);
                keys = Arrays.copyOf(keys, size * 2);
            }
            int i = size++;
            while (i > 0) {
                int p = (i - 1) >>> 1;
                if (keys[p] <= key) {
                    break;
                }
                nodes[i] = nodes[p];
                keys[i] = keys[p];
                i = p;
            }
            nodes[i] = node;
            keys[i] = key;
        }

        int pop() {
            int top = nodes[0];
            int lastNode = nodes[--size];
            double lastKey = keys[size];
            int i = 0;
            while (true) {
                int c = 2 * i + 1;
                if (c >= size) {
                    break;
                }
                if (c + 1 < size && keys[c + 1] < keys[c]) {
                    c++;
                }
                if (keys[c] >= lastKey) {
                    break;
                }
                nodes[i] = nodes[c];
                keys[i] = keys[c];
                i = c;
            }
            nodes[i] = lastNode;
            keys[i] = lastKey;
            return top;
        }
    }
}
