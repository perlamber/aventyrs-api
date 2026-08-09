package org.aventyrs.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket transport for the upcoming action endpoints — a game action (e.g.
 * resolving a Scene interaction) needs to broadcast its result to every client watching that
 * Scene/CharacterSheet, which is a pub/sub broadcast rather than a single request/response, so
 * STOMP's topic-based messaging fits better here than a raw {@code WebSocketHandler}.
 *
 * <p>No {@code @MessageMapping} handlers exist yet — this is transport plumbing only, ready for
 * the action endpoints to publish onto {@code /topic/**} destinations once they land. Allowed
 * origins are wide open for now since the frontend's dev/prod origins aren't pinned down yet;
 * tighten {@link #registerStompEndpoints} before shipping to production. SockJS fallback is
 * intentionally omitted — add {@code .withSockJS()} if a client without native WebSocket support
 * needs to be served.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }
}
