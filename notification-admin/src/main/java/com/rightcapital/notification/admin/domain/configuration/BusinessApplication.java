package com.rightcapital.notification.admin.domain.configuration;

import java.time.LocalDateTime;

public record BusinessApplication(
        Long id,
        String code,
        String name,
        String accessKey,
        String secretHash,
        ConfigurationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public BusinessApplication {
        code = ConfigurationFields.requiredText(code, "code");
        name = ConfigurationFields.requiredText(name, "name");
        accessKey = ConfigurationFields.requiredText(accessKey, "accessKey");
        secretHash = ConfigurationFields.requiredText(secretHash, "secretHash");
        status = ConfigurationFields.required(status, "status");
    }
}
