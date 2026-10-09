package org.aventyrs.api.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** The GM-only STOMP destinations — here, granting or dismissing Subordinados (core 0.1.5.6). */
class StompAuthChannelInterceptorTest {

    @SuppressWarnings("unchecked")
    private final StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(mock(JwtDecoder.class),
            (Converter<Jwt, AbstractAuthenticationToken>) mock(Converter.class));

    private static Message<byte[]> send(String destination, String role) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken("someone", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void onlyTheGmMaySendSubordinadoChanges() {
        MessageChannel channel = mock(MessageChannel.class);

        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(send("/app/scenes/s1/subordinates", "PLAYER"), channel));
        assertDoesNotThrow(() -> interceptor.preSend(send("/app/scenes/s1/subordinates", SecurityConfig.ROLE_GM), channel));
        assertDoesNotThrow(() -> interceptor.preSend(send("/app/scenes/s1/status", "PLAYER"), channel));
    }
}
