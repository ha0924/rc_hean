package com.rightcapital.notification.admin.controller.configuration;

import com.rightcapital.notification.admin.domain.configuration.BusinessApplication;
import com.rightcapital.notification.admin.domain.configuration.ConfigurationStatus;

import java.time.LocalDateTime;

public record BusinessApplicationResponse(
        long id,
        String code,
        String name,
        String accessKey,
        ConfigurationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static BusinessApplicationResponse from(BusinessApplication application) {
        return new BusinessApplicationResponse(
                application.id(),
                application.code(),
                application.name(),
                application.accessKey(),
                application.status(),
                application.createdAt(),
                application.updatedAt()
        );
    }
}
