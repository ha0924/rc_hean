package com.rightcapital.notification.worker.infrastructure.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rightcapital.notification.worker.domain.DeliveryConfigurationException;
import com.rightcapital.notification.worker.domain.DeliveryTaskDetail;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
class RequestMappingOutboundRequestFactory implements OutboundRequestFactory {

    private static final Set<String> RESERVED_HEADERS = Set.of(
            "authorization", "idempotency-key", "x-notification-id", "x-trace-id", "content-type"
    );

    private final ObjectMapper objectMapper;
    private final EndpointAuthenticationResolver authenticationResolver;
    private final EndpointUrlPolicy endpointUrlPolicy;

    RequestMappingOutboundRequestFactory(
            ObjectMapper objectMapper,
            EndpointAuthenticationResolver authenticationResolver,
            EndpointUrlPolicy endpointUrlPolicy
    ) {
        this.objectMapper = objectMapper;
        this.authenticationResolver = authenticationResolver;
        this.endpointUrlPolicy = endpointUrlPolicy;
    }

    @Override
    public OutboundHttpRequest create(DeliveryTaskDetail task) {
        JsonNode payload = readObject(task.payload(), "notification payload");
        JsonNode mapping = readMapping(task.requestMapping());
        Map<String, String> pathVariables = resolveFields(mapping.path("pathVariables"), payload, "pathVariables");
        Map<String, String> query = resolveFields(mapping.path("query"), payload, "query");
        HttpHeaders headers = endpointHeaders(task.defaultHeaders());
        applyRouteHeaders(headers, mapping.path("headers"), payload);

        EndpointAuthenticationResolver.EndpointAuthentication authentication = authenticationResolver.resolve(task);
        authentication.headers().forEach((name, values) -> headers.put(name, values));
        query.putAll(authentication.queryParameters());

        Body body = body(mapping.get("body"), payload);
        headers.set("X-Notification-Id", task.notificationId());
        headers.set("Idempotency-Key", task.taskId());
        headers.set("X-Trace-Id", task.traceId());
        return new OutboundHttpRequest(
                method(task.httpMethod()),
                uri(task.urlTemplate(), pathVariables, query),
                headers,
                body.contentType(),
                body.value()
        );
    }

    private JsonNode readMapping(String requestMapping) {
        if (requestMapping == null || requestMapping.isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }
        return readObject(requestMapping, "requestMapping");
    }

