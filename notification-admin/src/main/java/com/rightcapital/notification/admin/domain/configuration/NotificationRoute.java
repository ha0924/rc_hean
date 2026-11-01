package com.rightcapital.notification.admin.domain.configuration;

import java.time.LocalDateTime;

public record NotificationRoute(
        Long id,
        Long eventDefinitionId,
        Long externalEndpointId,
        String requestMapping,
        ConfigurationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public NotificationRoute {
        ConfigurationFields.requiredId(eventDefinitionId, "eventDefinitionId");
        ConfigurationFields.requiredId(externalEndpointId, "externalEndpointId");
        requestMapping = ConfigurationFields.requiredText(requestMapping, "requestMapping");
        status = ConfigurationFields.required(status, "status");
    }
}
