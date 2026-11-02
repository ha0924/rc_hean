package com.rightcapital.notification.api.infrastructure.mq;

import com.rightcapital.notification.api.application.notification.NotificationMessagingException;
import com.rightcapital.notification.api.application.notification.TransactionDispatchResult;
import com.rightcapital.notification.api.domain.notification.NotificationTransactionCommand;
import com.rightcapital.notification.common.constants.MqConstants;
import com.rightcapital.notification.common.message.DeliveryTaskMessage;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMqNotificationTransactionProducerTest {

    private final RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
    private final RocketMqNotificationTransactionProducer producer =
            new RocketMqNotificationTransactionProducer(rocketMQTemplate);

    @Test
    void sendsHalfMessageWithTaskKeyAndDeliveryPayload() {
        NotificationTransactionCommand command = command();
        when(rocketMQTemplate.sendMessageInTransaction(
                eq(MqConstants.DELIVERY_TOPIC + ":" + MqConstants.DELIVERY_TAG), any(Message.class), same(command)
        )).thenReturn(result(LocalTransactionState.COMMIT_MESSAGE));

        TransactionDispatchResult dispatchResult = producer.send(command);

        ArgumentCaptor<Message<?>> messageCaptor = messageCaptor();
        verify(rocketMQTemplate).sendMessageInTransaction(
                eq(MqConstants.DELIVERY_TOPIC + ":" + MqConstants.DELIVERY_TAG), messageCaptor.capture(), same(command)
        );
        Message<?> message = messageCaptor.getValue();
        assertThat(dispatchResult).isEqualTo(TransactionDispatchResult.COMMITTED);
        assertThat(message.getHeaders().get(RocketMQHeaders.KEYS)).isEqualTo("task-1");
        assertThat(message.getPayload()).isEqualTo(new DeliveryTaskMessage("notification-1", "task-1", "trace-1"));
    }

    @ParameterizedTest
    @MethodSource("transactionStates")
    void mapsBrokerTransactionState(LocalTransactionState state, TransactionDispatchResult expected) {
        NotificationTransactionCommand command = command();
        when(rocketMQTemplate.sendMessageInTransaction(
                any(String.class), any(Message.class), same(command)
        )).thenReturn(result(state));

        assertThat(producer.send(command)).isEqualTo(expected);
    }

    @Test
    void treatsMissingBrokerStateAsUnknown() {
        NotificationTransactionCommand command = command();
        when(rocketMQTemplate.sendMessageInTransaction(
                any(String.class), any(Message.class), same(command)
        )).thenReturn(result(null));

        assertThat(producer.send(command)).isEqualTo(TransactionDispatchResult.UNKNOWN);
    }

    @Test
    void translatesMessagingFailureToApplicationException() {
        NotificationTransactionCommand command = command();
        when(rocketMQTemplate.sendMessageInTransaction(
                any(String.class), any(Message.class), same(command)
        )).thenThrow(new MessagingException("broker unavailable"));

        assertThatThrownBy(() -> producer.send(command))
                .isInstanceOf(NotificationMessagingException.class)
                .hasCauseInstanceOf(MessagingException.class);
    }

    private static Stream<Arguments> transactionStates() {
        return Stream.of(
                Arguments.of(LocalTransactionState.COMMIT_MESSAGE, TransactionDispatchResult.COMMITTED),
                Arguments.of(LocalTransactionState.ROLLBACK_MESSAGE, TransactionDispatchResult.ROLLED_BACK),
                Arguments.of(LocalTransactionState.UNKNOW, TransactionDispatchResult.UNKNOWN)
        );
    }

    private static TransactionSendResult result(LocalTransactionState state) {
        TransactionSendResult result = new TransactionSendResult();
        result.setLocalTransactionState(state);
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<Message<?>> messageCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Message.class);
    }

    private NotificationTransactionCommand command() {
        return new NotificationTransactionCommand(
                "notification-1", "task-1", 1L, 2L, 3L, 4L,
                "client:payment-1", "{\"amount\":100}", "payload-hash", "trace-1"
        );
    }
}
