package com.goldenhour.dispatch.live;

import com.goldenhour.dispatch.common.ConflictException;
import com.goldenhour.dispatch.common.NotFoundException;
import com.goldenhour.dispatch.config.GoldenHourProperties;
import com.goldenhour.dispatch.corridor.GreenCorridorPlanner;
import com.goldenhour.dispatch.corridor.SignalWindow;
import com.goldenhour.dispatch.dispatch.DispatchService;
import com.goldenhour.dispatch.fleet.Ambulance;
import com.goldenhour.dispatch.fleet.AmbulanceRepository;
import com.goldenhour.dispatch.fleet.AmbulanceStatus;
import com.goldenhour.dispatch.fleet.FleetRepositoryOps;
import com.goldenhour.dispatch.fleet.RedisLockService;
import com.goldenhour.dispatch.forecast.StandbyPost;
import com.goldenhour.dispatch.hospital.HospitalService;
import com.goldenhour.dispatch.incident.Incident;
import com.goldenhour.dispatch.routing.CityModel;
import com.goldenhour.dispatch.routing.Route;
import com.goldenhour.dispatch.routing.TrafficBand;
import com.goldenhour.dispatch.trip.Trip;
import com.goldenhour.dispatch.trip.TripService;
import com.goldenhour.dispatch.trip.TripStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

/**
 * Drives the simulated city every second: crews acknowledge (or don't), ambulances drive their routes,
 * reach the patient, pick a hospital, hand over, become free. The simulator only plays the role of the
 * crews and the vehicles; every decision (dispatch, hospital, corridor, reassignment) is made by the same
 * services a real deployment would use.
 */
@Component
public class LiveEngine {

    private static final Logger log = LoggerFactory.getLogger(LiveEngine.class);

    static final Duration NO_ACK_OUT_OF_SERVICE = Duration.ofMinutes(10);
    static final Duration BREAKDOWN_OUT_OF_SERVICE = Duration.ofMinutes(45);
    static final Duration HANDOVER = Duration.ofMinutes(10);

    private final GoldenHourProperties props;
    private final SimClock clock;
    private final LiveState state;
    private final LiveSettings settings;
    private final CityModel city;
    private final TripService tripService;
    private final HospitalService hospitalService;
    private final DispatchService dispatch;
    private final AmbulanceRepository ambulances;
    private final FleetRepositoryOps fleetOps;
    private final RedisLockService locks;
    private final Random rnd = new Random(7);

    public LiveEngine(GoldenHourProperties props, SimClock clock, LiveState state, LiveSettings settings,
                      CityModel city, TripService tripService, HospitalService hospitalService,
                      DispatchService dispatch, AmbulanceRepository ambulances, FleetRepositoryOps fleetOps,
                      RedisLockService locks) {
        this.props = props;
        this.clock = clock;
        this.state = state;
        this.settings = settings;
        this.city = city;
        this.tripService = tripService;
        this.hospitalService = hospitalService;
        this.dispatch = dispatch;
        this.ambulances = ambulances;
        this.fleetOps = fleetOps;
        this.locks = locks;
    }

    /** Called by the dispatcher (holding the ambulance lock) right after a successful claim. */
    public void onAssigned(Trip trip, Incident inc, Ambulance amb, int fromNode, boolean corridor, LocalDateTime now) {
        state.stopMotion(amb.getId(), now); // cancel a reposition drive, if any
        Route route = city.router().route(fromNode, inc.getRoadNode(), TrafficBand.at(now), corridor);
        LiveTrip lt = new LiveTrip(trip.getId(), inc.getId(), amb.getId(), amb.getCallSign(), inc.getSeverity(),
                inc.getPatientType(), inc.getCreatedAt(), inc.getLat(), inc.getLon(), inc.getRoadNode(),
                inc.getAreaName(), now, route, corridor);
        synchronized (rnd) {
            // simulated crew: most acknowledge in 4-18 s, a few never do
            lt.ackAt = rnd.nextDouble() < props.live().noAckProbability() ? null : now.plusSeconds(4 + rnd.nextInt(15));
            if (rnd.nextDouble() < props.live().breakdownProbability()) {
                lt.breakdownAt = LocalDateTime.MIN; // decided now, timed once the vehicle is moving
            }
        }
        state.addTrip(lt);
        tripService.publish("ASSIGNED", trip, amb.getCallSign(), inc.getSeverity().name(),
                String.format(Locale.ROOT, "%s assigned to %s %s call in %s, ETA %.0f min", amb.getCallSign(),
                        inc.getSeverity().name().toLowerCase(Locale.ROOT), inc.getPatientType().name().toLowerCase(Locale.ROOT),
                        inc.getAreaName(), route.totalSeconds() / 60), now);
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 3000)
    public void scheduledTick() {
        if (props.live().enabled()) {
            tick();
        }
    }

