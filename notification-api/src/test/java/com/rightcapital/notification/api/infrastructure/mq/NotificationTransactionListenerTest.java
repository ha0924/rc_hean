package com.rightcapital.notification.api.infrastructure.mq;

import com.rightcapital.notification.api.application.notification.LocalNotificationTransactionService;
import com.rightcapital.notification.api.domain.notification.NotificationTransactionCommand;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationTransactionListenerTest {

    private final LocalNotificationTransactionService localTransactionService = mock(LocalNotificationTransactionService.class);
    private final NotificationTransactionListener listener = new NotificationTransactionListener(localTransactionService);

    @Test
    void commitsHalfMessageAfterLocalTransactionPersistsTheTask() {
        NotificationTransactionCommand command = command();

        RocketMQLocalTransactionState result = listener.executeLocalTransaction(
                MessageBuilder.withPayload("half-message").build(), command
        );

        assertThat(result).isEqualTo(RocketMQLocalTransactionState.COMMIT);
        verify(localTransactionService).persist(command);
    }

    @Test
    void rollsBackHalfMessageForDuplicateOrInvalidLocalData() {
        NotificationTransactionCommand command = command();
        doThrow(new DataIntegrityViolationException("duplicate")).when(localTransactionService).persist(command);

        RocketMQLocalTransactionState result = listener.executeLocalTransaction(
                MessageBuilder.withPayload("half-message").build(), command
        );

        assertThat(result).isEqualTo(RocketMQLocalTransactionState.ROLLBACK);
    }

    @Test
    void leavesTransactionUnknownWhenDatabaseIsTemporarilyUnavailable() {
        NotificationTransactionCommand command = command();
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(localTransactionService).persist(command);

        RocketMQLocalTransactionState result = listener.executeLocalTransaction(
                MessageBuilder.withPayload("half-message").build(), command
        );

        assertThat(result).isEqualTo(RocketMQLocalTransactionState.UNKNOWN);
    }

    @Test
    void commitsTransactionCheckOnlyWhenTaskExistsInDatabase() {
        when(localTransactionService.isCommitted("task-1")).thenReturn(true);

        RocketMQLocalTransactionState result = listener.checkLocalTransaction(MessageBuilder.withPayload(new byte[0])
                .setHeader(RocketMQHeaders.KEYS, "task-1")
                .build());

        assertThat(result).isEqualTo(RocketMQLocalTransactionState.COMMIT);
    }

    @Test
    void rollsBackTransactionCheckWhenTaskWasNeverCommitted() {
        when(localTransactionService.isCommitted("task-1")).thenReturn(false);

        RocketMQLocalTransactionState result = listener.checkLocalTransaction(MessageBuilder.withPayload(new byte[0])
                .setHeader(RocketMQHeaders.KEYS, "task-1")
                .build());

        assertThat(result).isEqualTo(RocketMQLocalTransactionState.ROLLBACK);
    }

    private NotificationTransactionCommand command() {
        return new NotificationTransactionCommand(
                "notification-1", "task-1", 1L, 2L, 3L, 4L,
                "client:payment-1", "{}", "payload-hash", "trace-1"
        );
    }
}
