package com.rightcapital.notification.admin.controller.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.rightcapital.notification.admin.domain.configuration.ConfigurationStatus;
import com.rightcapital.notification.admin.domain.configuration.EndpointAuthType;
import com.rightcapital.notification.admin.domain.configuration.EndpointHttpMethod;
import com.rightcapital.notification.admin.domain.configuration.ExternalEndpoint;

import java.time.LocalDateTime;

public record ExternalEndpointResponse(
        long id,
        String code,
        String name,
        EndpointHttpMethod httpMethod,
        String urlTemplate,
        EndpointAuthType authType,
        boolean authConfigured,
        JsonNode defaultHeaders,
        int timeoutMs,
        int maxConcurrency,
        JsonNode successRule,
        JsonNode retryableRule,
        ConfigurationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static ExternalEndpointResponse from(
            ExternalEndpoint endpoint,
            JsonNode defaultHeaders,
            JsonNode successRule,
            JsonNode retryableRule
    ) {
        return new ExternalEndpointResponse(
                endpoint.id(),
                endpoint.code(),
                endpoint.name(),
                endpoint.httpMethod(),
                endpoint.urlTemplate(),
                endpoint.authType(),
                endpoint.authConfig() != null && !endpoint.authConfig().isBlank(),
                defaultHeaders,
                endpoint.timeoutMs(),
                endpoint.maxConcurrency(),
                successRule,
                retryableRule,
                endpoint.status(),
                endpoint.createdAt(),
                endpoint.updatedAt()
        );
    }
}
