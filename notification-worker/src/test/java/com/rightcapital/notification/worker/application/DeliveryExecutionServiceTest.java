package com.rightcapital.notification.worker.application;

import com.rightcapital.notification.common.message.DeliveryTaskMessage;
import com.rightcapital.notification.worker.domain.DeliveryConfigurationException;
import com.rightcapital.notification.worker.domain.DeliveryExecutionResult;
import com.rightcapital.notification.worker.domain.DeliveryTaskDetail;
import com.rightcapital.notification.worker.domain.DeliveryTaskRepository;
import com.rightcapital.notification.worker.domain.EndpointConcurrencyLimiter;
import com.rightcapital.notification.worker.domain.ExternalApiClient;
import com.rightcapital.notification.worker.domain.HttpDeliveryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryExecutionServiceTest {

    private final DeliveryTaskRepository taskRepository = mock(DeliveryTaskRepository.class);
    private final ExternalApiClient externalApiClient = mock(ExternalApiClient.class);
    private final EndpointConcurrencyLimiter concurrencyLimiter = mock(EndpointConcurrencyLimiter.class);
    private DeliveryExecutionService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new DeliveryExecutionService(
                taskRepository,
                externalApiClient,
                new DeliveryResultClassifier(new com.fasterxml.jackson.databind.ObjectMapper()),
                concurrencyLimiter,
                new WorkerIdentity(),
                Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)
        );
        when(taskRepository.findByTaskId("task-1")).thenReturn(Optional.of(task()));
        when(concurrencyLimiter.acquire(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(() -> { });
    }

    @Test
    void acknowledgesAfterSuccessfulHttpDelivery() {
        when(taskRepository.tryClaim(anyString(), anyString(), anyString(), any())).thenReturn(true);
        when(externalApiClient.deliver(task())).thenReturn(HttpDeliveryResult.response(202, 15L, "accepted"));

        DeliveryExecutionResult result = service.execute(message());

        assertThat(result).isEqualTo(DeliveryExecutionResult.ACKNOWLEDGE);
        verify(taskRepository).markSucceeded(anyString(), anyString(), any(HttpDeliveryResult.class));
    }

    @Test
    void requestsBrokerRetryAfterRetryableHttpFailure() {
        when(taskRepository.tryClaim(anyString(), anyString(), anyString(), any())).thenReturn(true);
        when(externalApiClient.deliver(task())).thenReturn(HttpDeliveryResult.response(503, 15L, "unavailable"));

        DeliveryExecutionResult result = service.execute(message());

        assertThat(result).isEqualTo(DeliveryExecutionResult.RETRY);
        verify(taskRepository).markRetryWait(anyString(), anyString(), any(HttpDeliveryResult.class));
    }

    @Test
    void acknowledgesAfterFinalClientError() {
        when(taskRepository.tryClaim(anyString(), anyString(), anyString(), any())).thenReturn(true);
        when(externalApiClient.deliver(task())).thenReturn(HttpDeliveryResult.response(422, 15L, "invalid payload"));

        DeliveryExecutionResult result = service.execute(message());

        assertThat(result).isEqualTo(DeliveryExecutionResult.ACKNOWLEDGE);
        verify(taskRepository).markFailed(anyString(), anyString(), any(HttpDeliveryResult.class));
    }

    @Test
    void acknowledgesDuplicateMessageWithoutCallingExternalApi() {
        when(taskRepository.tryClaim(anyString(), anyString(), anyString(), any())).thenReturn(false);

        DeliveryExecutionResult result = service.execute(message());

        assertThat(result).isEqualTo(DeliveryExecutionResult.ACKNOWLEDGE);
        verify(externalApiClient, never()).deliver(any());
    }

    @Test
    void marksEndpointConfigurationErrorAsFinalFailure() {
        when(taskRepository.tryClaim(anyString(), anyString(), anyString(), any())).thenReturn(true);
        when(externalApiClient.deliver(task())).thenThrow(new DeliveryConfigurationException("invalid URL"));

        DeliveryExecutionResult result = service.execute(message());

        assertThat(result).isEqualTo(DeliveryExecutionResult.ACKNOWLEDGE);
        verify(taskRepository).markFailed(anyString(), anyString(), any(HttpDeliveryResult.class));
    }

    @Test
    void asksForRetryWhenTaskIsNotInDatabase() {
        when(taskRepository.findByTaskId("missing-task")).thenReturn(Optional.empty());

        assertThat(service.execute(new DeliveryTaskMessage("notification-1", "missing-task", "trace-1")))
                .isEqualTo(DeliveryExecutionResult.RETRY);
    }

    private DeliveryTaskMessage message() {
        return new DeliveryTaskMessage("notification-1", "task-1", "trace-1");
    }

    private DeliveryTaskDetail task() {
        return new DeliveryTaskDetail(
                "task-1", "notification-1", 3L, "{\"amount\":100}", "trace-1",
                "POST", "https://example.test/callback", "NONE", null, "{\"Accept\":\"application/json\"}", 5000,
                8, "{}", "{}", "{}"
        );
    }
}
