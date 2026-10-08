package com.goldenhour.dispatch.live;

import com.goldenhour.dispatch.hospital.BedType;
import com.goldenhour.dispatch.incident.PatientType;
import com.goldenhour.dispatch.incident.Severity;
import com.goldenhour.dispatch.routing.Route;
import com.goldenhour.dispatch.trip.TripStatus;

import java.time.LocalDateTime;

/**
 * In-memory working state of an open trip: the timers the simulated crew runs on. The database row
 * ({@link com.goldenhour.dispatch.trip.Trip}) is the durable record; this is the live "what happens next".
 */
public final class LiveTrip {

    final long tripId;
    final long incidentId;
    final long ambulanceId;
    final String callSign;
    final Severity severity;
    final PatientType patientType;
    final LocalDateTime callAt;
    final double incidentLat;
    final double incidentLon;
    final int incidentNode;
    final String area;
    final LocalDateTime assignedAt;
    final Route routeToScene;
    final boolean corridor;

    volatile TripStatus phase = TripStatus.ASSIGNED;
    /** When the simulated crew will press "acknowledge"; null = they never will (tests reassignment). */
    LocalDateTime ackAt;
    LocalDateTime breakdownAt;
    LocalDateTime sceneUntil;
    LocalDateTime handoverUntil;
    Long hospitalId;
    String hospitalName;
    BedType bedType;
    String hospitalReason;
    double treatmentEtaMinutes;

    LiveTrip(long tripId, long incidentId, long ambulanceId, String callSign, Severity severity,
             PatientType patientType, LocalDateTime callAt, double incidentLat, double incidentLon, int incidentNode,
             String area, LocalDateTime assignedAt, Route routeToScene, boolean corridor) {
        this.tripId = tripId;
        this.incidentId = incidentId;
        this.ambulanceId = ambulanceId;
        this.callSign = callSign;
        this.severity = severity;
        this.patientType = patientType;
        this.callAt = callAt;
        this.incidentLat = incidentLat;
        this.incidentLon = incidentLon;
        this.incidentNode = incidentNode;
        this.area = area;
        this.assignedAt = assignedAt;
        this.routeToScene = routeToScene;
        this.corridor = corridor;
    }

    public long tripId() {
        return tripId;
    }

    public long incidentId() {
        return incidentId;
    }

    public long ambulanceId() {
        return ambulanceId;
    }

    public String callSign() {
        return callSign;
    }

    public Severity severity() {
        return severity;
    }

    public PatientType patientType() {
        return patientType;
    }

    public LocalDateTime callAt() {
        return callAt;
    }

    public double incidentLat() {
        return incidentLat;
    }

    public double incidentLon() {
        return incidentLon;
    }

    public String area() {
        return area;
    }

    public TripStatus phase() {
        return phase;
    }

    public boolean corridor() {
        return corridor;
    }

    public Long hospitalId() {
        return hospitalId;
    }

    public String hospitalName() {
        return hospitalName;
    }

    public String hospitalReason() {
        return hospitalReason;
    }

    public double treatmentEtaMinutes() {
        return treatmentEtaMinutes;
    }
}
