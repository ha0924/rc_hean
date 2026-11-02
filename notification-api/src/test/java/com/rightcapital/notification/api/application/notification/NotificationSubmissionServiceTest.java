package com.rightcapital.notification.api.application.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightcapital.notification.api.domain.notification.ActiveDeliveryRoute;
import com.rightcapital.notification.api.domain.notification.AuthenticatedApplication;
import com.rightcapital.notification.api.domain.notification.BusinessApplicationAuthenticationRepository;
import com.rightcapital.notification.api.domain.notification.ConfiguredEvent;
import com.rightcapital.notification.api.domain.notification.DeliveryRouteRepository;
import com.rightcapital.notification.api.domain.notification.EventConfigurationRepository;
import com.rightcapital.notification.api.domain.notification.NotificationRecord;
import com.rightcapital.notification.api.domain.notification.NotificationRepository;
import com.rightcapital.notification.api.domain.notification.NotificationTransactionCommand;
import com.rightcapital.notification.common.enums.NotificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationSubmissionServiceTest {

    private static final String SECRET = "subscription-secret-v1";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BusinessApplicationAuthenticationRepository applicationRepository = mock(
            BusinessApplicationAuthenticationRepository.class
    );
    private final EventConfigurationRepository eventRepository = mock(EventConfigurationRepository.class);
    private final DeliveryRouteRepository routeRepository = mock(DeliveryRouteRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationTransactionProducer transactionProducer = mock(NotificationTransactionProducer.class);

    private CanonicalJsonHasher jsonHasher;
    private NotificationSubmissionService service;

    @BeforeEach
    void setUp() {
        jsonHasher = new CanonicalJsonHasher(objectMapper);
        service = new NotificationSubmissionService(
                applicationRepository,
                eventRepository,
                routeRepository,
                notificationRepository,
                new BcryptSecretVerifier(),
                jsonHasher,
                new IdempotencyKeyResolver(jsonHasher),
                transactionProducer
        );
        when(applicationRepository.findActiveByAccessKey("subscription-access-key")).thenReturn(Optional.of(
                new AuthenticatedApplication(
                        11L,
                        "subscription-service",
                        new BCryptPasswordEncoder().encode(SECRET)
                )
        ));
        when(eventRepository.findActiveByApplicationIdAndEventCode(11L, "subscription.payment_succeeded"))
                .thenReturn(Optional.of(new ConfiguredEvent(21L, "subscription.payment_succeeded")));
        when(routeRepository.findActiveByEventDefinitionId(21L))
                .thenReturn(java.util.List.of(new ActiveDeliveryRoute(31L, 41L)));
    }

    @Test
    void acceptsNewNotificationWithCallerSuppliedIdempotencyKey() throws Exception {
        when(notificationRepository.findByApplicationIdAndIdempotencyKey(anyLong(), anyString()))
                .thenReturn(Optional.empty());
        when(transactionProducer.send(any(NotificationTransactionCommand.class))).thenReturn(TransactionDispatchResult.COMMITTED);

        NotificationAcceptance acceptance = service.submit(command(
                "payment-0001", null, objectMapper.readTree("{\"amount\":100,\"contactId\":\"C-1\"}")
        ));

        ArgumentCaptor<NotificationTransactionCommand> captor = ArgumentCaptor.forClass(NotificationTransactionCommand.class);
        verify(transactionProducer).send(captor.capture());
        NotificationTransactionCommand transaction = captor.getValue();
        assertThat(acceptance.status()).isEqualTo(NotificationStatus.ACCEPTED);
        assertThat(acceptance.idempotentReplay()).isFalse();
        assertThat(acceptance.notificationId()).isEqualTo(transaction.notificationId());
        assertThat(transaction.idempotencyKey()).isEqualTo("client:payment-0001");
        assertThat(transaction.routeId()).isEqualTo(31L);
        assertThat(transaction.endpointId()).isEqualTo(41L);
        assertThat(transaction.payloadHash()).hasSize(64);
    }

    @Test
    void returnsExistingNotificationWithoutSendingAnotherHalfMessage() throws Exception {
        CanonicalJsonHasher.CanonicalJson canonicalPayload = jsonHasher.canonicalize(
                objectMapper.readTree("{\"contactId\":\"C-1\"}")
        );
        when(notificationRepository.findByApplicationIdAndIdempotencyKey(11L, "client:payment-0001"))
                .thenReturn(Optional.of(new NotificationRecord(
                        "existing-notification", 11L, 21L, "client:payment-0001", canonicalPayload.hash(),
                        NotificationStatus.PROCESSING
                )));

        NotificationAcceptance acceptance = service.submit(command(
                "payment-0001", null, objectMapper.readTree("{\"contactId\":\"C-1\"}")
        ));

        assertThat(acceptance).isEqualTo(new NotificationAcceptance(
                "existing-notification", NotificationStatus.PROCESSING, true
        ));
        verify(transactionProducer, never()).send(any(NotificationTransactionCommand.class));
    }

    @Test
    void derivesTheSameFallbackKeyForEquivalentJsonPayloads() throws Exception {
        when(notificationRepository.findByApplicationIdAndIdempotencyKey(anyLong(), anyString()))
                .thenReturn(Optional.empty());
        when(transactionProducer.send(any(NotificationTransactionCommand.class))).thenReturn(TransactionDispatchResult.COMMITTED);

        service.submit(command(null, "request-100", objectMapper.readTree("{\"amount\":100,\"contactId\":\"C-1\"}")));
        service.submit(command(null, "request-100", objectMapper.readTree("{\"contactId\":\"C-1\",\"amount\":100}")));

        ArgumentCaptor<NotificationTransactionCommand> captor = ArgumentCaptor.forClass(NotificationTransactionCommand.class);
        verify(transactionProducer, org.mockito.Mockito.times(2)).send(captor.capture());
        assertThat(captor.getAllValues().get(0).idempotencyKey())
                .isEqualTo(captor.getAllValues().get(1).idempotencyKey())
                .startsWith("request:");
    }

    @Test
    void rejectsReuseOfIdempotencyKeyForDifferentPayload() throws Exception {
        when(notificationRepository.findByApplicationIdAndIdempotencyKey(11L, "client:payment-0001"))
                .thenReturn(Optional.of(new NotificationRecord(
                        "existing-notification", 11L, 21L, "client:payment-0001", "different-payload-hash",
                        NotificationStatus.ACCEPTED
                )));

        assertThatThrownBy(() -> service.submit(command(
                "payment-0001", null, objectMapper.readTree("{\"contactId\":\"C-1\"}")
        )))
                .isInstanceOf(IdempotencyConflictException.class);
        verify(transactionProducer, never()).send(any(NotificationTransactionCommand.class));
    }

    @Test
    void resolvesDuplicateRaceAfterTransactionRollbackAsAnIdempotentReplay() throws Exception {
        CanonicalJsonHasher.CanonicalJson canonicalPayload = jsonHasher.canonicalize(
                objectMapper.readTree("{\"contactId\":\"C-1\"}")
        );
        when(notificationRepository.findByApplicationIdAndIdempotencyKey(11L, "client:payment-0001"))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(new NotificationRecord(
                                "existing-notification", 11L, 21L, "client:payment-0001", canonicalPayload.hash(),
                                NotificationStatus.ACCEPTED
                        ))
                );
        when(transactionProducer.send(any(NotificationTransactionCommand.class)))
                .thenReturn(TransactionDispatchResult.ROLLED_BACK);

        NotificationAcceptance acceptance = service.submit(command(
                "payment-0001", null, objectMapper.readTree("{\"contactId\":\"C-1\"}")
        ));

        assertThat(acceptance).isEqualTo(new NotificationAcceptance(
                "existing-notification", NotificationStatus.ACCEPTED, true
        ));
    }

    private NotificationSubmissionCommand command(String idempotencyKey, String requestId, JsonNode payload) {
        return new NotificationSubmissionCommand(
                "subscription-access-key",
                SECRET,
                "subscription.payment_succeeded",
                idempotencyKey,
                requestId,
                payload,
                "trace-100"
        );
    }
}
