package com.goldenhour.dispatch.dispatch;

import com.goldenhour.dispatch.config.GoldenHourProperties;
import com.goldenhour.dispatch.corridor.GreenCorridorPlanner;
import com.goldenhour.dispatch.fleet.Ambulance;
import com.goldenhour.dispatch.fleet.AmbulanceRepository;
import com.goldenhour.dispatch.fleet.RedisLockService;
import com.goldenhour.dispatch.incident.DispatchPriority;
import com.goldenhour.dispatch.incident.Incident;
import com.goldenhour.dispatch.incident.IncidentRepository;
import com.goldenhour.dispatch.incident.IncidentStatus;
import com.goldenhour.dispatch.live.LiveEngine;
import com.goldenhour.dispatch.live.LiveSettings;
import com.goldenhour.dispatch.live.LiveState;
import com.goldenhour.dispatch.live.SimClock;
import com.goldenhour.dispatch.routing.CityModel;
import com.goldenhour.dispatch.routing.TrafficBand;
import com.goldenhour.dispatch.trip.Trip;
import com.goldenhour.dispatch.trip.TripEvent;
import com.goldenhour.dispatch.trip.TripEventRepository;
import com.goldenhour.dispatch.trip.TripRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * The heart of the system: assign the nearest free ambulance to a call, with no double booking.
 *
 * <ol>
 *   <li><b>Candidates</b> - PostGIS KNN returns the {@code k} nearest free ambulances "as the crow flies"
 *       (index scan, microseconds).</li>
 *   <li><b>Road ETA</b> - one Dijkstra from the call reaches all k candidates, because the nearest by air
 *       is often not the nearest by road (the creek, a flyover, the railway line).</li>
 *   <li><b>Claim</b> - try candidates fastest-first: take a Redis lock on the ambulance, then a
 *       compare-and-set UPDATE in Postgres. If another dispatcher won, move to the next candidate.</li>
 * </ol>
 */
@Service
public class DispatchService {

    private final AmbulanceRepository ambulances;
    private final IncidentRepository incidents;
    private final TripRepository trips;
    private final TripEventRepository tripEvents;
    private final RedisLockService locks;
    private final CityModel city;
    private final SimClock clock;
    private final LiveSettings settings;
    private final LiveState state;
    private final LiveEngine engine;
    private final TransactionTemplate tx;
    private final GoldenHourProperties props;
    private final Timer dispatchTimer;

    public DispatchService(AmbulanceRepository ambulances, IncidentRepository incidents, TripRepository trips,
                           TripEventRepository tripEvents, RedisLockService locks, CityModel city, SimClock clock,
                           LiveSettings settings, LiveState state, @Lazy LiveEngine engine, TransactionTemplate tx,
                           GoldenHourProperties props, MeterRegistry meters) {
        this.ambulances = ambulances;
        this.incidents = incidents;
        this.trips = trips;
        this.tripEvents = tripEvents;
        this.locks = locks;
        this.city = city;
        this.clock = clock;
        this.settings = settings;
        this.state = state;
        this.engine = engine;
        this.tx = tx;
        this.props = props;
        this.dispatchTimer = Timer.builder("goldenhour.dispatch.latency")
                .description("Time to find and claim an ambulance for one call")
                .publishPercentiles(0.5, 0.95, 0.99).register(meters);
    }

    /** Result of trying to assign one call. */
    public record Assignment(Trip trip, Ambulance ambulance, double etaSeconds) {
    }

    private enum Claim { OK, AMBULANCE_TAKEN, INCIDENT_TAKEN }

    /**
     * Work through the waiting calls in priority order (severity + aging). Called every second by the
     * scheduler and right after a new call arrives.
     */
    public int dispatchQueue() {
        LocalDateTime now = clock.now();
        List<Incident> queue = incidents.findByStatus(IncidentStatus.QUEUED).stream()
                .sorted(Comparator.comparingDouble((Incident i) -> -DispatchPriority.score(i.getSeverity(),
                        SimClock.secondsBetween(i.getCreatedAt(), now))))
                .toList();
        int assigned = 0;
        for (Incident inc : queue) {
            if (tryAssign(inc).isPresent()) {
                assigned++;
            } else if (ambulances.findNearestFree(inc.getLat(), inc.getLon(), 1).isEmpty()) {
                break; // no free ambulance anywhere; the rest keep waiting in priority order
            }
        }
        return assigned;
    }

