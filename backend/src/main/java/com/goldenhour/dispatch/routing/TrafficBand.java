package com.goldenhour.dispatch.routing;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

/**
 * Three coarse traffic regimes for Mumbai. Coarse on purpose: it lets us precompute travel-time
 * tables per band (see {@link TravelTimeTable}) while still capturing that a 5 km trip takes twice as
 * long at 6 pm on a weekday as at 2 am.
 */
public enum TrafficBand {
    NIGHT,
    OFF_PEAK,
    PEAK;

    public static TrafficBand at(LocalDateTime time) {
        int h = time.getHour();
        boolean weekend = time.getDayOfWeek() == DayOfWeek.SATURDAY || time.getDayOfWeek() == DayOfWeek.SUNDAY;
        if (h >= 23 || h < 6) {
            return NIGHT;
        }
        if (!weekend && ((h >= 8 && h < 11) || (h >= 17 && h < 21))) {
            return PEAK;
        }
        return OFF_PEAK;
    }
}
