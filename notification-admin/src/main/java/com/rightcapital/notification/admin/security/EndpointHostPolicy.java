package com.rightcapital.notification.admin.security;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EndpointHostPolicy {

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[A-Za-z][A-Za-z0-9_-]*}");

    private final AdminSecurityProperties properties;

    public EndpointHostPolicy(AdminSecurityProperties properties) {
        this.properties = properties;
    }

    public void validate(String urlTemplate) {
        URI uri;
        try {
            uri = URI.create(normalizeTemplate(urlTemplate));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("urlTemplate is invalid", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("urlTemplate must use HTTPS");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank() || isLocalOrIpLiteral(host)) {
            throw new IllegalArgumentException("urlTemplate host is not permitted");
        }
        Set<String> allowedHosts = properties.getAllowedEndpointHosts().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (allowedHosts.isEmpty() || allowedHosts.stream().noneMatch(allowed -> hostMatches(host, allowed))) {
            throw new IllegalArgumentException("urlTemplate host is not in notification.admin.allowed-endpoint-hosts");
        }
    }

    private String normalizeTemplate(String template) {
        int schemeEnd = template.indexOf("://");
        if (schemeEnd < 1) {
            throw new IllegalArgumentException("urlTemplate is invalid");
        }
        int authorityStart = schemeEnd + 3;
        int pathStart = firstPathCharacter(template, authorityStart);
        String authority = template.substring(authorityStart, pathStart);
        if (authority.indexOf('{') >= 0 || authority.indexOf('}') >= 0) {
            throw new IllegalArgumentException("urlTemplate must not template the host");
        }
        String suffix = template.substring(pathStart);
        Matcher matcher = PATH_VARIABLE.matcher(suffix);
        String normalized = matcher.replaceAll("placeholder");
        if (normalized.indexOf('{') >= 0 || normalized.indexOf('}') >= 0) {
            throw new IllegalArgumentException("urlTemplate contains an invalid path variable");
        }
        return template.substring(0, pathStart) + normalized;
    }

    private int firstPathCharacter(String value, int start) {
        for (int index = start; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '/' || current == '?' || current == '#') {
                return index;
            }
        }
        return value.length();
    }

    private boolean isLocalOrIpLiteral(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || normalized.endsWith(".localhost")
                || normalized.matches("[0-9.]+")
                || normalized.contains(":");
    }

    private boolean hostMatches(String host, String allowedHost) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return normalizedHost.equals(allowedHost) || normalizedHost.endsWith("." + allowedHost);
    }
}