    public Optional<Assignment> tryAssign(Incident inc) {
        return dispatchTimer.record(() -> assign(inc));
    }

    /** Rounds of fresh candidate search when every candidate was grabbed by a concurrent dispatch. */
    private static final int MAX_ROUNDS = 4;

    private Optional<Assignment> assign(Incident inc) {
        for (int round = 0; round < MAX_ROUNDS; round++) {
            Attempt a = attempt(inc);
            if (a.result() != null || !a.contended()) {
                return Optional.ofNullable(a.result());
            }
            // Every nearby ambulance was taken by a parallel call; the next search sees the new state.
        }
        return Optional.empty();
    }

    private record Attempt(Assignment result, boolean contended) {
    }

    private Attempt attempt(Incident inc) {
        LocalDateTime now = clock.now();
        List<Ambulance> candidates = ambulances.findNearestFree(inc.getLat(), inc.getLon(), props.candidateAmbulances());
        if (candidates.isEmpty()) {
            return new Attempt(null, false);
        }
        boolean contended = false;
        boolean corridor = settings.greenCorridor() && GreenCorridorPlanner.eligible(inc.getSeverity());
        int[] nodes = candidates.stream().mapToInt(a -> nodeOf(a, now)).toArray();
        double[] eta = city.router().timesFrom(inc.getRoadNode(), nodes, TrafficBand.at(now), corridor, nodes.length);
        int[] order = IntStream.range(0, candidates.size()).boxed()
                .sorted(Comparator.comparingDouble(i -> eta[i])).mapToInt(Integer::intValue).toArray();

        for (int i : order) {
            Ambulance amb = candidates.get(i);
            String key = "ambulance:" + amb.getId();
            Optional<String> token = locks.tryLock(key, Duration.ofSeconds(5));
            if (token.isEmpty()) {
                contended = true;
                continue; // someone else is assigning this ambulance right now
            }
            try {
                Trip[] created = new Trip[1];
                Claim result = tx.execute(s -> {
                    if (ambulances.claim(amb.getId()) == 0) {
                        return Claim.AMBULANCE_TAKEN;
                    }
                    if (incidents.claim(inc.getId()) == 0) {
                        s.setRollbackOnly(); // undo the ambulance claim too
                        return Claim.INCIDENT_TAKEN;
                    }
                    Trip trip = trips.save(new Trip(inc.getId(), amb.getId(), now));
                    trip.setEtaSceneAt(now.plusSeconds((long) eta[i]));
                    Ambulance fresh = ambulances.findById(amb.getId()).orElseThrow();
                    fresh.setCurrentTripId(trip.getId());
                    tripEvents.save(new TripEvent(trip.getId(), now, "ASSIGNED",
                            String.format("%s assigned, %.1f min away by road", amb.getCallSign(), eta[i] / 60)));
                    created[0] = trip;
                    return Claim.OK;
                });
                if (result == Claim.INCIDENT_TAKEN) {
                    return new Attempt(null, false);
                }
                if (result == Claim.OK) {
                    // Still holding the lock: stop any reposition drive and hand the trip to the live engine.
                    engine.onAssigned(created[0], inc, amb, nodes[i], corridor, now);
                    return new Attempt(new Assignment(created[0], amb, eta[i]), false);
                }
                contended = true; // claimed by another dispatcher between search and lock
            } finally {
                locks.unlock(key, token.get());
            }
        }
        return new Attempt(null, contended);
    }

    /** A repositioning ambulance is somewhere along its route, not at its last database fix. */
    private int nodeOf(Ambulance a, LocalDateTime now) {
        var p = state.currentPosition(a.getId(), now);
        return p != null ? p.node() : a.getRoadNode();
    }
}
