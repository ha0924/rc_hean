package com.rightcapital.notification.admin.controller.configuration;

import com.rightcapital.notification.admin.domain.configuration.ConfigurationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BusinessApplicationRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 128) String accessKey,
        @NotBlank @Size(max = 72) String secret,
        @NotNull ConfigurationStatus status
) {
}