    /** One step of the simulation. Synchronized so manual commands (breakdown) don't interleave with it. */
    public synchronized void tick() {
        LocalDateTime now = clock.now();
        for (LiveTrip lt : new ArrayList<>(state.trips())) {
            try {
                step(lt, now);
            } catch (Exception e) {
                log.warn("Trip {} step failed: {}", lt.tripId(), e.toString());
            }
        }
        for (Motion m : new ArrayList<>(state.motions())) {
            if (m.purpose() == Motion.Purpose.REPOSITION && m.arrived(now) && state.endMotion(m)) {
                double[] end = m.position(now);
                state.setPosition(m.ambulanceId(), new LiveState.Position(end[0], end[1],
                        city.graph().nearestNode(end[0], end[1]), 0));
                fleetOps.finishRepositioning(m.ambulanceId());
            }
        }
        fleetOps.returnToService(now);
        dispatch.dispatchQueue();
    }

    private void step(LiveTrip lt, LocalDateTime now) {
        switch (lt.phase) {
            case ASSIGNED -> {
                if (lt.ackAt != null && !now.isBefore(lt.ackAt)) {
                    acknowledge(lt, now);
                } else if (lt.ackAt == null
                        && SimClock.secondsBetween(lt.assignedAt, now) >= props.ackTimeoutSeconds()) {
                    reassign(lt, now, "No acknowledgement within " + props.ackTimeoutSeconds() + " s",
                            NO_ACK_OUT_OF_SERVICE, "REASSIGNED");
                }
            }
            case EN_ROUTE -> {
                Motion m = state.motion(lt.ambulanceId);
                if (lt.breakdownAt != null && !now.isBefore(lt.breakdownAt)) {
                    reassign(lt, now, "Ambulance broke down on the way", BREAKDOWN_OUT_OF_SERVICE, "BREAKDOWN");
                } else if (m != null && m.arrived(now)) {
                    state.endMotion(m);
                    state.setPosition(lt.ambulanceId, new LiveState.Position(lt.incidentLat, lt.incidentLon, lt.incidentNode, 0));
                    Trip t = tripService.arriveAtScene(lt.tripId, now);
                    double jitter;
                    synchronized (rnd) {
                        jitter = 0.7 + 0.6 * rnd.nextDouble();
                    }
                    lt.sceneUntil = now.plusSeconds((long) (lt.severity.sceneMinutes() * 60 * jitter));
                    lt.phase = TripStatus.AT_SCENE;
                    tripService.publish("AT_SCENE", t, lt.callSign, lt.severity.name(),
                            String.format(Locale.ROOT, "%s reached the patient in %s after %d min", lt.callSign, lt.area,
                                    Math.round(t.getResponseSeconds() / 60.0)), now);
                }
            }
            case AT_SCENE -> {
                if (!now.isBefore(lt.sceneUntil)) {
                    pickUp(lt, now);
                }
            }
            case PICKED_UP -> {
                Motion m = state.motion(lt.ambulanceId);
                if (m != null && m.arrived(now)) {
                    state.endMotion(m);
                    double[] end = m.route().positionAt(m.route().totalSeconds());
                    state.setPosition(lt.ambulanceId, new LiveState.Position(end[0], end[1],
                            city.graph().nearestNode(end[0], end[1]), 0));
                    Trip t = tripService.arriveAtHospital(lt.tripId, now);
                    lt.handoverUntil = now.plus(HANDOVER);
                    lt.phase = TripStatus.AT_HOSPITAL;
                    tripService.publish("AT_HOSPITAL", t, lt.callSign, lt.severity.name(),
                            String.format(Locale.ROOT, "%s at %s, %d min after the call", lt.callSign, lt.hospitalName,
                                    Math.round(t.getCallToHospitalSeconds() / 60.0)), now);
                }
            }
            case AT_HOSPITAL -> {
                if (!now.isBefore(lt.handoverUntil)) {
                    Trip t = tripService.close(lt.tripId, now);
                    hospitalService.handedOver(lt.hospitalId);
                    state.removeTrip(lt.tripId);
                    tripService.publish("CLOSED", t, lt.callSign, lt.severity.name(),
                            lt.callSign + " handed over and is available again", now);
                    if (!settings.prePositioning()) {
                        ambulances.findById(lt.ambulanceId).ifPresent(a -> {
                            if (a.getHomePostId() != null) {
                                startReposition(a.getId(), city.posts().get(a.getHomePostId()), now);
                            }
                        });
                    }
                }
            }
            default -> {
            }
        }
    }

    private void acknowledge(LiveTrip lt, LocalDateTime now) {
        List<SignalWindow> windows = lt.corridor ? GreenCorridorPlanner.plan(lt.routeToScene, now) : List.of();
        state.startMotion(new Motion(lt.ambulanceId, lt.tripId, Motion.Purpose.TO_SCENE, lt.routeToScene, now, null, windows));
        Trip t = tripService.acknowledge(lt.tripId, now, now.plusSeconds((long) lt.routeToScene.totalSeconds()));
        if (lt.breakdownAt != null) {
            double share;
            synchronized (rnd) {
                share = 0.25 + 0.35 * rnd.nextDouble();
            }
            lt.breakdownAt = now.plusSeconds((long) (lt.routeToScene.totalSeconds() * share));
        }
        lt.phase = TripStatus.EN_ROUTE;
        tripService.publish("ACKNOWLEDGED", t, lt.callSign, lt.severity.name(),
                lt.callSign + " acknowledged, en route via " + lt.routeToScene.via(), now);
        if (!windows.isEmpty()) {
            tripService.publish("CORRIDOR", t, lt.callSign, lt.severity.name(), String.format(Locale.ROOT,
                    "Green corridor: %d signals pre-cleared for %s (saves ~%.0f s)", windows.size(), lt.callSign,
                    GreenCorridorPlanner.secondsSaved(lt.routeToScene)), now);
        }
    }

