package com.rightcapital.notification.worker.dlq;

import com.rightcapital.notification.common.constants.MqConstants;
import com.rightcapital.notification.common.message.DeliveryTaskMessage;
import com.rightcapital.notification.worker.application.DeliveryExecutionService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/** Marks a delivery as terminal once RocketMQ has exhausted the normal consumer retries. */
@Component
@RocketMQMessageListener(
        topic = MqConstants.DELIVERY_DLQ_TOPIC,
        consumerGroup = MqConstants.DELIVERY_DLQ_CONSUMER_GROUP
)
class DeliveryTaskDlqConsumer implements RocketMQListener<DeliveryTaskMessage> {

    private final DeliveryExecutionService deliveryExecutionService;

    DeliveryTaskDlqConsumer(DeliveryExecutionService deliveryExecutionService) {
        this.deliveryExecutionService = deliveryExecutionService;
    }

    @Override
    public void onMessage(DeliveryTaskMessage message) {
        deliveryExecutionService.failFromDeadLetter(message);
    }
}
