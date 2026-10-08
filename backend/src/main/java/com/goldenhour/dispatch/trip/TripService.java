package com.goldenhour.dispatch.trip;

import com.goldenhour.dispatch.common.NotFoundException;
import com.goldenhour.dispatch.fleet.Ambulance;
import com.goldenhour.dispatch.fleet.AmbulanceRepository;
import com.goldenhour.dispatch.fleet.AmbulanceStatus;
import com.goldenhour.dispatch.incident.Incident;
import com.goldenhour.dispatch.incident.IncidentRepository;
import com.goldenhour.dispatch.incident.IncidentStatus;
import com.goldenhour.dispatch.messaging.DispatchEvent;
import com.goldenhour.dispatch.messaging.EventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Every trip state change goes through here: update trip, ambulance and incident in one transaction,
 * append to the audit log, and publish the event to Kafka.
 *
 * <p>Note: publishing to Kafka inside the transaction is "best effort" - if the commit failed after the
 * send, a consumer could see an event for a change that didn't happen. The production fix is the
 * transactional outbox pattern (write the event to a table in the same transaction, relay it to Kafka
 * afterwards). It's listed under future improvements.</p>
 */
@Service
public class TripService {

    private final TripRepository trips;
    private final TripEventRepository events;
    private final AmbulanceRepository ambulances;
    private final IncidentRepository incidents;
    private final EventPublisher publisher;

    public TripService(TripRepository trips, TripEventRepository events, AmbulanceRepository ambulances,
                       IncidentRepository incidents, EventPublisher publisher) {
        this.trips = trips;
        this.events = events;
        this.ambulances = ambulances;
        this.incidents = incidents;
        this.publisher = publisher;
    }

    @Transactional
    public Trip acknowledge(long tripId, LocalDateTime now, LocalDateTime etaScene) {
        Trip t = load(tripId);
        t.moveTo(TripStatus.EN_ROUTE, now);
        t.setEtaSceneAt(etaScene);
        setAmbulance(t.getAmbulanceId(), AmbulanceStatus.EN_ROUTE);
        log(t, now, "ACKNOWLEDGED", "Crew acknowledged, en route");
        return t;
    }

    @Transactional
    public Trip arriveAtScene(long tripId, LocalDateTime now) {
        Trip t = load(tripId);
        t.moveTo(TripStatus.AT_SCENE, now);
        Incident inc = incidents.findById(t.getIncidentId()).orElseThrow();
        t.recordResponse(inc.getCreatedAt());
        setAmbulance(t.getAmbulanceId(), AmbulanceStatus.AT_SCENE);
        log(t, now, "AT_SCENE", "Reached patient in " + Math.round(t.getResponseSeconds() / 60.0) + " min");
        return t;
    }

    @Transactional
    public Trip pickUp(long tripId, LocalDateTime now, long hospitalId, LocalDateTime etaHospital, String reason,
                       String via, boolean corridor, int signals) {
        Trip t = load(tripId);
        t.moveTo(TripStatus.PICKED_UP, now);
        t.setHospitalId(hospitalId);
        t.setEtaHospitalAt(etaHospital);
        t.setHospitalReason(reason);
        t.setRouteVia(via);
        if (corridor) {
            t.setGreenCorridor(true);
            t.addSignalsCleared(signals);
        }
        setAmbulance(t.getAmbulanceId(), AmbulanceStatus.TRANSPORTING);
        log(t, now, "PICKED_UP", reason);
        return t;
    }

    @Transactional
    public Trip arriveAtHospital(long tripId, LocalDateTime now) {
        Trip t = load(tripId);
        t.moveTo(TripStatus.AT_HOSPITAL, now);
        Incident inc = incidents.findById(t.getIncidentId()).orElseThrow();
        t.recordResponse(inc.getCreatedAt());
        setAmbulance(t.getAmbulanceId(), AmbulanceStatus.AT_HOSPITAL);
        log(t, now, "AT_HOSPITAL", "Patient handed over to ER");
        return t;
    }

    /** Handover done: trip closed, incident resolved, ambulance free again. */
    @Transactional
    public Trip close(long tripId, LocalDateTime now) {
        Trip t = load(tripId);
        t.moveTo(TripStatus.CLOSED, now);
        Incident inc = incidents.findById(t.getIncidentId()).orElseThrow();
        inc.setStatus(IncidentStatus.RESOLVED);
        inc.setResolvedAt(now);
        Ambulance a = ambulances.findById(t.getAmbulanceId()).orElseThrow();
        a.setStatus(AmbulanceStatus.AVAILABLE);
        a.setCurrentTripId(null);
        log(t, now, "CLOSED", "Ambulance available");
        return t;
    }

    /**
     * Cancel a trip (crew didn't acknowledge, or breakdown). The incident goes back to the queue with its
     * original call time, so its priority keeps aging and it is reassigned first.
     */
    @Transactional
    public Trip cancel(long tripId, LocalDateTime now, String reason, LocalDateTime ambulanceBackAt) {
        Trip t = load(tripId);
        t.moveTo(TripStatus.CANCELLED, now);
        t.setCancelReason(reason);
        Incident inc = incidents.findById(t.getIncidentId()).orElseThrow();
        inc.setStatus(IncidentStatus.QUEUED);
        Ambulance a = ambulances.findById(t.getAmbulanceId()).orElseThrow();
        a.setStatus(AmbulanceStatus.OUT_OF_SERVICE);
        a.setAvailableAt(ambulanceBackAt);
        a.setCurrentTripId(null);
        log(t, now, "REASSIGNED", reason);
        return t;
    }

    public void publish(String type, Trip t, String callSign, String severity, String message, LocalDateTime at) {
        publisher.dispatch(new DispatchEvent(type, t.getId(), t.getIncidentId(), t.getAmbulanceId(), callSign,
                t.getHospitalId(), severity, message, at));
    }

    private Trip load(long id) {
        return trips.findById(id).orElseThrow(() -> new NotFoundException("Trip", id));
    }

    private void setAmbulance(long id, AmbulanceStatus status) {
        ambulances.findById(id).orElseThrow().setStatus(status);
    }

    private void log(Trip t, LocalDateTime at, String type, String detail) {
        events.save(new TripEvent(t.getId(), at, type, detail));
    }
}
