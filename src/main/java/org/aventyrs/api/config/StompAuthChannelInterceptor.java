package org.aventyrs.api.config;

import java.util.regex.Pattern;
import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP frames with the same JWT the REST API takes.
 *
 * <p>The client opens its WebSocket on the initial screen, before anyone has logged in, so an
 * anonymous CONNECT is accepted; what's guarded is every SEND and SUBSCRIBE. Each may carry the
 * token in an {@code Authorization} native header ({@code Bearer <token>} or the bare token); a
 * CONNECT that carries one authenticates the whole session instead, and a frame without its own
 * header falls back to that. A frame with neither, or with a bad or expired token, is refused —
 * the broker answers with an ERROR frame and closes the session, which the client's reconnect loop
 * then picks up.
 *
 * <p>The GM-only destinations mirror what the client only shows the GM: resizing the grid,
 * starting and ending combat, passing time, painting terrain, overriding initiative and granting or
 * dismissing Subordinados.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String GM_AUTHORITY = "ROLE_" + SecurityConfig.ROLE_GM;
    private static final Pattern GM_ONLY_DESTINATION =
            Pattern.compile("^/app/scenes/[^/]+/(grid|combat|combat/end|time|terrain|initiative|subordinates)$");

    private final JwtDecoder jwtDecoder;
    private final Converter<org.springframework.security.oauth2.jwt.Jwt, AbstractAuthenticationToken> converter;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder,
            Converter<org.springframework.security.oauth2.jwt.Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter) {
        this.jwtDecoder = jwtDecoder;
        this.converter = jwtAuthenticationConverter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        String token = token(accessor);

        if (command == StompCommand.CONNECT) {
            if (token != null) {
                accessor.setUser(authenticate(token));
            }
            return message;
        }
        if (command != StompCommand.SEND && command != StompCommand.SUBSCRIBE) {
            return message;
        }

        AbstractAuthenticationToken authentication;
        if (token != null) {
            authentication = authenticate(token);
            accessor.setUser(authentication);
        } else if (accessor.getUser() instanceof AbstractAuthenticationToken sessionUser) {
            authentication = sessionUser;
        } else {
            throw new MessageDeliveryException(message, "Authentication required");
        }

        if (command == StompCommand.SEND && isGmOnly(accessor.getDestination()) && !isGm(authentication)) {
            throw new MessageDeliveryException(message, "Only the GM may send to " + accessor.getDestination());
        }
        return message;
    }

    private AbstractAuthenticationToken authenticate(String token) {
        try {
            return converter.convert(jwtDecoder.decode(token));
        } catch (JwtException ex) {
            throw new MessageDeliveryException("Invalid or expired token");
        }
    }

    private static String token(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (header == null || header.isBlank()) {
            return null;
        }
        return header.startsWith(BEARER_PREFIX) ? header.substring(BEARER_PREFIX.length()).trim() : header.trim();
    }

    private static boolean isGmOnly(String destination) {
        return destination != null && GM_ONLY_DESTINATION.matcher(destination).matches();
    }

    private static boolean isGm(AbstractAuthenticationToken authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> GM_AUTHORITY.equals(a.getAuthority()));
    }
}