    private JsonNode readObject(String value, String field) {
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node == null || !node.isObject()) {
                throw new DeliveryConfigurationException(field + " must be a JSON object");
            }
            return node;
        } catch (JsonProcessingException exception) {
            throw new DeliveryConfigurationException(field + " is invalid JSON", exception);
        }
    }

    private HttpHeaders endpointHeaders(String defaultHeaders) {
        HttpHeaders headers = new HttpHeaders();
        if (defaultHeaders == null || defaultHeaders.isBlank()) {
            return headers;
        }
        JsonNode values = readObject(defaultHeaders, "defaultHeaders");
        values.properties().forEach(entry -> {
            requireWritableHeader(entry.getKey());
            headers.set(entry.getKey(), scalar(entry.getValue(), "defaultHeaders." + entry.getKey()));
        });
        return headers;
    }

    private void applyRouteHeaders(HttpHeaders headers, JsonNode headerMapping, JsonNode payload) {
        if (headerMapping.isMissingNode() || headerMapping.isNull()) {
            return;
        }
        requireObject(headerMapping, "headers");
        headerMapping.properties().forEach(entry -> {
            requireWritableHeader(entry.getKey());
            JsonNode value = resolveExpression(entry.getValue(), payload, "headers." + entry.getKey());
            if (!value.isMissingNode()) {
                headers.set(entry.getKey(), scalar(value, "headers." + entry.getKey()));
            }
        });
    }

    private Map<String, String> resolveFields(JsonNode mapping, JsonNode payload, String field) {
        Map<String, String> result = new LinkedHashMap<>();
        if (mapping.isMissingNode() || mapping.isNull()) {
            return result;
        }
        requireObject(mapping, field);
        mapping.properties().forEach(entry -> {
            JsonNode value = resolveExpression(entry.getValue(), payload, field + "." + entry.getKey());
            if (!value.isMissingNode()) {
                result.put(entry.getKey(), scalar(value, field + "." + entry.getKey()));
            }
        });
        return result;
    }

    private Body body(JsonNode bodyMapping, JsonNode payload) {
        if (bodyMapping == null || bodyMapping.isNull()) {
            return new Body(MediaType.APPLICATION_JSON, payload.toString());
        }
        requireObject(bodyMapping, "body");
        String type = requiredText(bodyMapping, "type", "body").toUpperCase(Locale.ROOT);
        return switch (type) {
            case "JSON" -> new Body(MediaType.APPLICATION_JSON, write(resolveTemplate(required(bodyMapping, "template", "body"), payload)));
            case "FORM_URLENCODED" -> new Body(
                    new MediaType(MediaType.APPLICATION_FORM_URLENCODED, StandardCharsets.UTF_8),
                    form(resolveFields(required(bodyMapping, "fields", "body"), payload, "body.fields"))
            );
            case "XML" -> new Body(
                    new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8),
                    xml(requiredText(bodyMapping, "root", "body"),
                            resolveFields(required(bodyMapping, "fields", "body"), payload, "body.fields"))
            );
            default -> throw new DeliveryConfigurationException("body.type must be JSON, FORM_URLENCODED, or XML");
        };
    }

    private JsonNode resolveTemplate(JsonNode template, JsonNode payload) {
        if (isExpression(template)) {
            return resolveExpression(template, payload, "body.template");
        }
        if (template.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            template.properties().forEach(entry -> {
                JsonNode value = resolveTemplate(entry.getValue(), payload);
                if (!value.isMissingNode()) {
                    result.set(entry.getKey(), value);
                }
            });
            return result;
        }
        if (template.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            template.forEach(value -> {
                JsonNode mapped = resolveTemplate(value, payload);
                if (!mapped.isMissingNode()) {
                    result.add(mapped);
                }
            });
            return result;
        }
        return template.deepCopy();
    }

    private JsonNode resolveExpression(JsonNode expression, JsonNode payload, String field) {
        requireObject(expression, field);
        boolean hasFrom = expression.has("from");
        boolean hasValue = expression.has("value");
        if (hasFrom == hasValue) {
            throw new DeliveryConfigurationException(field + " must contain exactly one of from or value");
        }
        if (hasValue) {
            return expression.get("value").deepCopy();
        }
        String pointer = requiredText(expression, "from", field);
        if (!pointer.isEmpty() && !pointer.startsWith("/")) {
            throw new DeliveryConfigurationException(field + ".from must be an RFC 6901 JSON Pointer");
        }
        JsonNode value = payload.at(pointer);
        if (!value.isMissingNode() && !value.isNull()) {
            return value.deepCopy();
        }
        if (expression.path("required").asBoolean(true)) {
            throw new DeliveryConfigurationException(field + " required payload value is missing");
        }
        return MissingNode.getInstance();
    }

    private URI uri(String template, Map<String, String> pathVariables, Map<String, String> query) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(template);
            query.forEach(builder::queryParam);
            URI uri = builder.buildAndExpand(pathVariables).encode().toUri();
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new DeliveryConfigurationException("Endpoint URL must use HTTP or HTTPS");
            }
            endpointUrlPolicy.validate(uri);
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new DeliveryConfigurationException("Endpoint URL is invalid", exception);
        }
    }

    private HttpMethod method(String value) {
        try {
            return HttpMethod.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new DeliveryConfigurationException("Endpoint HTTP method is invalid", exception);
        }
    }

    private String form(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String xml(String root, Map<String, String> fields) {
        requireXmlName(root, "body.root");
        StringBuilder xml = new StringBuilder("<").append(root).append('>');
        fields.forEach((name, value) -> {
            requireXmlName(name, "body.fields");
            xml.append('<').append(name).append('>').append(escapeXml(value)).append("</").append(name).append('>');
        });
        return xml.append("</").append(root).append('>').toString();
    }

    private String write(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new DeliveryConfigurationException("Mapped JSON body cannot be serialized", exception);
        }
    }

    private void requireWritableHeader(String name) {
        if (RESERVED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
            throw new DeliveryConfigurationException(name + " is a platform-reserved HTTP header");
        }
    }

    private void requireObject(JsonNode value, String field) {
        if (!value.isObject()) {
            throw new DeliveryConfigurationException(field + " must be a JSON object");
        }
    }

    private JsonNode required(JsonNode object, String field, String context) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            throw new DeliveryConfigurationException(context + " requires " + field);
        }
        return value;
    }

    private String requiredText(JsonNode object, String field, String context) {
        JsonNode value = required(object, field, context);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new DeliveryConfigurationException(context + "." + field + " must be a non-blank string");
        }
        return value.textValue();
    }

    private boolean isExpression(JsonNode value) {
        return value.isObject() && (value.has("from") || value.has("value"));
    }

    private String scalar(JsonNode value, String field) {
        if (!value.isValueNode() || value.isNull()) {
            throw new DeliveryConfigurationException(field + " must resolve to a scalar value");
        }
        return value.asText();
    }

    private void requireXmlName(String name, String field) {
        if (!name.matches("[A-Za-z_][A-Za-z0-9_.-]*")) {
            throw new DeliveryConfigurationException(field + " contains an invalid XML name");
        }
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record Body(MediaType contentType, String value) {
    }
}
