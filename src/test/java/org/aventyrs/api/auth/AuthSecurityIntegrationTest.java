package org.aventyrs.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.aventyrs.api.player.PlayerDocument;
import org.aventyrs.api.player.PlayerRepository;
import org.aventyrs.api.player.PlayerRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AuthSecurityIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlayerRepository players;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String playerLogin;
    private String gmLogin;

    @BeforeEach
    void seedAccounts() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        playerLogin = "player-" + suffix;
        gmLogin = "gm-" + suffix;
        players.save(new PlayerDocument(UUID.randomUUID().toString(), "A Player", playerLogin,
                PlayerRole.PLAYER, passwordEncoder.encode("player-pass")));
        players.save(new PlayerDocument(UUID.randomUUID().toString(), "The GM", gmLogin,
                PlayerRole.GM, passwordEncoder.encode("gm-pass")));
    }

    @Test
    void loginReturnsTokenAndPlayer() throws Exception {
        mockMvc.perform(login(playerLogin, "player-pass"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.player.login").value(playerLogin))
                .andExpect(jsonPath("$.player.role").value("PLAYER"))
                .andExpect(jsonPath("$.player.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordUnknownLoginAndPasswordlessAccountAreAllUnauthorized() throws Exception {
        players.save(new PlayerDocument(UUID.randomUUID().toString(), "No Password", "nopass-" + playerLogin,
                PlayerRole.PLAYER, null));

        mockMvc.perform(login(playerLogin, "wrong")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("nobody-" + playerLogin, "player-pass")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("nopass-" + playerLogin, "")).andExpect(status().isBadRequest());
        mockMvc.perform(login("nopass-" + playerLogin, "anything")).andExpect(status().isUnauthorized());
    }

    @Test
    void apiRequiresATokenExceptLoginAndSkills() throws Exception {
        mockMvc.perform(get("/api/players")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/character-sheets")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/skills")).andExpect(status().isOk());
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        String token = tokenFor(playerLogin, "player-pass");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        mockMvc.perform(get("/api/players").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheTokensPlayer() throws Exception {
        String token = tokenFor(gmLogin, "gm-pass");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value(gmLogin))
                .andExpect(jsonPath("$.role").value("GM"));
    }

    @Test
    void playerCanReadButNotRunTheTable() throws Exception {
        String bearer = "Bearer " + tokenFor(playerLogin, "player-pass");

        mockMvc.perform(get("/api/players").header("Authorization", bearer)).andExpect(status().isOk());
        mockMvc.perform(get("/api/monster-sheets").header("Authorization", bearer)).andExpect(status().isOk());

        mockMvc.perform(delete("/api/monster-sheets/{id}", "missing").header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/scenes/{id}", "missing").header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/campaigns/{id}", "missing").header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/players/{id}", "missing").header("Authorization", bearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void gmGetsPastTheRoleCheck() throws Exception {
        String bearer = "Bearer " + tokenFor(gmLogin, "gm-pass");

        // 404 rather than 403: authorized, and the target just doesn't exist.
        mockMvc.perform(delete("/api/monster-sheets/{id}", "missing").header("Authorization", bearer))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/players/{id}", "missing").header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }

    private String tokenFor(String login, String password) throws Exception {
        String body = mockMvc.perform(login(login, password))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private org.springframework.test.web.servlet.RequestBuilder login(String login, String password) throws Exception {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new org.aventyrs.api.auth.dto.LoginRequest(login, password)));
    }
}
