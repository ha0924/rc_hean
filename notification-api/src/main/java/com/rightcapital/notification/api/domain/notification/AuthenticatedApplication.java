package com.rightcapital.notification.api.domain.notification;

public record AuthenticatedApplication(
        long id,
        String code,
        String secretHash
) {
}
