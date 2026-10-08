package com.goldenhour.dispatch.live;

import com.goldenhour.dispatch.routing.RoadGraph;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared in-memory state of the live simulation: who is driving where, open trips, and the last known
 * position of every vehicle. Concurrent maps because the API threads (new calls) and the scheduler
 * thread (moving the fleet) both touch it.
 */
@Component
public class LiveState {

    /** Last known position of a vehicle as the "GPS device" sees it. */
    public record Position(double lat, double lon, int node, double heading) {
    }

    private final Map<Long, Motion> motions = new ConcurrentHashMap<>();
    private final Map<Long, LiveTrip> trips = new ConcurrentHashMap<>();
    private final Map<Long, Position> positions = new ConcurrentHashMap<>();
    private final RoadGraph graph;

    public LiveState(com.goldenhour.dispatch.routing.CityModel city) {
        this.graph = city.graph();
    }

    public void clear() {
        motions.clear();
        trips.clear();
        positions.clear();
    }

    // ---------------------------------------------------------------- motions

    public void startMotion(Motion m) {
        motions.put(m.ambulanceId(), m);
    }

    public Motion motion(long ambulanceId) {
        return motions.get(ambulanceId);
    }

    public Collection<Motion> motions() {
        return motions.values();
    }

    /** Remove only if it is still the same motion (another thread may have replaced it). */
    public boolean endMotion(Motion m) {
        return motions.remove(m.ambulanceId(), m);
    }

    public void stopMotion(long ambulanceId, LocalDateTime now) {
        Motion m = motions.remove(ambulanceId);
        if (m != null) {
            double[] p = m.position(now);
            positions.put(ambulanceId, new Position(p[0], p[1], graph.nearestNode(p[0], p[1]), m.route().headingAt(m.elapsed(now))));
        }
    }

    // ---------------------------------------------------------------- positions

    public void setPosition(long ambulanceId, Position p) {
        positions.put(ambulanceId, p);
    }

    public Position position(long ambulanceId) {
        return positions.get(ambulanceId);
    }

    /** Where the vehicle is right now: on its route if driving, else its last fix. */
    public Position currentPosition(long ambulanceId, LocalDateTime now) {
        Motion m = motions.get(ambulanceId);
        if (m == null) {
            return positions.get(ambulanceId);
        }
        double[] p = m.position(now);
        return new Position(p[0], p[1], graph.nearestNode(p[0], p[1]), m.route().headingAt(m.elapsed(now)));
    }

    // ---------------------------------------------------------------- trips

    public void addTrip(LiveTrip t) {
        trips.put(t.tripId(), t);
    }

    public LiveTrip trip(long tripId) {
        return trips.get(tripId);
    }

    public void removeTrip(long tripId) {
        trips.remove(tripId);
    }

    public Collection<LiveTrip> trips() {
        return trips.values();
    }
}
