package com.rightcapital.notification.admin.application.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightcapital.notification.admin.domain.configuration.ExternalEndpoint;
import com.rightcapital.notification.admin.domain.configuration.NotificationRoute;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
class DeliveryConfigurationValidator {

    private static final Set<String> RESERVED_HEADERS = Set.of(
            "authorization", "idempotency-key", "x-notification-id", "x-trace-id", "content-type"
    );
    private static final Pattern URL_VARIABLE = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_-]*)}");

    private final ObjectMapper objectMapper;

    DeliveryConfigurationValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void validate(ExternalEndpoint endpoint) {
        validateHeaders(optionalObject(endpoint.defaultHeaders(), "defaultHeaders"), "defaultHeaders");
        validateStatusRule(endpoint.successRule(), "successRule");
        validateStatusRule(endpoint.retryableRule(), "retryableRule");
        switch (endpoint.authType()) {
            case NONE -> {
            }
            case BEARER -> requiredText(requiredObject(endpoint.authConfig(), "authConfig"), "secretRef", "authConfig");
            case API_KEY -> {
                JsonNode authConfig = requiredObject(endpoint.authConfig(), "authConfig");
                requiredText(authConfig, "secretRef", "authConfig");
                requiredText(authConfig, "name", "authConfig");
                String location = authConfig.path("in").asText("HEADER");
                if (!"HEADER".equals(location) && !"QUERY".equals(location)) {
                    throw new IllegalArgumentException("authConfig.in must be HEADER or QUERY");
                }
            }
            case BASIC -> {
                JsonNode authConfig = requiredObject(endpoint.authConfig(), "authConfig");
                requiredText(authConfig, "username", "authConfig");
                requiredText(authConfig, "passwordSecretRef", "authConfig");
            }
        }
    }

    void validate(NotificationRoute route, ExternalEndpoint endpoint) {
        JsonNode mapping = requiredObject(route.requestMapping(), "requestMapping");
        JsonNode pathVariables = mapping.path("pathVariables");
        validateExpressionObject(pathVariables, "requestMapping.pathVariables");
        validateUrlVariables(endpoint.urlTemplate(), pathVariables);
        validateExpressionObject(mapping.path("query"), "requestMapping.query");
        validateHeaders(mapping.path("headers"), "requestMapping.headers");
        JsonNode body = mapping.get("body");
        if (body != null && !body.isNull()) {
            if (!body.isObject()) {
                throw new IllegalArgumentException("requestMapping.body must be a JSON object");
            }
            String type = requiredText(body, "type", "requestMapping.body");
            if (!Set.of("JSON", "FORM_URLENCODED", "XML").contains(type)) {
                throw new IllegalArgumentException("requestMapping.body.type is unsupported");
            }
            switch (type) {
                case "JSON" -> validateJsonTemplate(
                        required(body, "template", "requestMapping.body"), "requestMapping.body.template"
                );
                case "FORM_URLENCODED" -> validateExpressionObject(
                        required(body, "fields", "requestMapping.body"), "requestMapping.body.fields"
                );
                case "XML" -> {
                    String root = requiredText(body, "root", "requestMapping.body");
                    if (!root.matches("[A-Za-z_][A-Za-z0-9_.-]*")) {
                        throw new IllegalArgumentException("requestMapping.body.root contains an invalid XML name");
                    }
                    validateExpressionObject(
                            required(body, "fields", "requestMapping.body"), "requestMapping.body.fields"
                    );
                }
                default -> throw new IllegalStateException("Unsupported body type");
            }
        }
    }

    private void validateExpressionObject(JsonNode node, String field) {
        if (node.isMissingNode() || node.isNull()) {
            return;
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException(field + " must be a JSON object");
        }
        node.properties().forEach(entry -> validateExpression(entry.getValue(), field + "." + entry.getKey()));
    }

    private void validateUrlVariables(String urlTemplate, JsonNode pathVariables) {
        Matcher matcher = URL_VARIABLE.matcher(urlTemplate);
        while (matcher.find()) {
            if (!pathVariables.has(matcher.group(1))) {
                throw new IllegalArgumentException(
                        "requestMapping.pathVariables must provide URL variable " + matcher.group(1)
                );
            }
        }
    }

    private void validateJsonTemplate(JsonNode node, String field) {
        if (node.isObject()) {
            if (node.has("from") || node.has("value")) {
                validateExpression(node, field);
                return;
            }
            node.properties().forEach(entry -> validateJsonTemplate(entry.getValue(), field + "." + entry.getKey()));
            return;
        }
        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                validateJsonTemplate(node.get(index), field + "[" + index + "]");
            }
        }
    }

    private void validateHeaders(JsonNode headers, String field) {
        if (headers == null || headers.isMissingNode() || headers.isNull()) {
            return;
        }
        if (!headers.isObject()) {
            throw new IllegalArgumentException(field + " must be a JSON object");
        }
        headers.properties().forEach(entry -> {
            if (RESERVED_HEADERS.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(field + " must not set platform-reserved header " + entry.getKey());
            }
            if (field.equals("defaultHeaders")) {
                if (!entry.getValue().isValueNode() || entry.getValue().isNull()) {
                    throw new IllegalArgumentException(field + "." + entry.getKey() + " must be a scalar value");
                }
            } else {
                validateExpression(entry.getValue(), field + "." + entry.getKey());
            }
        });
    }

    private void validateExpression(JsonNode expression, String field) {
        if (!expression.isObject()) {
            throw new IllegalArgumentException(field + " must be a value expression object");
        }
        boolean hasFrom = expression.has("from");
        boolean hasValue = expression.has("value");
        if (hasFrom == hasValue) {
            throw new IllegalArgumentException(field + " must contain exactly one of from or value");
        }
        if (hasFrom) {
            String pointer = requiredText(expression, "from", field);
            if (!pointer.isEmpty() && !pointer.startsWith("/")) {
                throw new IllegalArgumentException(field + ".from must be an RFC 6901 JSON Pointer");
            }
        }
        if (expression.has("required") && !expression.get("required").isBoolean()) {
            throw new IllegalArgumentException(field + ".required must be a boolean");
        }
    }

    private void validateStatusRule(String json, String field) {
        JsonNode rule = requiredObject(json, field);
        validateStatusValues(rule.get("statuses"), field + ".statuses");
        validateRanges(rule.get("ranges"), field + ".ranges");
    }

    private void validateStatusValues(JsonNode statuses, String field) {
        if (statuses == null || statuses.isNull()) {
            return;
        }
        if (!statuses.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        for (JsonNode status : statuses) {
            if (!status.canConvertToInt() || status.intValue() < 100 || status.intValue() > 599) {
                throw new IllegalArgumentException(field + " contains an invalid HTTP status");
            }
        }
    }

    private void validateRanges(JsonNode ranges, String field) {
        if (ranges == null || ranges.isNull()) {
            return;
        }
        if (!ranges.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        for (JsonNode range : ranges) {
            if (!range.isTextual() || !range.textValue().matches("[2-5]xx")) {
                throw new IllegalArgumentException(field + " contains an invalid HTTP range");
            }
        }
    }

    private JsonNode optionalObject(String json, String field) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return requiredObject(json, field);
    }

    private JsonNode requiredObject(String json, String field) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException(field + " must be a JSON object");
            }
            return node;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(field + " must be valid JSON", exception);
        }
    }

    private String requiredText(JsonNode object, String field, String context) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(context + " requires " + field);
        }
        return value.textValue();
    }

    private JsonNode required(JsonNode object, String field, String context) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(context + " requires " + field);
        }
        return value;
    }
}
