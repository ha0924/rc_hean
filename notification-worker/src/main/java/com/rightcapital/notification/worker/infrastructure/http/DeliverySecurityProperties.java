package com.rightcapital.notification.worker.infrastructure.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "notification.delivery")
public class DeliverySecurityProperties {

    private Set<String> allowedEndpointHosts = new LinkedHashSet<>();

    public Set<String> getAllowedEndpointHosts() {
        return Set.copyOf(allowedEndpointHosts);
    }

    public void setAllowedEndpointHosts(Set<String> allowedEndpointHosts) {
        this.allowedEndpointHosts = allowedEndpointHosts == null ? new LinkedHashSet<>() : new LinkedHashSet<>(allowedEndpointHosts);
    }
}
