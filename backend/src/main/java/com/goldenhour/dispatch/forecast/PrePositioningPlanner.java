package com.goldenhour.dispatch.forecast;

import com.goldenhour.dispatch.routing.RoadGraph;
import com.goldenhour.dispatch.routing.TrafficBand;
import com.goldenhour.dispatch.routing.TravelTimeTable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * USP 1 - predictive pre-positioning.
 *
 * <p>Given the forecast for the next hour and {@code k} idle ambulances, pick {@code k} standby posts that
 * minimise the <i>expected</i> response time:</p>
 *
 * <pre>
 *   cost(S) = sum over cells c of  expectedCalls(c) * min over posts p in S of travelTime(p, c)
 * </pre>
 *
 * <p>This is the classic <b>p-median</b> facility-location problem (NP-hard). We use the greedy
 * heuristic: add the post that lowers the cost most, k times. It runs in O(k * posts * cells), a few
 * milliseconds here, and is within a few percent of optimal on problems of this shape. Then idle
 * ambulances are matched to the chosen posts, nearest pairs first.</p>
 */
public final class PrePositioningPlanner {

    /** Cost assigned to demand no post covers yet (30 min), so the first post goes where demand is. */
    static final double UNCOVERED_SECONDS = 30 * 60;
    /** Don't send an ambulance on a reposition drive longer than this. */
    static final double MAX_REPOSITION_SECONDS = 20 * 60;

    private PrePositioningPlanner() {
    }

    /** Idle ambulance as seen by the planner. */
    public record IdleUnit(long id, int node) {
    }

    /**
     * Fixed home stations: the best static placement for <i>average</i> weekly demand. Used as the
     * baseline fleet layout and as each ambulance's home base.
     */
    public static List<StandbyPost> staticStations(DemandModel model, int stations, com.goldenhour.dispatch.routing.CityModel city,
                                                   java.time.LocalDateTime weekStart) {
        List<DemandModel.CellForecast> week = model.forecast(weekStart, 168 * 60);
        return choosePosts(week, stations, city.posts(), city.postTable(), city.graph(), TrafficBand.OFF_PEAK);
    }

    /** Chosen posts, best first. */
    public static List<StandbyPost> choosePosts(List<DemandModel.CellForecast> demand, int k,
                                                List<StandbyPost> posts, TravelTimeTable postTable,
                                                RoadGraph graph, TrafficBand band) {
        if (k <= 0 || demand.isEmpty()) {
            return List.of();
        }
        int cells = demand.size();
        int[] cellNode = new int[cells];
        double[] weight = new double[cells];
        for (int c = 0; c < cells; c++) {
            DemandModel.CellForecast f = demand.get(c);
            cellNode[c] = graph.nearestNode(f.lat(), f.lon());
            weight[c] = f.expectedCalls();
        }
        double[] best = new double[cells];
        java.util.Arrays.fill(best, UNCOVERED_SECONDS);
        List<StandbyPost> chosen = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (int round = 0; round < Math.min(k, posts.size()); round++) {
            StandbyPost pick = null;
            double pickGain = 0;
            for (StandbyPost p : posts) {
                if (used.contains(p.index())) {
                    continue;
                }
                double gain = 0;
                for (int c = 0; c < cells; c++) {
                    double t = postTable.seconds(p.index(), cellNode[c], band);
                    if (t < best[c]) {
                        gain += weight[c] * (best[c] - t);
                    }
                }
                if (gain > pickGain) {
                    pickGain = gain;
                    pick = p;
                }
            }
            if (pick == null) {
                break; // nothing left improves coverage
            }
            used.add(pick.index());
            chosen.add(pick);
            for (int c = 0; c < cells; c++) {
                best[c] = Math.min(best[c], postTable.seconds(pick.index(), cellNode[c], band));
            }
        }
        return chosen;
    }

    /**
     * Match idle ambulances to posts. Nearest (ambulance, post) pairs are taken first; ambulances already at a
     * chosen post stay put. Returns ambulanceId -> post for ambulances that should move or stay.
     */
    public static Map<Long, StandbyPost> assign(List<IdleUnit> idle, List<StandbyPost> chosen,
                                                TravelTimeTable postTable, TrafficBand band) {
        record Pair(IdleUnit unit, StandbyPost post, double seconds) {
        }
        List<Pair> pairs = new ArrayList<>();
        for (IdleUnit u : idle) {
            for (StandbyPost p : chosen) {
                double t = postTable.seconds(p.index(), u.node(), band);
                if (t <= MAX_REPOSITION_SECONDS) {
                    pairs.add(new Pair(u, p, t));
                }
            }
        }
        pairs.sort(Comparator.comparingDouble(Pair::seconds));
        Map<Long, StandbyPost> out = new LinkedHashMap<>();
        Set<Integer> taken = new HashSet<>();
        for (Pair pr : pairs) {
            if (out.containsKey(pr.unit().id()) || taken.contains(pr.post().index())) {
                continue;
            }
            out.put(pr.unit().id(), pr.post());
            taken.add(pr.post().index());
        }
        return out;
    }

    /** Share of forecast demand that some chosen post reaches within {@code withinSeconds}. */
    public static double coverage(List<DemandModel.CellForecast> demand, List<StandbyPost> chosen,
                                  TravelTimeTable postTable, RoadGraph graph, TrafficBand band,
                                  double withinSeconds) {
        double total = 0;
        double covered = 0;
        for (DemandModel.CellForecast f : demand) {
            int node = graph.nearestNode(f.lat(), f.lon());
            total += f.expectedCalls();
            for (StandbyPost p : chosen) {
                if (postTable.seconds(p.index(), node, band) <= withinSeconds) {
                    covered += f.expectedCalls();
                    break;
                }
            }
        }
        return total == 0 ? 0 : covered / total;
    }
}