    private void pickUp(LiveTrip lt, LocalDateTime now) {
        HospitalService.Decision d = hospitalService.chooseAndReserve(lt.incidentNode, lt.patientType, lt.severity, now);
        Route route = city.router().route(lt.incidentNode, d.hospital().getRoadNode(), TrafficBand.at(now), lt.corridor);
        List<SignalWindow> windows = lt.corridor ? GreenCorridorPlanner.plan(route, now) : List.of();
        state.startMotion(new Motion(lt.ambulanceId, lt.tripId, Motion.Purpose.TO_HOSPITAL, route, now, null, windows));
        LocalDateTime eta = now.plusSeconds((long) route.totalSeconds());
        lt.hospitalId = d.hospital().getId();
        lt.hospitalName = d.hospital().getName();
        lt.bedType = d.bed();
        lt.hospitalReason = d.reason();
        lt.treatmentEtaMinutes = d.estimate().timeToTreatmentSeconds() / 60;
        lt.phase = TripStatus.PICKED_UP;
        Trip t = tripService.pickUp(lt.tripId, now, d.hospital().getId(), eta, d.reason(), route.via(), lt.corridor,
                windows.size());
        hospitalService.preAlert(d, lt.tripId, lt.severity, lt.patientType, now, eta);
        tripService.publish("PICKED_UP", t, lt.callSign, lt.severity.name(), "Patient on board. " + d.reason(), now);
        tripService.publish("PRE_ALERT", t, lt.callSign, lt.severity.name(),
                "Pre-alert sent to " + d.hospital().getName(), now);
    }

    private void reassign(LiveTrip lt, LocalDateTime now, String reason, Duration outOfService, String type) {
        state.stopMotion(lt.ambulanceId, now);
        Trip t = tripService.cancel(lt.tripId, now, reason, now.plus(outOfService));
        state.removeTrip(lt.tripId);
        tripService.publish(type, t, lt.callSign, lt.severity.name(),
                reason + ": " + lt.callSign + " taken off the call, reassigning", now);
        // the call is QUEUED again with its original time, so the next dispatch round picks it up first
    }

    /** Manual breakdown from the dashboard. */
    public synchronized String breakdown(long ambulanceId) {
        LocalDateTime now = clock.now();
        Ambulance a = ambulances.findById(ambulanceId).orElseThrow(() -> new NotFoundException("Ambulance", ambulanceId));
        Optional<LiveTrip> trip = state.trips().stream().filter(t -> t.ambulanceId == ambulanceId).findFirst();
        if (trip.isPresent()) {
            LiveTrip lt = trip.get();
            if (lt.phase != TripStatus.ASSIGNED && lt.phase != TripStatus.EN_ROUTE) {
                throw new ConflictException(a.getCallSign() + " has a patient on board; it can't be taken off the trip");
            }
            reassign(lt, now, "Ambulance broke down on the way", BREAKDOWN_OUT_OF_SERVICE, "BREAKDOWN");
            return a.getCallSign() + " broke down; its call is being reassigned";
        }
        if (a.getStatus() == AmbulanceStatus.OUT_OF_SERVICE) {
            throw new ConflictException(a.getCallSign() + " is already out of service");
        }
        state.stopMotion(ambulanceId, now);
        a.setStatus(AmbulanceStatus.OUT_OF_SERVICE);
        a.setAvailableAt(now.plus(BREAKDOWN_OUT_OF_SERVICE));
        ambulances.save(a);
        return a.getCallSign() + " is out of service for 45 min";
    }

    /** Send an idle ambulance to a standby post. Takes the same lock as the dispatcher. */
    public boolean startReposition(long ambulanceId, StandbyPost post, LocalDateTime now) {
        String key = "ambulance:" + ambulanceId;
        Optional<String> token = locks.tryLock(key, Duration.ofSeconds(5));
        if (token.isEmpty()) {
            return false;
        }
        try {
            LiveState.Position p = state.currentPosition(ambulanceId, now);
            if (p == null || fleetOps.startRepositioning(ambulanceId, post.index()) == 0) {
                return false;
            }
            Route r = city.router().route(p.node(), post.node(), TrafficBand.at(now), false);
            state.startMotion(new Motion(ambulanceId, null, Motion.Purpose.REPOSITION, r, now, post.index(), List.of()));
            return true;
        } finally {
            locks.unlock(key, token.get());
        }
    }
}
