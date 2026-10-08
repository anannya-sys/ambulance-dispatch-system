package com.goldenhour.dispatch.trip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.LocalDateTime;

/** One ambulance working one incident, from assignment to handover at the hospital. */
@Entity
@Table(name = "trip")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id")
    private Long incidentId;

    @Column(name = "ambulance_id")
    private Long ambulanceId;

    @Column(name = "hospital_id")
    private Long hospitalId;

    @Enumerated(EnumType.STRING)
    private TripStatus status;

    @Column(name = "assigned_at") private LocalDateTime assignedAt;
    @Column(name = "acknowledged_at") private LocalDateTime acknowledgedAt;
    @Column(name = "at_scene_at") private LocalDateTime atSceneAt;
    @Column(name = "picked_up_at") private LocalDateTime pickedUpAt;
    @Column(name = "at_hospital_at") private LocalDateTime atHospitalAt;
    @Column(name = "closed_at") private LocalDateTime closedAt;
    @Column(name = "eta_scene_at") private LocalDateTime etaSceneAt;
    @Column(name = "eta_hospital_at") private LocalDateTime etaHospitalAt;

    /** Call received -> ambulance at scene. */
    @Column(name = "response_seconds")
    private Integer responseSeconds;

    @Column(name = "call_to_hospital_seconds")
    private Integer callToHospitalSeconds;

    @Column(name = "green_corridor")
    private boolean greenCorridor;

    @Column(name = "signals_cleared")
    private int signalsCleared;

    @Column(name = "hospital_reason")
    private String hospitalReason;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "route_via")
    private String routeVia;

    @Version
    private long version;

    protected Trip() {
    }

    public Trip(Long incidentId, Long ambulanceId, LocalDateTime assignedAt) {
        this.incidentId = incidentId;
        this.ambulanceId = ambulanceId;
        this.assignedAt = assignedAt;
        this.status = TripStatus.ASSIGNED;
    }

    /** Guarded transition; throws on an illegal move. */
    public void moveTo(TripStatus target, LocalDateTime at) {
        if (!status.canMoveTo(target)) {
            throw new InvalidTransitionException(status, target);
        }
        status = target;
        switch (target) {
            case EN_ROUTE -> acknowledgedAt = at;
            case AT_SCENE -> atSceneAt = at;
            case PICKED_UP -> pickedUpAt = at;
            case AT_HOSPITAL -> atHospitalAt = at;
            case CLOSED, CANCELLED -> closedAt = at;
            default -> {
            }
        }
    }

    /** Open trips can't continue after a restart (vehicle state lived in memory). */
    public void moveToCancelledOnRestart(LocalDateTime at) {
        status = TripStatus.CANCELLED;
        closedAt = at;
        cancelReason = "System restart";
    }

    public void recordResponse(LocalDateTime callAt) {
        if (atSceneAt != null) {
            responseSeconds = (int) Duration.between(callAt, atSceneAt).getSeconds();
        }
        if (atHospitalAt != null) {
            callToHospitalSeconds = (int) Duration.between(callAt, atHospitalAt).getSeconds();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public Long getAmbulanceId() {
        return ambulanceId;
    }

    public Long getHospitalId() {
        return hospitalId;
    }

    public void setHospitalId(Long hospitalId) {
        this.hospitalId = hospitalId;
    }

    public TripStatus getStatus() {
        return status;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public LocalDateTime getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public LocalDateTime getAtSceneAt() {
        return atSceneAt;
    }

    public LocalDateTime getPickedUpAt() {
        return pickedUpAt;
    }

    public LocalDateTime getAtHospitalAt() {
        return atHospitalAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public LocalDateTime getEtaSceneAt() {
        return etaSceneAt;
    }

    public void setEtaSceneAt(LocalDateTime etaSceneAt) {
        this.etaSceneAt = etaSceneAt;
    }

    public LocalDateTime getEtaHospitalAt() {
        return etaHospitalAt;
    }

    public void setEtaHospitalAt(LocalDateTime etaHospitalAt) {
        this.etaHospitalAt = etaHospitalAt;
    }

    public Integer getResponseSeconds() {
        return responseSeconds;
    }

    public Integer getCallToHospitalSeconds() {
        return callToHospitalSeconds;
    }

    public boolean isGreenCorridor() {
        return greenCorridor;
    }

    public void setGreenCorridor(boolean greenCorridor) {
        this.greenCorridor = greenCorridor;
    }

    public int getSignalsCleared() {
        return signalsCleared;
    }

    public void addSignalsCleared(int n) {
        this.signalsCleared += n;
    }

    public String getHospitalReason() {
        return hospitalReason;
    }

    public void setHospitalReason(String hospitalReason) {
        this.hospitalReason = hospitalReason;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public String getRouteVia() {
        return routeVia;
    }

    public void setRouteVia(String routeVia) {
        this.routeVia = routeVia;
    }
}
