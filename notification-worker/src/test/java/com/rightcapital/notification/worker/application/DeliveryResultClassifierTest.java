package com.rightcapital.notification.worker.application;

import com.rightcapital.notification.worker.domain.HttpDeliveryResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryResultClassifierTest {

    private final DeliveryResultClassifier classifier = new DeliveryResultClassifier();

    @Test
    void treats2xxAsSuccessful() {
        HttpDeliveryResult result = HttpDeliveryResult.response(204, 12L, "");

        assertThat(classifier.isSuccessful(result)).isTrue();
        assertThat(classifier.isRetryable(result)).isFalse();
    }

    @Test
    void retriesTransportFailuresTimeoutsRateLimitsAndServerErrors() {
        assertThat(classifier.isRetryable(HttpDeliveryResult.transportFailure(20L, "NETWORK", "timeout"))).isTrue();
        assertThat(classifier.isRetryable(HttpDeliveryResult.response(408, 20L, ""))).isTrue();
        assertThat(classifier.isRetryable(HttpDeliveryResult.response(429, 20L, ""))).isTrue();
        assertThat(classifier.isRetryable(HttpDeliveryResult.response(503, 20L, ""))).isTrue();
    }

    @Test
    void treatsClientErrorsAsFinalFailures() {
        HttpDeliveryResult result = HttpDeliveryResult.response(422, 20L, "invalid payload");

        assertThat(classifier.isSuccessful(result)).isFalse();
        assertThat(classifier.isRetryable(result)).isFalse();
    }
}
