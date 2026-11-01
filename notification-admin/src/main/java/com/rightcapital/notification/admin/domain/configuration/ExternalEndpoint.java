package com.rightcapital.notification.admin.domain.configuration;

import java.time.LocalDateTime;

public record ExternalEndpoint(
        Long id,
        String code,
        String name,
        EndpointHttpMethod httpMethod,
        String urlTemplate,
        EndpointAuthType authType,
        String authConfig,
        String defaultHeaders,
        int timeoutMs,
        int maxConcurrency,
        String successRule,
        String retryableRule,
        ConfigurationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public ExternalEndpoint {
        code = ConfigurationFields.requiredText(code, "code");
        name = ConfigurationFields.requiredText(name, "name");
        httpMethod = ConfigurationFields.required(httpMethod, "httpMethod");
        urlTemplate = ConfigurationFields.requiredText(urlTemplate, "urlTemplate");
        if (!urlTemplate.startsWith("http://") && !urlTemplate.startsWith("https://")) {
            throw new IllegalArgumentException("urlTemplate must use HTTP or HTTPS");
        }
        authType = ConfigurationFields.required(authType, "authType");
        if (authType != EndpointAuthType.NONE) {
            authConfig = ConfigurationFields.requiredText(authConfig, "authConfig");
        }
        defaultHeaders = ConfigurationFields.optionalJson(defaultHeaders);
        timeoutMs = ConfigurationFields.positive(timeoutMs, "timeoutMs");
        maxConcurrency = ConfigurationFields.positive(maxConcurrency, "maxConcurrency");
        successRule = ConfigurationFields.requiredText(successRule, "successRule");
        retryableRule = ConfigurationFields.requiredText(retryableRule, "retryableRule");
        status = ConfigurationFields.required(status, "status");
    }
}
