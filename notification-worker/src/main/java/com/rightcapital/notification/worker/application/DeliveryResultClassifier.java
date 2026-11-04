package com.rightcapital.notification.worker.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightcapital.notification.worker.domain.DeliveryConfigurationException;
import com.rightcapital.notification.worker.domain.DeliveryTaskDetail;
import com.rightcapital.notification.worker.domain.HttpDeliveryResult;
import org.springframework.stereotype.Component;

/** Classifies endpoint responses with the configured success and retry rules. */
@Component
class DeliveryResultClassifier {

    private final ObjectMapper objectMapper;

    DeliveryResultClassifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    boolean isSuccessful(DeliveryTaskDetail task, HttpDeliveryResult result) {
        return result.httpStatus() != null && matches(task.successRule(), result.httpStatus(), true);
    }

    boolean isRetryable(DeliveryTaskDetail task, HttpDeliveryResult result) {
        if (result.httpStatus() == null) {
            return true;
        }
        return matches(task.retryableRule(), result.httpStatus(), false);
    }

    private boolean matches(String ruleJson, int status, boolean successRule) {
        if (ruleJson == null || ruleJson.isBlank() || "{}".equals(ruleJson.trim())) {
            return successRule ? status >= 200 && status < 300 : status == 408 || status == 429 || status >= 500;
        }
        try {
            JsonNode rule = objectMapper.readTree(ruleJson);
            if (!rule.isObject()) {
                throw new DeliveryConfigurationException("Endpoint HTTP status rule must be a JSON object");
            }
            for (JsonNode value : rule.path("statuses")) {
                if (!value.canConvertToInt()) {
                    throw new DeliveryConfigurationException("Endpoint HTTP status rule contains an invalid status");
                }
                if (status == value.intValue()) {
                    return true;
                }
            }
            for (JsonNode value : rule.path("ranges")) {
                if (!value.isTextual() || !value.textValue().matches("[2-5]xx")) {
                    throw new DeliveryConfigurationException("Endpoint HTTP status rule contains an invalid range");
                }
                if (status / 100 == Character.digit(value.textValue().charAt(0), 10)) {
                    return true;
                }
            }
            return false;
        } catch (JsonProcessingException exception) {
            throw new DeliveryConfigurationException("Endpoint HTTP status rule is invalid JSON", exception);
        }
    }
}
