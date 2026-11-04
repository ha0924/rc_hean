package com.rightcapital.notification.api.application.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class PayloadSchemaValidator {

    private final ObjectMapper objectMapper;
    private final SchemaRegistry schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    PayloadSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void validate(String schemaJson, JsonNode payload) {
        JsonNode schema = parse(schemaJson);
        if (containsExternalReference(schema)) {
            throw new NotificationConfigurationException("Event payload schema must not contain external $ref values");
        }
        try {
            Schema compiledSchema = schemaRegistry.getSchema(schema);
            List<String> errors = compiledSchema.validate(payload).stream().map(this::format).toList();
            if (!errors.isEmpty()) {
                throw new PayloadSchemaValidationException(errors);
            }
        } catch (PayloadSchemaValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new NotificationConfigurationException("Event payload schema is invalid");
        }
    }

    private JsonNode parse(String schemaJson) {
        try {
            JsonNode schema = objectMapper.readTree(schemaJson);
            if (schema == null || !schema.isObject()) {
                throw new NotificationConfigurationException("Event payload schema must be a JSON object");
            }
            return schema;
        } catch (JsonProcessingException exception) {
            throw new NotificationConfigurationException("Event payload schema is invalid JSON");
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

    private String format(Error error) {
        return error.getInstanceLocation() + ": " + error.getMessage();
    }
}
