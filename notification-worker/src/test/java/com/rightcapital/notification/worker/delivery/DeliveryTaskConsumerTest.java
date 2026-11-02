package com.rightcapital.notification.worker.delivery;

import com.rightcapital.notification.common.message.DeliveryTaskMessage;
import com.rightcapital.notification.worker.application.DeliveryExecutionService;
import com.rightcapital.notification.worker.domain.DeliveryExecutionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryTaskConsumerTest {

    private final DeliveryExecutionService service = mock(DeliveryExecutionService.class);
    private final DeliveryTaskConsumer consumer = new DeliveryTaskConsumer(service);

    @Test
    void returnsNormallySoRocketMqAcknowledgesTerminalDelivery() {
        DeliveryTaskMessage message = message();
        when(service.execute(message)).thenReturn(DeliveryExecutionResult.ACKNOWLEDGE);

        assertThatCode(() -> consumer.onMessage(message)).doesNotThrowAnyException();
    }

    @Test
    void throwsForRetrySoRocketMqRedeliversTheMessage() {
        DeliveryTaskMessage message = message();
        when(service.execute(message)).thenReturn(DeliveryExecutionResult.RETRY);

        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(RetryableDeliveryException.class);
    }

    private DeliveryTaskMessage message() {
        return new DeliveryTaskMessage("notification-1", "task-1", "trace-1");
    }
}
