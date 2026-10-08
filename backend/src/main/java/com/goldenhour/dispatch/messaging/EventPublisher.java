package com.goldenhour.dispatch.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Thin wrapper over KafkaTemplate so services don't depend on topic names or keys. */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, Object> kafka;

    public EventPublisher(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    public void location(LocationPing ping) {
        kafka.send(KafkaTopics.LOCATIONS, String.valueOf(ping.ambulanceId()), ping);
    }

    public void dispatch(DispatchEvent event) {
        String key = event.tripId() != null ? String.valueOf(event.tripId()) : String.valueOf(event.incidentId());
        kafka.send(KafkaTopics.DISPATCH_EVENTS, key, event)
                .whenComplete((r, e) -> {
                    if (e != null) {
                        log.warn("Could not publish {} event: {}", event.type(), e.getMessage());
                    }
                });
    }
}
