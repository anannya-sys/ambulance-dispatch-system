package com.goldenhour.dispatch.hospital;

import com.goldenhour.dispatch.incident.PatientType;
import com.goldenhour.dispatch.incident.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Pre-alert sent to an ER before the patient arrives: "critical cardiac, ETA 7 min, cath lab needed". */
@Entity
@Table(name = "hospital_alert")
public class HospitalAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id")
    private Long hospitalId;

    @Column(name = "trip_id")
    private Long tripId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "eta_at")
    private LocalDateTime etaAt;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "patient_type")
    private PatientType patientType;

    @Enumerated(EnumType.STRING)
    @Column(name = "bed_type")
    private BedType bedType;

    private String message;

    protected HospitalAlert() {
    }

    public HospitalAlert(Long hospitalId, Long tripId, LocalDateTime createdAt, LocalDateTime etaAt,
                         Severity severity, PatientType patientType, BedType bedType, String message) {
        this.hospitalId = hospitalId;
        this.tripId = tripId;
        this.createdAt = createdAt;
        this.etaAt = etaAt;
        this.severity = severity;
        this.patientType = patientType;
        this.bedType = bedType;
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public Long getHospitalId() {
        return hospitalId;
    }

    public Long getTripId() {
        return tripId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getEtaAt() {
        return etaAt;
    }

    public Severity getSeverity() {
        return severity;
    }

    public PatientType getPatientType() {
        return patientType;
    }

    public BedType getBedType() {
        return bedType;
    }

    public String getMessage() {
        return message;
    }
}
