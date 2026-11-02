package com.rightcapital.notification.api.domain.notification;

import java.util.Optional;

public interface NotificationRepository {

    Optional<NotificationRecord> findByApplicationIdAndIdempotencyKey(long applicationId, String idempotencyKey);

    void insert(NotificationTransactionCommand command);

    boolean deliveryTaskExists(String taskId);
}
