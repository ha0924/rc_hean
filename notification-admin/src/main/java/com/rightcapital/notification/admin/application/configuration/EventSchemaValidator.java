package com.rightcapital.notification.admin.application.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.stereotype.Component;

@Component
class EventSchemaValidator {

    private final ObjectMapper objectMapper;
    private final SchemaRegistry schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    EventSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void validate(String schemaJson) {
        try {
            JsonNode schema = objectMapper.readTree(schemaJson);
            if (schema == null || !schema.isObject()) {
                throw new IllegalArgumentException("payloadSchema must be a JSON object");
            }
            if (containsExternalReference(schema)) {
                throw new IllegalArgumentException("payloadSchema must not contain external $ref values");
            }
            schemaRegistry.getSchema(schema);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("payloadSchema must be valid JSON", exception);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("payloadSchema is not a valid Draft 2020-12 JSON Schema", exception);
        }
    }

    private boolean containsExternalReference(JsonNode node) {
        if (node.isObject()) {
            JsonNode reference = node.get("$ref");
            if (reference != null && (!reference.isTextual() || !reference.textValue().startsWith("#"))) {
                return true;
            }
            return node.properties().stream().anyMatch(entry -> containsExternalReference(entry.getValue()));
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsExternalReference(child)) {
                    return true;
                }
            }
        }
        return false;
    }
}
