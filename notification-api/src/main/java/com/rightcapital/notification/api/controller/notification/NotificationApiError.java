package com.rightcapital.notification.api.controller.notification;

import java.time.Instant;

record NotificationApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
