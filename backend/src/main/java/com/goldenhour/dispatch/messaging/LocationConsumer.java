package com.goldenhour.dispatch.messaging;

import com.goldenhour.dispatch.fleet.AmbulanceRepository;
import com.goldenhour.dispatch.routing.CityModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracking service: consumes GPS pings in batches and
 * <ol>
 *   <li>updates the ambulance row (position + nearest road node) so PostGIS KNN sees fresh positions,</li>
 *   <li>updates the Redis GEO set {@code fleet:positions}, a fast shared cache for "who is near here?".</li>
 * </ol>
 * Batching turns 50 pings/s into one transaction instead of 50.
 */
@Component
public class LocationConsumer {

    public static final String GEO_KEY = "fleet:positions";
    private static final Logger log = LoggerFactory.getLogger(LocationConsumer.class);

    private final AmbulanceRepository ambulances;
    private final CityModel city;
    private final StringRedisTemplate redis;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public LocationConsumer(AmbulanceRepository ambulances, CityModel city, StringRedisTemplate redis,
                            TransactionTemplate tx, ObjectMapper json) {
        this.ambulances = ambulances;
        this.city = city;
        this.redis = redis;
        this.tx = tx;
        this.json = json;
    }

    @KafkaListener(topics = KafkaTopics.LOCATIONS, groupId = "tracking", batch = "true")
    public void onBatch(List<ConsumerRecord<String, String>> records) {
        List<LocationPing> pings = new ArrayList<>(records.size());
        for (ConsumerRecord<String, String> r : records) {
            try {
                pings.add(json.readValue(r.value(), LocationPing.class));
            } catch (JsonProcessingException e) {
                log.warn("Skipping malformed location ping at offset {}: {}", r.offset(), e.getOriginalMessage());
            }
        }
        onPings(pings);
    }

    void onPings(List<LocationPing> pings) {
        tx.executeWithoutResult(s -> pings.forEach(p -> ambulances.updatePosition(p.ambulanceId(), p.lat(), p.lon(),
                city.graph().nearestNode(p.lat(), p.lon()), p.heading(), p.at())));
        pings.forEach(p -> redis.opsForGeo().add(GEO_KEY, new Point(p.lon(), p.lat()), String.valueOf(p.ambulanceId())));
    }
}
