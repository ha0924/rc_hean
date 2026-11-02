package com.rightcapital.notification.api.domain.notification;

public record NotificationTransactionCommand(
        String notificationId,
        String taskId,
        long applicationId,
        long eventDefinitionId,
        long routeId,
        long endpointId,
        String idempotencyKey,
        String payload,
        String payloadHash,
        String traceId
) {
}
