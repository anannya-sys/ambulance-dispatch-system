package com.goldenhour.dispatch.incident;

/**
 * Priority score used when there are more open calls than free ambulances.
 *
 * <p>score = severity base score + {@value #AGING_PER_MINUTE} points per minute waited.
 * A CRITICAL call (100) always beats a fresh MINOR call (10), but aging guarantees no call
 * waits forever: a MINOR call that has waited 45 minutes (10 + 90) outranks a fresh CRITICAL one.
 * This is the classic fix for starvation in priority scheduling.</p>
 */
public final class DispatchPriority {

    public static final double AGING_PER_MINUTE = 2.0;

    private DispatchPriority() {
    }

    public static double score(Severity severity, double secondsWaiting) {
        return severity.baseScore() + AGING_PER_MINUTE * (secondsWaiting / 60.0);
    }
}
