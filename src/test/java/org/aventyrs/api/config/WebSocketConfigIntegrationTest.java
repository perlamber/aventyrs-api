package org.aventyrs.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.aventyrs.api.auth.TokenService;
import org.aventyrs.api.player.PlayerDocument;
import org.aventyrs.api.player.PlayerRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketConfigIntegrationTest {

    private static final byte[] PING = "{\"position\":{\"x\":1,\"y\":2}}".getBytes(StandardCharsets.UTF_8);

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @LocalServerPort
    private int port;

    @Autowired
    private TokenService tokenService;

    @Test
    void acceptsAnonymousStompHandshake() throws Exception {
        StompSession session = connect(new StompHeaders());

        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    @Test
    void sendWithoutTokenIsRefused() throws Exception {
        StompSession session = connect(new StompHeaders());

        session.send(sendHeaders("/app/scenes/s1/ping", null), PING);

        assertDropped(session);
    }

    @Test
    void sendAndSubscribeWithTokenAreRelayed() throws Exception {
        String token = token(PlayerRole.PLAYER);
        StompSession session = connect(new StompHeaders());
        String sceneId = UUID.randomUUID().toString();
        CompletableFuture<byte[]> received = subscribe(session, "/topic/scenes/" + sceneId + "/pings", token);

        byte[] payload = sendUntilReceived(session, sendHeaders("/app/scenes/" + sceneId + "/ping", token), received);

        assertThat(new String(payload, StandardCharsets.UTF_8)).contains("\"x\":1");
    }

    @Test
    void tokenOnConnectAuthenticatesTheWholeSession() throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token(PlayerRole.PLAYER));
        StompSession session = connect(connectHeaders);
        String sceneId = UUID.randomUUID().toString();
        CompletableFuture<byte[]> received = subscribe(session, "/topic/scenes/" + sceneId + "/pings", null);

        assertThat(sendUntilReceived(session, sendHeaders("/app/scenes/" + sceneId + "/ping", null), received))
                .isNotEmpty();
    }

    @Test
    void playerCannotSendGmOnlyFrames() throws Exception {
        StompSession session = connect(new StompHeaders());

        session.send(sendHeaders("/app/scenes/s1/combat", token(PlayerRole.PLAYER)), new byte[0]);

        assertDropped(session);
    }

    @Test
    void invalidTokenIsRefused() throws Exception {
        StompSession session = connect(new StompHeaders());

        session.send(sendHeaders("/app/scenes/s1/ping", "not-a-jwt"), PING);

        assertDropped(session);
    }

    private StompSession connect(StompHeaders connectHeaders) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        return stompClient
                .connectAsync("ws://localhost:" + port + "/ws", (org.springframework.web.socket.WebSocketHttpHeaders) null, connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    private static CompletableFuture<byte[]> subscribe(StompSession session, String destination, String token)
            throws Exception {
        CompletableFuture<byte[]> received = new CompletableFuture<>();
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        if (token != null) {
            headers.add("Authorization", "Bearer " + token);
        }
        session.subscribe(headers, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders frameHeaders) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders frameHeaders, Object payload) {
                received.complete((byte[]) payload);
            }
        });
        return received;
    }

    /**
     * The simple broker sends no RECEIPT for a SUBSCRIBE, so there's no signal for when it has been
     * registered; resend until the relayed ping arrives (or give up after ~5s).
     */
    private static byte[] sendUntilReceived(StompSession session, StompHeaders headers, CompletableFuture<byte[]> received)
            throws Exception {
        for (int attempt = 0; attempt < 50 && !received.isDone(); attempt++) {
            session.send(headers, PING);
            Thread.sleep(100);
        }
        return received.get(1, TimeUnit.SECONDS);
    }

    private static StompHeaders sendHeaders(String destination, String token) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        headers.setContentType(org.springframework.util.MimeTypeUtils.APPLICATION_JSON);
        if (token != null) {
            headers.add("Authorization", "Bearer " + token);
        }
        return headers;
    }

    private String token(PlayerRole role) {
        PlayerDocument player = new PlayerDocument(UUID.randomUUID().toString(), "Someone", "someone", role, null);
        return tokenService.issue(player, role).token();
    }

    private static void assertDropped(StompSession session) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (session.isConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(session.isConnected()).as("server closes the session after refusing the frame").isFalse();
    }
}
