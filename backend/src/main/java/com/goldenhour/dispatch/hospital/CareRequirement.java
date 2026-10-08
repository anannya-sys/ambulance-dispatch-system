package com.goldenhour.dispatch.hospital;

import com.goldenhour.dispatch.incident.PatientType;
import com.goldenhour.dispatch.incident.Severity;

/**
 * What a patient needs from the receiving hospital. {@code capability}/{@code specialist} are null when
 * any hospital will do.
 */
public record CareRequirement(BedType bed, Capability capability, Specialist specialist) {

    public static CareRequirement of(PatientType type, Severity severity) {
        boolean urgent = severity != Severity.MINOR;
        return switch (type) {
            case CARDIAC -> urgent
                    ? new CareRequirement(BedType.CARDIAC, Capability.CATH_LAB, Specialist.CARDIOLOGIST)
                    : general();
            case STROKE -> urgent
                    ? new CareRequirement(BedType.ICU, Capability.STROKE_UNIT, Specialist.NEUROLOGIST)
                    : general();
            case TRAUMA -> switch (severity) {
                case CRITICAL -> new CareRequirement(BedType.TRAUMA, Capability.TRAUMA_CENTRE, Specialist.TRAUMA_SURGEON);
                case SERIOUS -> new CareRequirement(BedType.TRAUMA, Capability.TRAUMA_CENTRE, null);
                case MINOR -> general();
            };
            case RESPIRATORY -> severity == Severity.CRITICAL
                    ? new CareRequirement(BedType.ICU, null, null)
                    : general();
            case MATERNITY -> new CareRequirement(BedType.GENERAL, Capability.MATERNITY,
                    urgent ? Specialist.OBSTETRICIAN : null);
            case GENERAL -> severity == Severity.CRITICAL
                    ? new CareRequirement(BedType.ICU, null, null)
                    : general();
        };
    }

    private static CareRequirement general() {
        return new CareRequirement(BedType.GENERAL, null, null);
    }
}
