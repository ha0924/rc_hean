package com.rightcapital.notification.api.application.notification;

import com.rightcapital.notification.api.domain.notification.NotificationTransactionCommand;

public interface NotificationTransactionProducer {

    TransactionDispatchResult send(NotificationTransactionCommand command);
}
