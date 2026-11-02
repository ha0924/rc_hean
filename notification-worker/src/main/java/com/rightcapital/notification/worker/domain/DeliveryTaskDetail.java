package com.rightcapital.notification.worker.domain;

/** Immutable task and endpoint data required for one outbound HTTP delivery. */
public record DeliveryTaskDetail(
        String taskId,
        String notificationId,
        long endpointId,
        String payload,
        String traceId,
        String httpMethod,
        String urlTemplate,
        String authType,
        String authConfig,
        String defaultHeaders,
        int timeoutMs
) {
}
