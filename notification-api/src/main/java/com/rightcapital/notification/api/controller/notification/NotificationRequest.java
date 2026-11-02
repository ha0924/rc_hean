package com.rightcapital.notification.api.controller.notification;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NotificationRequest(
        @NotBlank @Size(max = 128) String eventCode,
        @Size(max = 128) String requestId,
        @NotNull JsonNode payload
) {
}
