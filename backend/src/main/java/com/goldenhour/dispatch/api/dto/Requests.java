package com.goldenhour.dispatch.api.dto;

import com.goldenhour.dispatch.incident.PatientType;
import com.goldenhour.dispatch.incident.Severity;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request bodies, validated with Bean Validation. Coordinates are limited to the Mumbai region. */
public final class Requests {

    private Requests() {
    }

    public record CreateIncident(
            @NotNull @DecimalMin("18.85") @DecimalMax("19.30") Double lat,
            @NotNull @DecimalMin("72.75") @DecimalMax("73.05") Double lon,
            @NotNull Severity severity,
            @NotNull PatientType patientType,
            @Size(max = 200) String description) {
    }

    public record Surge(
            @NotNull @DecimalMin("18.85") @DecimalMax("19.30") Double lat,
            @NotNull @DecimalMin("72.75") @DecimalMax("73.05") Double lon,
            @NotNull @Min(2) @Max(20) Integer casualties,
            @Size(max = 200) String description) {
    }

    public record Location(
            @NotNull @DecimalMin("18.85") @DecimalMax("19.30") Double lat,
            @NotNull @DecimalMin("72.75") @DecimalMax("73.05") Double lon,
            @DecimalMin("0") @DecimalMax("360") Double heading,
            @DecimalMin("0") @DecimalMax("200") Double speedKmph) {
    }

    public record Settings(
            @DecimalMin("0.5") @DecimalMax("60") Double speed,
            Boolean autoCalls,
            @DecimalMin("0") @DecimalMax("5000") Double callsPerDay,
            Boolean prePositioning,
            Boolean greenCorridor) {
    }
}
