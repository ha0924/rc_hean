package com.rightcapital.notification.worker.infrastructure.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightcapital.notification.worker.domain.DeliveryTaskDetail;
import com.rightcapital.notification.worker.domain.HttpDeliveryResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

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
    void sendsMappedRequestToExternalApi() {
        RestClientExternalApiClient client = new RestClientExternalApiClient(requestFactory());

        HttpDeliveryResult result = client.deliver(task());

        assertThat(result.httpStatus()).isEqualTo(202);
        assertThat(requestBody.get()).isEqualTo("{\"amount\":100}");
        assertThat(requestHeaders.get().getFirst("Idempotency-Key")).isEqualTo("task-1");
        assertThat(requestHeaders.get().getFirst("X-Notification-Id")).isEqualTo("notification-1");
        assertThat(requestHeaders.get().getFirst("X-Trace-Id")).isEqualTo("trace-1");
        assertThat(requestHeaders.get().getFirst("X-Endpoint-Name")).isEqualTo("crm");
        assertThat(requestHeaders.get().getFirst("Authorization")).isEqualTo("Bearer crm-token");
    }

    private OutboundRequestFactory requestFactory() {
        return ignored -> {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Endpoint-Name", "crm");
            headers.set("X-Notification-Id", "notification-1");
            headers.set("Idempotency-Key", "task-1");
            headers.set("X-Trace-Id", "trace-1");
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer crm-token");
            return new OutboundHttpRequest(
                    HttpMethod.POST,
                    java.net.URI.create("http://localhost:" + server.getAddress().getPort() + "/callback"),
                    headers,
                    MediaType.APPLICATION_JSON,
                    "{\"amount\":100}"
            );
        };
    }

    private DeliveryTaskDetail task() {
        return new DeliveryTaskDetail(
                "task-1",
                "notification-1",
                3L,
                "{\"amount\":100}",
                "trace-1",
                "POST",
                "https://crm.example.com/callback",
                "BEARER",
                "{\"secretRef\":\"env:CRM_TOKEN\"}",
                "{\"X-Endpoint-Name\":\"crm\"}",
                5000,
                8,
                "{}",
                "{}",
                "{}"
        );
    }
}
