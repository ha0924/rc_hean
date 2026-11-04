package com.rightcapital.notification.worker.infrastructure.http;

import com.rightcapital.notification.worker.domain.DeliveryConfigurationException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

@Component
class EndpointUrlPolicy {

    private final DeliverySecurityProperties properties;

    EndpointUrlPolicy(DeliverySecurityProperties properties) {
        this.properties = properties;
    }

    void validate(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new DeliveryConfigurationException("Endpoint URL must use HTTPS");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank() || localOrIpLiteral(host)) {
            throw new DeliveryConfigurationException("Endpoint URL host is not permitted");
        }
        Set<String> allowedHosts = properties.getAllowedEndpointHosts().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (allowedHosts.isEmpty() || allowedHosts.stream().noneMatch(allowed -> matches(host, allowed))) {
            throw new DeliveryConfigurationException("Endpoint URL host is not in notification.delivery.allowed-endpoint-hosts");
        }
    }

    private boolean localOrIpLiteral(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || normalized.endsWith(".localhost")
                || normalized.matches("[0-9.]+")
                || normalized.contains(":");
    }

    private boolean matches(String host, String allowedHost) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return normalizedHost.equals(allowedHost) || normalizedHost.endsWith("." + allowedHost);
    }
}
