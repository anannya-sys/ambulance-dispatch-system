package com.goldenhour.dispatch.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldenhour.dispatch.realtime.LivePublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Two independent consumers of the same {@value KafkaTopics#DISPATCH_EVENTS} stream (different consumer
 * groups, so each gets every event): one relays events to the dashboard's live feed, the other turns
 * them into metrics. Adding an SMS notifier or a data-warehouse loader later is just another group.
 */
@Component
public class DispatchEventConsumers {

    private final LivePublisher live;
    private final MeterRegistry meters;
    private final ObjectMapper json;

    public DispatchEventConsumers(LivePublisher live, MeterRegistry meters, ObjectMapper json) {
        this.live = live;
        this.meters = meters;
        this.json = json;
    }

    @KafkaListener(topics = KafkaTopics.DISPATCH_EVENTS, groupId = "dashboard-relay")
    public void relay(String payload) throws JsonProcessingException {
        live.event(json.readValue(payload, DispatchEvent.class));
    }

    @KafkaListener(topics = KafkaTopics.DISPATCH_EVENTS, groupId = "metrics")
    public void count(String payload) throws JsonProcessingException {
        DispatchEvent e = json.readValue(payload, DispatchEvent.class);
        meters.counter("goldenhour.events", "type", e.type(),
                "severity", e.severity() == null ? "NA" : e.severity()).increment();
    }
}
