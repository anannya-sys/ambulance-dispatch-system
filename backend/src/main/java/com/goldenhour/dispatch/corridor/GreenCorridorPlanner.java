package com.goldenhour.dispatch.corridor;

import com.goldenhour.dispatch.incident.Severity;
import com.goldenhour.dispatch.routing.Route;
import com.goldenhour.dispatch.routing.TrafficModel;

import java.time.LocalDateTime;
import java.util.List;

/**
 * USP 3 - automatic green corridor.
 *
 * <p>For a critical patient, every signalised junction on the route gets a pre-emption window timed to
 * the ambulance's ETA at that junction (a little early, to flush the queue of cars in front). In a real
 * deployment these windows would go to the city's traffic-signal controllers or traffic police;
 * here they drive the green-signal animation on the map and the simulation's travel times.</p>
 */
public final class GreenCorridorPlanner {

    /** Turn green this many seconds before the ambulance arrives so the queue in front clears. */
    static final long LEAD_SECONDS = 20;
    /** Keep green this long after the ETA to absorb ETA error. */
    static final long TRAIL_SECONDS = 15;

    private GreenCorridorPlanner() {
    }

    public static boolean eligible(Severity severity) {
        return severity == Severity.CRITICAL;
    }

    public static List<SignalWindow> plan(Route route, LocalDateTime departure) {
        return route.signals().stream().map(s -> {
            LocalDateTime eta = departure.plusSeconds((long) s.secondsFromStart());
            return new SignalWindow(s.node(), s.lat(), s.lon(),
                    eta.minusSeconds(LEAD_SECONDS), eta.plusSeconds(TRAIL_SECONDS));
        }).toList();
    }

    /** Expected seconds saved versus waiting at each signal. */
    public static double secondsSaved(Route corridorRoute) {
        return corridorRoute.signals().size()
                * (TrafficModel.SIGNAL_DELAY_SECONDS - TrafficModel.CORRIDOR_SIGNAL_DELAY_SECONDS);
    }
}
