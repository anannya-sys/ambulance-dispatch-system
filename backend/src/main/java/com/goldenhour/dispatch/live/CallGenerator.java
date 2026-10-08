package com.goldenhour.dispatch.live;

import com.goldenhour.dispatch.config.GoldenHourProperties;
import com.goldenhour.dispatch.forecast.CallPattern;
import com.goldenhour.dispatch.forecast.SyntheticCall;
import com.goldenhour.dispatch.incident.IncidentSource;
import com.goldenhour.dispatch.incident.IncidentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Random;

/** Plays the callers: emergencies arrive following the city's time-of-day pattern. */
@Component
public class CallGenerator {

    private static final Logger log = LoggerFactory.getLogger(CallGenerator.class);

    private final GoldenHourProperties props;
    private final LiveSettings settings;
    private final SimClock clock;
    private final CallPattern pattern;
    private final IncidentService incidents;
    private final Random rnd = new Random();
    private LocalDateTime last;

    public CallGenerator(GoldenHourProperties props, LiveSettings settings, SimClock clock, CallPattern pattern,
                         IncidentService incidents) {
        this.props = props;
        this.settings = settings;
        this.clock = clock;
        this.pattern = pattern;
        this.incidents = incidents;
    }

    @Scheduled(fixedRate = 1000, initialDelay = 5000)
    public void generate() {
        LocalDateTime now = clock.now();
        if (last == null || !props.live().enabled() || !settings.autoCalls()) {
            last = now;
            return;
        }
        double seconds = SimClock.secondsBetween(last, now);
        last = now;
        double perHour = pattern.expectedCallsInHour(now.withMinute(0).withSecond(0).withNano(0))
                * settings.callsPerDay() / pattern.callsPerDay();
        double expected = perHour / 3600 * seconds;
        // Poisson thinning: number of calls in this slice
        int n = 0;
        double l = Math.exp(-expected);
        double p = rnd.nextDouble();
        while (p > l) {
            n++;
            p *= rnd.nextDouble();
        }
        for (int i = 0; i < n; i++) {
            SyntheticCall c = pattern.sampleOne(rnd, now);
            try {
                incidents.create(c.lat(), c.lon(), c.severity(), c.patientType(), IncidentSource.AUTO, c.hotspot());
            } catch (RuntimeException e) {
                log.debug("Generated call skipped: {}", e.getMessage());
            }
        }
    }
}
