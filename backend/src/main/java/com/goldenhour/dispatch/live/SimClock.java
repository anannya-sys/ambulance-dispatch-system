package com.goldenhour.dispatch.live;

import com.goldenhour.dispatch.config.GoldenHourProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Simulated city clock. It starts at the current Mumbai time and runs {@code speed} times faster than
 * real time, so a 20-minute ambulance trip plays out in a couple of minutes on the dashboard.
 * Every time-dependent decision (traffic band, specialist rosters, 30 s acknowledgement rule) reads
 * this clock, never the system clock, which also makes the logic testable.
 */
@Component
public class SimClock {

    public static final ZoneId MUMBAI = ZoneId.of("Asia/Kolkata");

    private LocalDateTime anchorSim;
    private long anchorRealNanos;
    private double speed;

    public SimClock(GoldenHourProperties props) {
        this.anchorSim = LocalDateTime.now(MUMBAI).withNano(0);
        this.anchorRealNanos = System.nanoTime();
        this.speed = props.live().speed();
    }

    public synchronized LocalDateTime now() {
        long realNanos = System.nanoTime() - anchorRealNanos;
        return anchorSim.plusNanos((long) (realNanos * speed));
    }

    public synchronized double speed() {
        return speed;
    }

    /** Change speed without making simulated time jump. */
    public synchronized void setSpeed(double newSpeed) {
        LocalDateTime current = now();
        anchorSim = current;
        anchorRealNanos = System.nanoTime();
        speed = Math.max(0.5, Math.min(60, newSpeed));
    }

    /** Simulated seconds between two simulated times. */
    public static double secondsBetween(LocalDateTime a, LocalDateTime b) {
        return Duration.between(a, b).toMillis() / 1000.0;
    }
}
