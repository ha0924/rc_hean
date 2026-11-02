package com.rightcapital.notification.api.infrastructure.mq;

import com.rightcapital.notification.api.application.notification.NotificationTransactionProducer;
import com.rightcapital.notification.api.application.notification.NotificationMessagingException;
import com.rightcapital.notification.api.application.notification.TransactionDispatchResult;
import com.rightcapital.notification.api.domain.notification.NotificationTransactionCommand;
import com.rightcapital.notification.common.constants.MqConstants;
import com.rightcapital.notification.common.message.DeliveryTaskMessage;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Component;

@Component
class RocketMqNotificationTransactionProducer implements NotificationTransactionProducer {

    private final RocketMQTemplate rocketMQTemplate;

    RocketMqNotificationTransactionProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @Override
    public TransactionDispatchResult send(NotificationTransactionCommand command) {
        try {
            DeliveryTaskMessage payload = new DeliveryTaskMessage(
                    command.notificationId(), command.taskId(), command.traceId()
            );
            TransactionSendResult result = rocketMQTemplate.sendMessageInTransaction(
                    MqConstants.DELIVERY_TOPIC + ":" + MqConstants.DELIVERY_TAG,
                    MessageBuilder.withPayload(payload)
                            .setHeader(RocketMQHeaders.KEYS, command.taskId())
                            .build(),
                    command
            );
            return map(result.getLocalTransactionState());
        } catch (MessagingException exception) {
            throw new NotificationMessagingException(exception);
        }
    }

    private TransactionDispatchResult map(LocalTransactionState state) {
        if (state == null) {
            return TransactionDispatchResult.UNKNOWN;
        }
        return switch (state) {
            case COMMIT_MESSAGE -> TransactionDispatchResult.COMMITTED;
            case ROLLBACK_MESSAGE -> TransactionDispatchResult.ROLLED_BACK;
            case UNKNOW -> TransactionDispatchResult.UNKNOWN;
        };
    }
}
