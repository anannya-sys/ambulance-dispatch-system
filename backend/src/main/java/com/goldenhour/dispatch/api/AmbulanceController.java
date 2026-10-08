package com.goldenhour.dispatch.api;

import com.goldenhour.dispatch.api.dto.Requests;
import com.goldenhour.dispatch.api.dto.Views;
import com.goldenhour.dispatch.common.NotFoundException;
import com.goldenhour.dispatch.fleet.AmbulanceRepository;
import com.goldenhour.dispatch.live.LiveEngine;
import com.goldenhour.dispatch.live.SimClock;
import com.goldenhour.dispatch.messaging.EventPublisher;
import com.goldenhour.dispatch.messaging.LocationConsumer;
import com.goldenhour.dispatch.messaging.LocationPing;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ambulances")
@Tag(name = "Fleet", description = "Ambulances, GPS ingestion and breakdowns")
public class AmbulanceController {

    private final AmbulanceRepository ambulances;
    private final LiveEngine engine;
    private final EventPublisher publisher;
    private final SimClock clock;
    private final StringRedisTemplate redis;

    public AmbulanceController(AmbulanceRepository ambulances, LiveEngine engine, EventPublisher publisher,
                               SimClock clock, StringRedisTemplate redis) {
        this.ambulances = ambulances;
        this.engine = engine;
        this.publisher = publisher;
        this.clock = clock;
        this.redis = redis;
    }

    @GetMapping
    public List<Views.AmbulanceView> list() {
        return ambulances.findAll().stream().map(Views.AmbulanceView::of).toList();
    }

    @PostMapping("/{id}/breakdown")
    @Operation(summary = "Simulate a breakdown; if the ambulance was heading to a call, the call is reassigned")
    public Map<String, String> breakdown(@PathVariable long id) {
        return Map.of("message", engine.breakdown(id));
    }

    /**
     * Where a real GPS device in the ambulance would post its position. The ping goes onto the same Kafka
     * topic as the simulated fleet's, so the rest of the system can't tell the difference.
     */
    @PostMapping("/{id}/location")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Ingest a GPS fix from a device (published to Kafka)")
    public Map<String, String> location(@PathVariable long id, @Valid @RequestBody Requests.Location req) {
        if (!ambulances.existsById(id)) {
            throw new NotFoundException("Ambulance", id);
        }
        publisher.location(new LocationPing(id, req.lat(), req.lon(),
                req.heading() == null ? 0 : req.heading(), req.speedKmph() == null ? 0 : req.speedKmph(), clock.now()));
        return Map.of("status", "accepted");
    }

    @GetMapping("/nearby")
    @Operation(summary = "Ambulances within a radius, straight from the Redis GEO cache")
    public List<Map<String, Object>> nearby(@RequestParam double lat, @RequestParam double lon,
                                            @RequestParam(defaultValue = "3") double radiusKm) {
        var results = redis.opsForGeo().radius(LocationConsumer.GEO_KEY,
                new Circle(new Point(lon, lat), new Distance(radiusKm, Metrics.KILOMETERS)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().sortAscending());
        List<Map<String, Object>> out = new ArrayList<>();
        if (results != null) {
            results.forEach(r -> out.add(Map.of("ambulanceId", Long.parseLong(r.getContent().getName()),
                    "distanceKm", Math.round(r.getDistance().getValue() * 100) / 100.0)));
        }
        return out;
    }
}
