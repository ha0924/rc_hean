package com.rightcapital.notification.admin.controller.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.rightcapital.notification.admin.domain.configuration.ConfigurationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record NotificationRouteRequest(
        @NotNull @Positive Long eventDefinitionId,
        @NotNull @Positive Long externalEndpointId,
        @NotNull JsonNode requestMapping,
        @NotNull ConfigurationStatus status
) {
}
