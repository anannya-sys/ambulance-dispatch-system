package com.goldenhour.dispatch.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/** An emergency call. One incident can have several trips if an ambulance has to be reassigned. */
@Entity
@Table(name = "incident")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private double lat;
    private double lon;

    @Column(name = "road_node")
    private int roadNode;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "patient_type")
    private PatientType patientType;

    @Enumerated(EnumType.STRING)
    private IncidentStatus status;

    @Enumerated(EnumType.STRING)
    private IncidentSource source;

    @Column(name = "area_name")
    private String areaName;

    private String description;

    @Column(name = "surge_group")
    private String surgeGroup;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Version
    private long version;

    protected Incident() {
    }

    public Incident(LocalDateTime createdAt, double lat, double lon, int roadNode, Severity severity,
                    PatientType patientType, IncidentSource source, String areaName, String description,
                    String surgeGroup) {
        this.createdAt = createdAt;
        this.lat = lat;
        this.lon = lon;
        this.roadNode = roadNode;
        this.severity = severity;
        this.patientType = patientType;
        this.status = IncidentStatus.QUEUED;
        this.source = source;
        this.areaName = areaName;
        this.description = description;
        this.surgeGroup = surgeGroup;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public int getRoadNode() {
        return roadNode;
    }

    public Severity getSeverity() {
        return severity;
    }

    public PatientType getPatientType() {
        return patientType;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public IncidentSource getSource() {
        return source;
    }

    public String getAreaName() {
        return areaName;
    }

    public String getDescription() {
        return description;
    }

    public String getSurgeGroup() {
        return surgeGroup;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
