package com.goldenhour.dispatch.config;

import com.goldenhour.dispatch.forecast.CallPattern;
import com.goldenhour.dispatch.forecast.DemandModel;
import com.goldenhour.dispatch.routing.CityModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Builds the static city model once and the demand model from 8 weeks of call history. */
@Configuration
public class CityConfig {

    private static final Logger log = LoggerFactory.getLogger(CityConfig.class);

    @Bean
    public CityModel cityModel() {
        long t = System.currentTimeMillis();
        CityModel city = CityModel.load();
        log.info("Road graph loaded: {} junctions, {} road segments, {} hospitals, {} standby posts in {} ms",
                city.graph().nodeCount(), city.graph().edgeCount(), city.hospitals().size(), city.posts().size(),
                System.currentTimeMillis() - t);
        return city;
    }

    @Bean
    public CallPattern callPattern(CityModel city, GoldenHourProperties props) {
        return new CallPattern(city.graph(), props.live().callsPerDay());
    }

    /**
     * In production this would be trained on the real call log; here the history is 8 weeks of
     * synthetic calls. Live calls are added to it as they come in.
     */
    @Bean
    public DemandModel demandModel(CallPattern pattern) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata")).withMinute(0).withSecond(0).withNano(0);
        var history = pattern.generate(now.minusWeeks(8), now, 42);
        log.info("Demand model trained on {} historical calls", history.size());
        return DemandModel.train(history);
    }
}
