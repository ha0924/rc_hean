package com.rightcapital.notification.worker.domain;

import java.time.LocalDateTime;
import java.util.Optional;

public interface DeliveryTaskRepository {

    Optional<DeliveryTaskDetail> findByTaskId(String taskId);

    boolean tryClaim(String taskId, String workerId, String lockToken, LocalDateTime lockUntil);

    boolean markSucceeded(String taskId, String lockToken, HttpDeliveryResult result);

    boolean markRetryWait(String taskId, String lockToken, HttpDeliveryResult result);

    boolean markFailed(String taskId, String lockToken, HttpDeliveryResult result);

    void markFailedFromDeadLetter(String taskId, String reason);
}
