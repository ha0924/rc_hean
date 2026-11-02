package com.rightcapital.notification.api.infrastructure.mq;

import com.rightcapital.notification.api.application.notification.LocalNotificationTransactionService;
import com.rightcapital.notification.api.domain.notification.NotificationTransactionCommand;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
@RocketMQTransactionListener(rocketMQTemplateBeanName = "rocketMQTemplate")
class NotificationTransactionListener implements RocketMQLocalTransactionListener {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationTransactionListener.class);

    private final LocalNotificationTransactionService localTransactionService;

    NotificationTransactionListener(LocalNotificationTransactionService localTransactionService) {
        this.localTransactionService = localTransactionService;
    }

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message message, Object argument) {
        if (!(argument instanceof NotificationTransactionCommand command)) {
            LOG.error("Rejecting transaction message because its local transaction command is missing");
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        try {
            localTransactionService.persist(command);
            return RocketMQLocalTransactionState.COMMIT;
        } catch (DataIntegrityViolationException exception) {
            LOG.info("Rolling back duplicate or invalid notification transaction: taskId={}", command.taskId());
            return RocketMQLocalTransactionState.ROLLBACK;
        } catch (DataAccessException exception) {
            LOG.warn("Local transaction state is unknown because MySQL is unavailable: taskId={}, reason={}",
                    command.taskId(), exception.getMessage());
            return RocketMQLocalTransactionState.UNKNOWN;
        } catch (RuntimeException exception) {
            LOG.error("Rolling back notification transaction: taskId={}", command.taskId(), exception);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message message) {
        String taskId = message.getHeaders().get(RocketMQHeaders.KEYS, String.class);
        if (taskId == null || taskId.isBlank()) {
            LOG.error("Rejecting transaction check without a task key");
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        try {
            return localTransactionService.isCommitted(taskId)
                    ? RocketMQLocalTransactionState.COMMIT
                    : RocketMQLocalTransactionState.ROLLBACK;
        } catch (DataAccessException exception) {
            LOG.warn("Transaction check remains unknown because MySQL is unavailable: taskId={}, reason={}",
                    taskId, exception.getMessage());
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }
}
