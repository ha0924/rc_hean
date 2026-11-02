package com.rightcapital.notification.api.domain.notification;

import com.rightcapital.notification.common.enums.NotificationStatus;

public record NotificationRecord(
        String id,
        long applicationId,
        long eventDefinitionId,
        String idempotencyKey,
        String payloadHash,
        NotificationStatus status
) {
}
