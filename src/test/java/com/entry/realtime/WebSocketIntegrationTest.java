package com.entry.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.entry.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.json.JsonMapper;

/** Real server + real STOMP client: token auth on CONNECT, entrance-scoped subscriptions, live delivery. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class WebSocketIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper jsonMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void chatMessageIsDeliveredLiveToEntranceMembers() throws Exception {
        String[] admin = registerAdmin();
        StompSession session = connect(admin[0]);

        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        session.subscribe("/topic/entrance/" + admin[1] + "/chat", handler(received));
        Thread.sleep(300); // let the SUBSCRIBE reach the broker

        postJson("/api/chat/messages", admin[0], "{\"text\": \"Hello neighbours\"}");

        Map<String, Object> event = received.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.get("type")).isEqualTo("MESSAGE_CREATED");
        assertThat(((Map<?, ?>) event.get("payload")).get("text")).isEqualTo("Hello neighbours");
        session.disconnect();
    }

    @Test
    void cannotSubscribeToAnotherEntrance() throws Exception {
        String[] admin = registerAdmin();
        String[] other = registerAdmin();
        StompSession session = connect(admin[0]);

        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        session.subscribe("/topic/entrance/" + other[1] + "/chat", handler(received));
        Thread.sleep(300);
        postJson("/api/chat/messages", other[0], "{\"text\": \"secret\"}");

        assertThat(received.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void connectWithoutTokenIsRejected() {
        assertThatThrownBy(() -> connect("not-a-token")).isNotNull();
    }

    // ---------- helpers ----------

    private StompSession connect(String token) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter(jsonMapper));
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        CompletableFuture<StompSession> future = client.connectAsync("ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() { });
        return future.get(5, TimeUnit.SECONDS);
    }

    private static StompFrameHandler handler(BlockingQueue<Map<String, Object>> sink) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                sink.add((Map<String, Object>) payload);
            }
        };
    }

    /** @return [accessToken, entranceId] */
    private String[] registerAdmin() throws Exception {
        String body = postJson("/api/auth/register", null, """
                {"email": "%s@example.com", "password": "password123", "firstName": "Ws", "lastName": "Tester",
                 "floor": "1", "apartment": "1", "newEntrance": {"name": "E", "address": "A"}}"""
                .formatted(UUID.randomUUID()));
        return new String[] {JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.user.entrance.id")};
    }

    private String postJson(String path, String token, String json) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isBetween(200, 299);
        return res.body();
    }
}
