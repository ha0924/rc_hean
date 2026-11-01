package com.rightcapital.notification.common.message;

public record DeliveryTaskMessage(
        String notificationId,
        String taskId,
        String traceId
) {
}
