package com.goldenhour.dispatch.live;

import com.goldenhour.dispatch.corridor.SignalWindow;
import com.goldenhour.dispatch.routing.Route;

import java.time.LocalDateTime;
import java.util.List;

/**
 * An ambulance driving a route that started at {@code start} (simulated time).
 * Position at any moment = route.positionAt(now - start).
 */
public record Motion(long ambulanceId, Long tripId, Purpose purpose, Route route, LocalDateTime start,
                     Integer postId, List<SignalWindow> corridor) {

    public enum Purpose { TO_SCENE, TO_HOSPITAL, REPOSITION }

    public double elapsed(LocalDateTime now) {
        return SimClock.secondsBetween(start, now);
    }

    public boolean arrived(LocalDateTime now) {
        return elapsed(now) >= route.totalSeconds();
    }

    public double[] position(LocalDateTime now) {
        return route.positionAt(elapsed(now));
    }

    public double remainingSeconds(LocalDateTime now) {
        return route.remainingSeconds(elapsed(now));
    }

    public LocalDateTime eta() {
        return start.plusNanos((long) (route.totalSeconds() * 1e9));
    }
}
