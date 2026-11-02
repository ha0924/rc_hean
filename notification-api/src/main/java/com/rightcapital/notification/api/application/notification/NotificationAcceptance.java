package com.rightcapital.notification.api.application.notification;

import com.rightcapital.notification.common.enums.NotificationStatus;

public record NotificationAcceptance(
        String notificationId,
        NotificationStatus status,
        boolean idempotentReplay
) {
}
