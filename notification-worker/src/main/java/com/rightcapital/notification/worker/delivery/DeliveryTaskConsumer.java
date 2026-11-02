package com.rightcapital.notification.worker.delivery;

import com.rightcapital.notification.common.constants.MqConstants;
import com.rightcapital.notification.common.message.DeliveryTaskMessage;
import com.rightcapital.notification.worker.application.DeliveryExecutionService;
import com.rightcapital.notification.worker.domain.DeliveryExecutionResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/** RocketMQ adapter; acknowledgement is delayed until delivery state is persisted. */
@Component
@RocketMQMessageListener(
        topic = MqConstants.DELIVERY_TOPIC,
        consumerGroup = MqConstants.DELIVERY_CONSUMER_GROUP,
        selectorExpression = MqConstants.DELIVERY_TAG,
        maxReconsumeTimes = 5
)
class DeliveryTaskConsumer implements RocketMQListener<DeliveryTaskMessage> {

    private final DeliveryExecutionService deliveryExecutionService;

    DeliveryTaskConsumer(DeliveryExecutionService deliveryExecutionService) {
        this.deliveryExecutionService = deliveryExecutionService;
    }

    @Override
    public void onMessage(DeliveryTaskMessage message) {
        if (deliveryExecutionService.execute(message) == DeliveryExecutionResult.RETRY) {
            throw new RetryableDeliveryException("Delivery task requires RocketMQ retry: " + message.taskId());
        }
    }
}
