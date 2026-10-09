package org.aventyrs.api.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Signing key and lifetime of the bearer tokens issued at login.
 *
 * @param secret HMAC-SHA256 key; at least 32 bytes. Dev ships a fixed value; prod has no default
 *               and must get one from {@code JWT_SECRET}, so a forgotten secret fails the boot
 *               instead of signing production tokens with the dev key.
 * @param ttl    how long a token stays valid after login
 */
@ConfigurationProperties("aventyrs.security.jwt")
public record JwtProperties(String secret, Duration ttl) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("aventyrs.security.jwt.secret must be set to at least "
                    + MIN_SECRET_BYTES + " bytes (JWT_SECRET in prod)");
        }
        if (ttl == null) {
            ttl = Duration.ofHours(12);
        }
    }
}
