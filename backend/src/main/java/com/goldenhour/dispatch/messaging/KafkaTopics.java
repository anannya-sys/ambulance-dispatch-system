package com.goldenhour.dispatch.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topics.
 * <ul>
 *   <li>{@value #LOCATIONS}: GPS pings, keyed by ambulance id so each vehicle's pings stay in order
 *       within one partition.</li>
 *   <li>{@value #DISPATCH_EVENTS}: trip lifecycle events, keyed by trip id. Consumers (live dashboard
 *       relay, analytics) each read it independently with their own consumer group.</li>
 * </ul>
 */
@Configuration
public class KafkaTopics {

    public static final String LOCATIONS = "ambulance.locations";
    public static final String DISPATCH_EVENTS = "dispatch.events";

    @Bean
    public NewTopic locationsTopic() {
        return TopicBuilder.name(LOCATIONS).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic dispatchEventsTopic() {
        return TopicBuilder.name(DISPATCH_EVENTS).partitions(3).replicas(1).build();
    }
}
