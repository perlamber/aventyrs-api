package org.aventyrs.api.config;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.aventyrs.api.auth.JwtProperties;
import org.aventyrs.api.auth.TokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless JWT security for the REST API. {@code POST /api/auth/login} trades a login and
 * password for a token (see {@link TokenService}); every other {@code /api} request needs it as
 * {@code Authorization: Bearer}, and the table-running endpoints below additionally need the GM
 * role. The {@code /ws} handshake itself is open — STOMP frames are authenticated one by one in
 * {@link StompAuthChannelInterceptor}, since the client opens its socket before anyone has
 * logged in.
 *
 * <p>Rules are by URL rather than {@code @PreAuthorize} so an {@code AccessDeniedException} is
 * answered by the filter chain as a 403, instead of surfacing inside a controller where {@code
 * GlobalExceptionHandler} would see it.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    public static final String ROLE_GM = "GM";

    /** Only in a servlet app — non-web test contexts still get the encoder/decoder beans below. */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(POST, "/api/auth/login").permitAll()
                        // Static rules data, and the client's pre-login "is the server up" probe.
                        .requestMatchers(GET, "/api/skills").permitAll()
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()

                        // Accounts are provisioned by a database administrator; through the API
                        // only a GM may change them.
                        .requestMatchers(POST, "/api/players", "/api/players/**").hasRole(ROLE_GM)
                        .requestMatchers(PUT, "/api/players/**").hasRole(ROLE_GM)
                        .requestMatchers(DELETE, "/api/players/**").hasRole(ROLE_GM)

                        // Scene authoring and table control. Joining/leaving, travel and summons
                        // stay open to players.
                        .requestMatchers(POST, "/api/scenes").hasRole(ROLE_GM)
                        .requestMatchers(PUT, "/api/scenes/*", "/api/scenes/*/active",
                                "/api/scenes/*/connections").hasRole(ROLE_GM)
                        .requestMatchers(DELETE, "/api/scenes/*").hasRole(ROLE_GM)
                        .requestMatchers(POST, "/api/scenes/*/combat", "/api/scenes/*/combat/end",
                                "/api/scenes/*/time").hasRole(ROLE_GM)

                        // Monster stat blocks: readable by everyone, authored by the GM.
                        .requestMatchers(POST, "/api/monster-sheets").hasRole(ROLE_GM)
                        .requestMatchers(PUT, "/api/monster-sheets/**").hasRole(ROLE_GM)
                        .requestMatchers(DELETE, "/api/monster-sheets/**").hasRole(ROLE_GM)

                        // Campaigns: the GM runs sessions, rosters and the bag's contents;
                        // players claim, deposit and loot.
                        .requestMatchers(POST, "/api/campaigns", "/api/campaigns/*/sessions",
                                "/api/campaigns/*/sessions/*/start", "/api/campaigns/*/sessions/*/end",
                                "/api/campaigns/*/bag").hasRole(ROLE_GM)
                        .requestMatchers(PUT, "/api/campaigns/*/participants/*").hasRole(ROLE_GM)
                        .requestMatchers(DELETE, "/api/campaigns/*", "/api/campaigns/*/participants/*",
                                "/api/campaigns/*/bag/*").hasRole(ROLE_GM)

                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return NimbusJwtEncoder.withSecretKey(secretKey(properties)).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(TokenService.ISSUER)));
        return decoder;
    }

    /**
     * Principal name = the player id ({@code sub}); the {@code role} claim becomes {@code
     * ROLE_<role>}. Shared with the STOMP interceptor so both transports read a token the same way.
     */
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            String role = jwt.getClaimAsString(TokenService.CLAIM_ROLE);
            List<SimpleGrantedAuthority> authorities = role == null
                    ? List.of()
                    : List.of(new SimpleGrantedAuthority("ROLE_" + role));
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }

    private static SecretKey secretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
