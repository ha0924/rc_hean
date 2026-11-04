package com.rightcapital.notification.admin.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "notification.admin")
public class AdminSecurityProperties {

    private String apiKeyHash = "";
    private Set<String> allowedEndpointHosts = new LinkedHashSet<>();

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public void setApiKeyHash(String apiKeyHash) {
        this.apiKeyHash = apiKeyHash == null ? "" : apiKeyHash;
    }

    public Set<String> getAllowedEndpointHosts() {
        return Set.copyOf(allowedEndpointHosts);
    }

    public void setAllowedEndpointHosts(Set<String> allowedEndpointHosts) {
        this.allowedEndpointHosts = allowedEndpointHosts == null ? new LinkedHashSet<>() : new LinkedHashSet<>(allowedEndpointHosts);
    }
}
