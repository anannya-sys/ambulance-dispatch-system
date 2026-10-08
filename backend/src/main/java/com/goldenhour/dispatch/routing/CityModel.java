package com.goldenhour.dispatch.routing;

import com.goldenhour.dispatch.forecast.StandbyPost;
import com.goldenhour.dispatch.geo.Localities;
import com.goldenhour.dispatch.hospital.HospitalCatalog;
import com.goldenhour.dispatch.hospital.HospitalSite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything static about the city, built once at startup (~2 s) and shared by the live system and the
 * offline simulation: road graph, router, hospital sites, standby posts and their travel-time tables.
 */
public final class CityModel {

    /** Grid spacing for candidate standby posts (~2 km). */
    static final double POST_GRID_DEG = 0.018;

    private final RoadGraph graph;
    private final Router router;
    private final List<HospitalSite> hospitals;
    private final TravelTimeTable hospitalTable;
    private final List<StandbyPost> posts;
    private final TravelTimeTable postTable;

    public CityModel(RoadGraph graph) {
        this.graph = graph;
        this.router = new Router(graph);
        this.hospitals = HospitalCatalog.load(graph);
        this.hospitalTable = new TravelTimeTable(router, hospitals.stream().mapToInt(HospitalSite::node).toArray());
        this.posts = generatePosts(graph);
        this.postTable = new TravelTimeTable(router, posts.stream().mapToInt(StandbyPost::node).toArray());
    }

    public static CityModel load() {
        return new CityModel(RoadGraphLoader.fromClasspath("data/mumbai-roads.bin.gz"));
    }

    public RoadGraph graph() {
        return graph;
    }

    public Router router() {
        return router;
    }

    public List<HospitalSite> hospitals() {
        return hospitals;
    }

    public TravelTimeTable hospitalTable() {
        return hospitalTable;
    }

    public List<StandbyPost> posts() {
        return posts;
    }

    public TravelTimeTable postTable() {
        return postTable;
    }

    /**
     * One candidate post per ~2 km grid square: the junction on the biggest road nearest the square's
     * centre (main roads are where you'd park to reach everywhere fast).
     */
    static List<StandbyPost> generatePosts(RoadGraph g) {
        double[] b = g.bounds();
        Map<Long, Integer> bestNode = new HashMap<>();
        Map<Long, Double> bestScore = new HashMap<>();
        for (int n = 0; n < g.nodeCount(); n++) {
            if (g.lon(n) > 72.975) {
                continue; // across the creek
            }
            RoadClass c = g.bestClassAt(n);
            if (c.ordinal() > RoadClass.SECONDARY.ordinal()) {
                continue;
            }
            long r = (long) ((g.lat(n) - b[0]) / POST_GRID_DEG);
            long col = (long) ((g.lon(n) - b[1]) / POST_GRID_DEG);
            long key = r * 10_000 + col;
            double cLat = b[0] + (r + 0.5) * POST_GRID_DEG;
            double cLon = b[1] + (col + 0.5) * POST_GRID_DEG;
            double score = com.goldenhour.dispatch.geo.GeoUtils.distanceMeters(g.lat(n), g.lon(n), cLat, cLon);
            if (score < bestScore.getOrDefault(key, Double.MAX_VALUE)) {
                bestScore.put(key, score);
                bestNode.put(key, n);
            }
        }
        List<Long> keys = new ArrayList<>(bestNode.keySet());
        keys.sort(null);
        List<StandbyPost> out = new ArrayList<>();
        Map<String, Integer> used = new HashMap<>();
        for (long k : keys) {
            int n = bestNode.get(k);
            String loc = Localities.nearest(g.lat(n), g.lon(n));
            int seq = used.merge(loc, 1, Integer::sum);
            String name = seq == 1 ? loc + " post" : loc + " post " + seq;
            out.add(new StandbyPost(out.size(), name, n, g.lat(n), g.lon(n)));
        }
        return out;
    }
}
