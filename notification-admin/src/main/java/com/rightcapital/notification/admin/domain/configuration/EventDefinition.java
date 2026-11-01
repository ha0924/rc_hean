package com.rightcapital.notification.admin.domain.configuration;

import java.time.LocalDateTime;

public record EventDefinition(
        Long id,
        Long applicationId,
        String eventCode,
        String name,
        String payloadSchema,
        ConfigurationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public EventDefinition {
        ConfigurationFields.requiredId(applicationId, "applicationId");
        eventCode = ConfigurationFields.requiredText(eventCode, "eventCode");
        name = ConfigurationFields.requiredText(name, "name");
        payloadSchema = ConfigurationFields.requiredText(payloadSchema, "payloadSchema");
        status = ConfigurationFields.required(status, "status");
    }
}
