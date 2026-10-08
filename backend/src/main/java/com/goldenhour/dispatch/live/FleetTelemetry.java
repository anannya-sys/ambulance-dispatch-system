package com.goldenhour.dispatch.live;

import com.goldenhour.dispatch.config.GoldenHourProperties;
import com.goldenhour.dispatch.fleet.Ambulance;
import com.goldenhour.dispatch.fleet.AmbulanceRepository;
import com.goldenhour.dispatch.messaging.EventPublisher;
import com.goldenhour.dispatch.messaging.LocationPing;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Plays the GPS device in each ambulance: moving vehicles report every second, parked ones every 10 s.
 * Pings go to Kafka exactly like a real device posting to {@code POST /api/ambulances/{id}/location}.
 */
@Component
public class FleetTelemetry {

    private final GoldenHourProperties props;
    private final SimClock clock;
    private final LiveState state;
    private final AmbulanceRepository ambulances;
    private final EventPublisher publisher;
    private List<Long> fleetIds = List.of();
    private int tick;

    public FleetTelemetry(GoldenHourProperties props, SimClock clock, LiveState state, AmbulanceRepository ambulances,
                          EventPublisher publisher) {
        this.props = props;
        this.clock = clock;
        this.state = state;
        this.ambulances = ambulances;
        this.publisher = publisher;
    }

    @Scheduled(fixedRate = 1000, initialDelay = 3000)
    public void report() {
        if (!props.live().enabled()) {
            return;
        }
        if (fleetIds.isEmpty() || tick % 60 == 0) {
            fleetIds = ambulances.findAll().stream().map(Ambulance::getId).toList();
        }
        tick++;
        LocalDateTime now = clock.now();
        for (Long id : fleetIds) {
            Motion m = state.motion(id);
            if (m == null && tick % 10 != 0) {
                continue;
            }
            LiveState.Position p = state.currentPosition(id, now);
            if (p == null) {
                continue;
            }
            double speed = m == null ? 0 : m.route().totalMeters() / Math.max(1, m.route().totalSeconds()) * 3.6;
            publisher.location(new LocationPing(id, p.lat(), p.lon(), p.heading(), speed, now));
        }
    }
}
