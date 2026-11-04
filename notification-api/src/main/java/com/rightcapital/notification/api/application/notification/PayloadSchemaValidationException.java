package com.rightcapital.notification.api.application.notification;

import java.util.List;

public class PayloadSchemaValidationException extends RuntimeException {

    public PayloadSchemaValidationException(List<String> errors) {
        super("Payload does not match the configured event schema: " + String.join("; ", errors));
    }
}
