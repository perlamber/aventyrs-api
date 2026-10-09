package org.aventyrs.api.auth;

import java.time.Instant;
import org.aventyrs.api.player.PlayerDocument;
import org.aventyrs.api.player.PlayerRole;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues the HS256 bearer token a player gets at login. The subject is the player id; {@code
 * login}, {@code name} and {@code role} ride along as claims so neither the REST filter chain nor
 * the STOMP interceptor has to look the player up again on every request.
 */
@Service
public class TokenService {

    public static final String ISSUER = "aventyrs-api";
    public static final String CLAIM_LOGIN = "login";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_ROLE = "role";

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public TokenService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public IssuedToken issue(PlayerDocument player, PlayerRole role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(player.getId())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(CLAIM_LOGIN, player.getLogin())
                .claim(CLAIM_NAME, player.getName())
                .claim(CLAIM_ROLE, role.name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
