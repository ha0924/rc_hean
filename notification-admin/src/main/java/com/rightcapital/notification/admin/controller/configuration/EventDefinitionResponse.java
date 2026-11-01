package com.rightcapital.notification.admin.controller.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.rightcapital.notification.admin.domain.configuration.ConfigurationStatus;
import com.rightcapital.notification.admin.domain.configuration.EventDefinition;

import java.time.LocalDateTime;

public record EventDefinitionResponse(
        long id,
        long applicationId,
        String eventCode,
        String name,
        JsonNode payloadSchema,
        ConfigurationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static EventDefinitionResponse from(EventDefinition eventDefinition, JsonNode payloadSchema) {
        return new EventDefinitionResponse(
                eventDefinition.id(),
                eventDefinition.applicationId(),
                eventDefinition.eventCode(),
                eventDefinition.name(),
                payloadSchema,
                eventDefinition.status(),
                eventDefinition.createdAt(),
                eventDefinition.updatedAt()
        );
    }
}
