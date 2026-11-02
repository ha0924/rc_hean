package com.rightcapital.notification.api.controller.notification;

import com.rightcapital.notification.api.application.notification.NotificationAcceptance;
import com.rightcapital.notification.common.enums.NotificationStatus;

public record NotificationAcceptanceResponse(
        String notificationId,
        NotificationStatus status,
        boolean idempotentReplay
) {
    static NotificationAcceptanceResponse from(NotificationAcceptance acceptance) {
        return new NotificationAcceptanceResponse(
                acceptance.notificationId(), acceptance.status(), acceptance.idempotentReplay()
        );
    }
}
