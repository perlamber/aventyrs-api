package org.aventyrs.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI aventyrsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Aventyrs API")
                        .description("Light API exposing the Aventyrs core rules engine — CharacterSheet, "
                                + "Player and Scene persistence over REST, with a STOMP-over-WebSocket "
                                + "transport reserved for upcoming real-time actions (skill rolls, movement).")
                        .version("v0"))
                // Lets Swagger UI's "Authorize" button send the token from POST /api/auth/login.
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
