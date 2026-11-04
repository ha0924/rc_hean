package com.rightcapital.notification.api.application.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayloadSchemaValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PayloadSchemaValidator validator = new PayloadSchemaValidator(objectMapper);

    @Test
    void acceptsPayloadThatMatchesDraft202012Schema() throws Exception {
        assertThatCode(() -> validator.validate("""
                {"type":"object","required":["contactId"],"properties":{"contactId":{"type":"string","minLength":1}}}
                """, objectMapper.readTree("{\"contactId\":\"C-100\"}")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPayloadThatViolatesConfiguredSchema() throws Exception {
        assertThatThrownBy(() -> validator.validate("""
                {"type":"object","required":["contactId"],"properties":{"contactId":{"type":"string"}}}
                """, objectMapper.readTree("{\"contactId\":100}")))
                .isInstanceOf(PayloadSchemaValidationException.class);
    }

    @Test
    void rejectsExternalSchemaReference() throws Exception {
        assertThatThrownBy(() -> validator.validate("""
                {"$ref":"https://schema.example.com/contact.json"}
                """, objectMapper.readTree("{}")))
                .isInstanceOf(NotificationConfigurationException.class)
                .hasMessageContaining("external $ref");
    }
}
