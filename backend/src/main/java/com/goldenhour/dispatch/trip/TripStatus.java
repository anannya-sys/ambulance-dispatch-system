package com.goldenhour.dispatch.trip;

import java.util.EnumSet;
import java.util.Set;

/**
 * Trip lifecycle as a state machine. Each state lists the states it may move to; anything else is a bug
 * and throws, which keeps the live system and its data consistent.
 *
 * <pre>
 *   ASSIGNED -> EN_ROUTE -> AT_SCENE -> PICKED_UP -> AT_HOSPITAL -> CLOSED
 *       \__________\_____ CANCELLED (no acknowledgement in 30 s, or breakdown)
 * </pre>
 */
public enum TripStatus {
    ASSIGNED,
    EN_ROUTE,
    AT_SCENE,
    PICKED_UP,
    AT_HOSPITAL,
    CLOSED,
    CANCELLED;

    public Set<TripStatus> next() {
        return switch (this) {
            case ASSIGNED -> EnumSet.of(EN_ROUTE, CANCELLED);
            case EN_ROUTE -> EnumSet.of(AT_SCENE, CANCELLED);
            case AT_SCENE -> EnumSet.of(PICKED_UP);
            case PICKED_UP -> EnumSet.of(AT_HOSPITAL);
            case AT_HOSPITAL -> EnumSet.of(CLOSED);
            case CLOSED, CANCELLED -> EnumSet.noneOf(TripStatus.class);
        };
    }

    public boolean canMoveTo(TripStatus target) {
        return next().contains(target);
    }

    public boolean isOpen() {
        return this != CLOSED && this != CANCELLED;
    }
}
