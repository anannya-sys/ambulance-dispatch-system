package com.goldenhour.dispatch.hospital;

import java.time.LocalDateTime;

/**
 * Simulated specialist duty roster. Big hospitals have specialists in-house 24x7; smaller ones only in
 * the day and on-call at night. Deterministic (hash of hospital, specialist and date) so the live demo
 * and tests are reproducible.
 */
public final class DutyRoster {

    private DutyRoster() {
    }

    /**
     * @param coverage24x7 true for large teaching hospitals
     */
    public static boolean onDuty(long hospitalId, Specialist s, LocalDateTime at, boolean coverage24x7) {
        if (coverage24x7) {
            return true;
        }
        int h = at.getHour();
        boolean day = h >= 9 && h < 21;
        // ~85% chance in the day, ~35% at night, varying by hospital, specialist and day
        long seed = hospitalId * 31 + s.ordinal() * 7L + at.toLocalDate().toEpochDay() * 13 + (day ? 1 : 2);
        double u = ((seed * 0x9E3779B97F4A7C15L) >>> 11) / (double) (1L << 53);
        return u < (day ? 0.85 : 0.35);
    }
}
