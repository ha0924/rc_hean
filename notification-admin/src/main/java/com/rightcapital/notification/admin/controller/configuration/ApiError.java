package com.rightcapital.notification.admin.controller.configuration;

import java.time.Instant;

record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
