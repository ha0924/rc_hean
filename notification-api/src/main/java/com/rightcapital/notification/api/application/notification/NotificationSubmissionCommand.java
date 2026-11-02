package com.rightcapital.notification.api.application.notification;

import com.fasterxml.jackson.databind.JsonNode;

public record NotificationSubmissionCommand(
        String accessKey,
        String secret,
        String eventCode,
        String suppliedIdempotencyKey,
        String requestId,
        JsonNode payload,
        String traceId
) {
}
