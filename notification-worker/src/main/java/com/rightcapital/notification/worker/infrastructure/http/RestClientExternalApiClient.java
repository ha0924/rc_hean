package com.rightcapital.notification.worker.infrastructure.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightcapital.notification.worker.domain.DeliveryConfigurationException;
import com.rightcapital.notification.worker.domain.DeliveryTaskDetail;
import com.rightcapital.notification.worker.domain.ExternalApiClient;
import com.rightcapital.notification.worker.domain.HttpDeliveryResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * First-version HTTP adapter. It intentionally supports payload pass-through and NONE authentication only;
 * a secret-provider-backed authentication resolver and mapping DSL can be added without changing task state rules.
 */
@Component
class RestClientExternalApiClient implements ExternalApiClient {

    private static final int MAX_RESPONSE_EXCERPT_LENGTH = 2048;

    private final ObjectMapper objectMapper;

    RestClientExternalApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public HttpDeliveryResult deliver(DeliveryTaskDetail task) {
        long startedAt = System.nanoTime();
        try {
            URI uri = endpointUri(task.urlTemplate());
            HttpMethod method = endpointMethod(task.httpMethod());
            HttpHeaders headers = headers(task);
            Duration timeout = Duration.ofMillis(task.timeoutMs());
            return restClient(timeout)
                    .method(method)
                    .uri(uri)
                    .headers(requestHeaders -> requestHeaders.putAll(headers))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(task.payload())
                    .exchange((request, response) -> HttpDeliveryResult.response(
                            response.getStatusCode().value(),
                            elapsedMillis(startedAt),
                            truncate(StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8))
                    ));
        } catch (DeliveryConfigurationException exception) {
            throw exception;
        } catch (RestClientException exception) {
            return HttpDeliveryResult.transportFailure(
                    elapsedMillis(startedAt), "NETWORK", safeMessage(exception)
            );
        }
    }

    private RestClient restClient(Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private URI endpointUri(String urlTemplate) {
        try {
            URI uri = URI.create(urlTemplate);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new DeliveryConfigurationException("Endpoint URL must use HTTP or HTTPS");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new DeliveryConfigurationException("Endpoint URL is invalid", exception);
        }
    }

    private HttpMethod endpointMethod(String method) {
        try {
            return HttpMethod.valueOf(method);
        } catch (IllegalArgumentException exception) {
            throw new DeliveryConfigurationException("Endpoint HTTP method is invalid", exception);
        }
    }

    private HttpHeaders headers(DeliveryTaskDetail task) {
        if (!"NONE".equals(task.authType())) {
            throw new DeliveryConfigurationException(
                    "Endpoint authentication requires a secret provider and is not enabled in the initial worker"
            );
        }
        HttpHeaders headers = new HttpHeaders();
        if (task.defaultHeaders() != null) {
            JsonNode defaultHeaders = readJson(task.defaultHeaders(), "defaultHeaders");
            if (!defaultHeaders.isObject()) {
                throw new DeliveryConfigurationException("defaultHeaders must be a JSON object");
            }
            defaultHeaders.properties().forEach(entry -> {
                if (!entry.getValue().isTextual()) {
                    throw new DeliveryConfigurationException("defaultHeaders values must be strings");
                }
                headers.set(entry.getKey(), entry.getValue().textValue());
            });
        }
        headers.set("X-Notification-Id", task.notificationId());
        headers.set("Idempotency-Key", task.taskId());
        headers.set("X-Trace-Id", task.traceId());
        return headers;
    }

    private JsonNode readJson(String value, String fieldName) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new DeliveryConfigurationException(fieldName + " is invalid JSON", exception);
        }
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String truncate(String responseBody) {
        if (responseBody.length() <= MAX_RESPONSE_EXCERPT_LENGTH) {
            return responseBody;
        }
        return responseBody.substring(0, MAX_RESPONSE_EXCERPT_LENGTH);
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
