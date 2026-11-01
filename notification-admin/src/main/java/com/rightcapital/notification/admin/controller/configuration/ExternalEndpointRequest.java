package com.rightcapital.notification.admin.controller.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.rightcapital.notification.admin.domain.configuration.ConfigurationStatus;
import com.rightcapital.notification.admin.domain.configuration.EndpointAuthType;
import com.rightcapital.notification.admin.domain.configuration.EndpointHttpMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ExternalEndpointRequest(
        @NotBlank @Size(max = 128) String code,
        @NotBlank @Size(max = 128) String name,
        @NotNull EndpointHttpMethod httpMethod,
        @NotBlank @Size(max = 2048) String urlTemplate,
        @NotNull EndpointAuthType authType,
        String authConfig,
        JsonNode defaultHeaders,
        @Positive int timeoutMs,
        @Positive int maxConcurrency,
        @NotNull JsonNode successRule,
        @NotNull JsonNode retryableRule,
        @NotNull ConfigurationStatus status
) {
}
