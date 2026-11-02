package com.rightcapital.notification.api.application.notification;

import com.rightcapital.notification.api.domain.notification.NotificationRepository;
import com.rightcapital.notification.api.domain.notification.NotificationTransactionCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LocalNotificationTransactionService {

    private final NotificationRepository notificationRepository;
    private final TransactionTemplate transactionTemplate;

    public LocalNotificationTransactionService(
            NotificationRepository notificationRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.notificationRepository = notificationRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void persist(NotificationTransactionCommand command) {
        transactionTemplate.executeWithoutResult(status -> notificationRepository.insert(command));
    }

    public boolean isCommitted(String taskId) {
        return notificationRepository.deliveryTaskExists(taskId);
    }
}
