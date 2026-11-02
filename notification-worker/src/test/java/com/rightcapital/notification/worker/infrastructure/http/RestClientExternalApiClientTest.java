package com.rightcapital.notification.worker.infrastructure.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightcapital.notification.worker.domain.DeliveryConfigurationException;
import com.rightcapital.notification.worker.domain.DeliveryTaskDetail;
import com.rightcapital.notification.worker.domain.HttpDeliveryResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientExternalApiClientTest {

    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<com.sun.net.httpserver.Headers> requestHeaders = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/callback", exchange -> {
            requestHeaders.set(exchange.getRequestHeaders());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "accepted".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(202, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void forwardsPayloadAndPlatformIdempotencyHeaders() {
        RestClientExternalApiClient client = new RestClientExternalApiClient(new ObjectMapper());

        HttpDeliveryResult result = client.deliver(task("NONE", null));

        assertThat(result.httpStatus()).isEqualTo(202);
        assertThat(requestBody.get()).isEqualTo("{\"amount\":100}");
        assertThat(requestHeaders.get().getFirst("Idempotency-Key")).isEqualTo("task-1");
        assertThat(requestHeaders.get().getFirst("X-Notification-Id")).isEqualTo("notification-1");
        assertThat(requestHeaders.get().getFirst("X-Trace-Id")).isEqualTo("trace-1");
        assertThat(requestHeaders.get().getFirst("X-Endpoint-Name")).isEqualTo("crm");
    }

    @Test
    void rejectsEndpointAuthenticationUntilSecretResolutionIsImplemented() {
        RestClientExternalApiClient client = new RestClientExternalApiClient(new ObjectMapper());

        assertThatThrownBy(() -> client.deliver(task("BEARER", "secret-reference")))
                .isInstanceOf(DeliveryConfigurationException.class)
                .hasMessageContaining("secret provider");
    }

    private DeliveryTaskDetail task(String authType, String authConfig) {
        return new DeliveryTaskDetail(
                "task-1",
                "notification-1",
                3L,
                "{\"amount\":100}",
                "trace-1",
                "POST",
                "http://localhost:" + server.getAddress().getPort() + "/callback",
                authType,
                authConfig,
                "{\"X-Endpoint-Name\":\"crm\"}",
                5000
        );
    }
}
