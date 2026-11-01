package com.rightcapital.notification.admin.application.configuration;

import com.rightcapital.notification.admin.domain.configuration.ConfigurationStatus;

public record BusinessApplicationCommand(
        String code,
        String name,
        String accessKey,
        String secret,
        ConfigurationStatus status
) {
}
