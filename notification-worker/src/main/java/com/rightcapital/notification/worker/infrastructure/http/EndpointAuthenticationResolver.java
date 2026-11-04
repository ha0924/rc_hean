package com.rightcapital.notification.worker.infrastructure.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightcapital.notification.worker.domain.DeliveryConfigurationException;
import com.rightcapital.notification.worker.domain.DeliveryTaskDetail;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
class EndpointAuthenticationResolver {

    private final ObjectMapper objectMapper;
    private final SecretProvider secretProvider;

    EndpointAuthenticationResolver(ObjectMapper objectMapper, SecretProvider secretProvider) {
        this.objectMapper = objectMapper;
        this.secretProvider = secretProvider;
    }

    EndpointAuthentication resolve(DeliveryTaskDetail task) {
        HttpHeaders headers = new HttpHeaders();
        Map<String, String> query = new LinkedHashMap<>();
        return switch (task.authType()) {
            case "NONE" -> new EndpointAuthentication(headers, query);
            case "BEARER" -> {
                JsonNode config = config(task.authConfig());
                headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + secretProvider.resolve(requiredText(config, "secretRef")));
                yield new EndpointAuthentication(headers, query);
            }
            case "API_KEY" -> {
                JsonNode config = config(task.authConfig());
                String secret = secretProvider.resolve(requiredText(config, "secretRef"));
                String name = requiredText(config, "name");
                String location = config.path("in").asText("HEADER");
                if ("HEADER".equals(location)) {
                    headers.set(name, secret);
                } else if ("QUERY".equals(location)) {
                    query.put(name, secret);
                } else {
                    throw new DeliveryConfigurationException("API key location must be HEADER or QUERY");
                }
                yield new EndpointAuthentication(headers, query);
            }
            case "BASIC" -> {
                JsonNode config = config(task.authConfig());
                String username = requiredText(config, "username");
                String password = secretProvider.resolve(requiredText(config, "passwordSecretRef"));
                String token = Base64.getEncoder().encodeToString((username + ":" + password)
                        .getBytes(StandardCharsets.UTF_8));
                headers.set(HttpHeaders.AUTHORIZATION, "Basic " + token);
                yield new EndpointAuthentication(headers, query);
            }
            default -> throw new DeliveryConfigurationException("Endpoint authentication type is unsupported");
        };
    }

    private JsonNode config(String authConfig) {
        if (authConfig == null || authConfig.isBlank()) {
            throw new DeliveryConfigurationException("Endpoint authentication configuration is required");
        }
        try {
            JsonNode config = objectMapper.readTree(authConfig);
            if (!config.isObject()) {
                throw new DeliveryConfigurationException("Endpoint authentication configuration must be a JSON object");
            }
            return config;
        } catch (JsonProcessingException exception) {
            throw new DeliveryConfigurationException("Endpoint authentication configuration is invalid JSON", exception);
        }
    }

    private String requiredText(JsonNode config, String field) {
        JsonNode value = config.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new DeliveryConfigurationException("Endpoint authentication configuration requires " + field);
        }
        return value.textValue();
    }

    record EndpointAuthentication(HttpHeaders headers, Map<String, String> queryParameters) {
    }
}
