package com.rightcapital.notification.worker.application;

import com.rightcapital.notification.worker.domain.HttpDeliveryResult;
import com.rightcapital.notification.worker.domain.DeliveryTaskDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryResultClassifierTest {

    private final DeliveryResultClassifier classifier = new DeliveryResultClassifier(new ObjectMapper());

    @Test
    void treats2xxAsSuccessful() {
        HttpDeliveryResult result = HttpDeliveryResult.response(204, 12L, "");

        assertThat(classifier.isSuccessful(task("{}", "{}"), result)).isTrue();
        assertThat(classifier.isRetryable(task("{}", "{}"), result)).isFalse();
    }

    @Test
    void retriesTransportFailuresTimeoutsRateLimitsAndServerErrors() {
        assertThat(classifier.isRetryable(task("{}", "{}"), HttpDeliveryResult.transportFailure(20L, "NETWORK", "timeout"))).isTrue();
        assertThat(classifier.isRetryable(task("{}", "{}"), HttpDeliveryResult.response(408, 20L, ""))).isTrue();
        assertThat(classifier.isRetryable(task("{}", "{}"), HttpDeliveryResult.response(429, 20L, ""))).isTrue();
        assertThat(classifier.isRetryable(task("{}", "{}"), HttpDeliveryResult.response(503, 20L, ""))).isTrue();
    }

    @Test
    void treatsClientErrorsAsFinalFailures() {
        HttpDeliveryResult result = HttpDeliveryResult.response(422, 20L, "invalid payload");

        assertThat(classifier.isSuccessful(task("{}", "{}"), result)).isFalse();
        assertThat(classifier.isRetryable(task("{}", "{}"), result)).isFalse();
    }

    @Test
    void usesEndpointSpecificStatusRules() {
        DeliveryTaskDetail task = task("{\"statuses\":[202]}", "{\"statuses\":[404]}");

        assertThat(classifier.isSuccessful(task, HttpDeliveryResult.response(202, 1L, ""))).isTrue();
        assertThat(classifier.isRetryable(task, HttpDeliveryResult.response(404, 1L, ""))).isTrue();
        assertThat(classifier.isSuccessful(task, HttpDeliveryResult.response(200, 1L, ""))).isFalse();
    }

    private DeliveryTaskDetail task(String successRule, String retryableRule) {
        return new DeliveryTaskDetail(
                "task-1", "notification-1", 3L, "{}", "trace-1", "POST", "https://example.test/callback",
                "NONE", null, null, 5000, 8, successRule, retryableRule, "{}"
        );
    }
}
