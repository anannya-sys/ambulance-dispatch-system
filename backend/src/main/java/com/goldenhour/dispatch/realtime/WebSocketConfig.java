package com.goldenhour.dispatch.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket. The dashboard connects to {@code /ws} and subscribes to topics:
 * <ul>
 *   <li>{@code /topic/fleet} - every ambulance's position and status, once per second</li>
 *   <li>{@code /topic/events} - trip lifecycle events (from the Kafka dispatch-events stream)</li>
 *   <li>{@code /topic/alerts} - hospital pre-alerts</li>
 *   <li>{@code /topic/positioning} - latest forecast and chosen standby posts</li>
 * </ul>
 * The simple in-memory broker is enough for one instance; with several instances you would switch to a
 * broker relay (RabbitMQ/ActiveMQ) or fan out from Kafka on each node.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
