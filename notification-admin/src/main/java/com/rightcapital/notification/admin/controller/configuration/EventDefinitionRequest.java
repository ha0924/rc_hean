package com.rightcapital.notification.admin.controller.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.rightcapital.notification.admin.domain.configuration.ConfigurationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EventDefinitionRequest(
        @NotNull @Positive Long applicationId,
        @NotBlank @Size(max = 128) String eventCode,
        @NotBlank @Size(max = 128) String name,
        @NotNull JsonNode payloadSchema,
        @NotNull ConfigurationStatus status
) {
}
