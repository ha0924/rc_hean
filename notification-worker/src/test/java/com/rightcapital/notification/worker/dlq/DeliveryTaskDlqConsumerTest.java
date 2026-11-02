package com.rightcapital.notification.worker.dlq;

import com.rightcapital.notification.common.message.DeliveryTaskMessage;
import com.rightcapital.notification.worker.application.DeliveryExecutionService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeliveryTaskDlqConsumerTest {

    private final DeliveryExecutionService service = mock(DeliveryExecutionService.class);
    private final DeliveryTaskDlqConsumer consumer = new DeliveryTaskDlqConsumer(service);

    @Test
    void recordsTerminalFailureForDeadLetteredTask() {
        DeliveryTaskMessage message = new DeliveryTaskMessage("notification-1", "task-1", "trace-1");

        consumer.onMessage(message);

        verify(service).failFromDeadLetter(message);
    }
}
