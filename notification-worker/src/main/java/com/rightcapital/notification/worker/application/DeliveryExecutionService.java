package com.rightcapital.notification.worker.application;

import com.rightcapital.notification.common.message.DeliveryTaskMessage;
import com.rightcapital.notification.worker.domain.DeliveryConfigurationException;
import com.rightcapital.notification.worker.domain.DeliveryExecutionResult;
import com.rightcapital.notification.worker.domain.DeliveryTaskDetail;
import com.rightcapital.notification.worker.domain.DeliveryTaskRepository;
import com.rightcapital.notification.worker.domain.ExternalApiClient;
import com.rightcapital.notification.worker.domain.HttpDeliveryResult;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/** Coordinates one consume attempt without holding a database transaction over the HTTP call. */
@Service
public class DeliveryExecutionService {

    private static final Duration LOCK_SAFETY_BUFFER = Duration.ofSeconds(10);

    private final DeliveryTaskRepository taskRepository;
    private final ExternalApiClient externalApiClient;
    private final DeliveryResultClassifier resultClassifier;
    private final WorkerIdentity workerIdentity;
    private final Clock clock;

    DeliveryExecutionService(
            DeliveryTaskRepository taskRepository,
            ExternalApiClient externalApiClient,
            DeliveryResultClassifier resultClassifier,
            WorkerIdentity workerIdentity,
            Clock clock
    ) {
        this.taskRepository = taskRepository;
        this.externalApiClient = externalApiClient;
        this.resultClassifier = resultClassifier;
        this.workerIdentity = workerIdentity;
        this.clock = clock;
    }

    public DeliveryExecutionResult execute(DeliveryTaskMessage message) {
        if (message == null || message.taskId() == null || message.taskId().isBlank()) {
            return DeliveryExecutionResult.RETRY;
        }

        Optional<DeliveryTaskDetail> task = taskRepository.findByTaskId(message.taskId());
        if (task.isEmpty()) {
            return DeliveryExecutionResult.RETRY;
        }

        DeliveryTaskDetail detail = task.get();
        String lockToken = UUID.randomUUID().toString();
        LocalDateTime lockUntil = LocalDateTime.now(clock)
                .plus(Duration.ofMillis(detail.timeoutMs()))
                .plus(LOCK_SAFETY_BUFFER);
        if (!taskRepository.tryClaim(detail.taskId(), workerIdentity.value(), lockToken, lockUntil)) {
            return DeliveryExecutionResult.ACKNOWLEDGE;
        }

        try {
            HttpDeliveryResult result = externalApiClient.deliver(detail);
            if (resultClassifier.isSuccessful(result)) {
                taskRepository.markSucceeded(detail.taskId(), lockToken, result);
                return DeliveryExecutionResult.ACKNOWLEDGE;
            }
            if (resultClassifier.isRetryable(result)) {
                taskRepository.markRetryWait(detail.taskId(), lockToken, result);
                return DeliveryExecutionResult.RETRY;
            }
            taskRepository.markFailed(detail.taskId(), lockToken, result);
            return DeliveryExecutionResult.ACKNOWLEDGE;
        } catch (DeliveryConfigurationException exception) {
            taskRepository.markFailed(detail.taskId(), lockToken, HttpDeliveryResult.transportFailure(
                    0L, "CONFIGURATION", exception.getMessage()
            ));
            return DeliveryExecutionResult.ACKNOWLEDGE;
        }
    }

    public void failFromDeadLetter(DeliveryTaskMessage message) {
        if (message != null && message.taskId() != null && !message.taskId().isBlank()) {
            taskRepository.markFailedFromDeadLetter(message.taskId(), "RocketMQ retry limit exceeded");
        }
    }
}
