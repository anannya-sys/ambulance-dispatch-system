package com.goldenhour.dispatch.forecast;

import com.goldenhour.dispatch.incident.PatientType;
import com.goldenhour.dispatch.incident.Severity;

import java.time.LocalDateTime;

/** One emergency call produced by the synthetic city model. {@code node} is the road node it snaps to. */
public record SyntheticCall(LocalDateTime time, double lat, double lon, int node,
                            Severity severity, PatientType patientType, String hotspot) {
}
