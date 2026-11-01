package com.rightcapital.notification.admin.controller.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.rightcapital.notification.admin.domain.configuration.ConfigurationStatus;
import com.rightcapital.notification.admin.domain.configuration.NotificationRoute;

import java.time.LocalDateTime;

public record NotificationRouteResponse(
        long id,
        long eventDefinitionId,
        long externalEndpointId,
        JsonNode requestMapping,
        ConfigurationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static NotificationRouteResponse from(NotificationRoute route, JsonNode requestMapping) {
        return new NotificationRouteResponse(
                route.id(),
                route.eventDefinitionId(),
                route.externalEndpointId(),
                requestMapping,
                route.status(),
                route.createdAt(),
                route.updatedAt()
        );
    }
}
