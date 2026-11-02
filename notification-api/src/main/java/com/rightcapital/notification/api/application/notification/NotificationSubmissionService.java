package com.rightcapital.notification.api.application.notification;

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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationSubmissionService {

    private final BusinessApplicationAuthenticationRepository applicationRepository;
    private final EventConfigurationRepository eventRepository;
    private final DeliveryRouteRepository routeRepository;
    private final NotificationRepository notificationRepository;
    private final BcryptSecretVerifier secretVerifier;
    private final CanonicalJsonHasher jsonHasher;
    private final IdempotencyKeyResolver idempotencyKeyResolver;
    private final NotificationTransactionProducer transactionProducer;

    public NotificationSubmissionService(
            BusinessApplicationAuthenticationRepository applicationRepository,
            EventConfigurationRepository eventRepository,
            DeliveryRouteRepository routeRepository,
            NotificationRepository notificationRepository,
            BcryptSecretVerifier secretVerifier,
            CanonicalJsonHasher jsonHasher,
            IdempotencyKeyResolver idempotencyKeyResolver,
            NotificationTransactionProducer transactionProducer
    ) {
        this.applicationRepository = applicationRepository;
        this.eventRepository = eventRepository;
        this.routeRepository = routeRepository;
        this.notificationRepository = notificationRepository;
        this.secretVerifier = secretVerifier;
        this.jsonHasher = jsonHasher;
        this.idempotencyKeyResolver = idempotencyKeyResolver;
        this.transactionProducer = transactionProducer;
    }

    public NotificationAcceptance submit(NotificationSubmissionCommand command) {
        if (!command.payload().isObject()) {
            throw new IllegalArgumentException("payload must be a JSON object");
        }
        AuthenticatedApplication application = authenticate(command.accessKey(), command.secret());
        ConfiguredEvent event = findEvent(application.id(), command.eventCode());
        ActiveDeliveryRoute route = findOnlyActiveRoute(event.id());
        CanonicalJsonHasher.CanonicalJson canonicalPayload = jsonHasher.canonicalize(command.payload());
        String idempotencyKey = idempotencyKeyResolver.resolve(
                command.suppliedIdempotencyKey(),
                command.requestId(),
                application.id(),
                event.id(),
                canonicalPayload.json()
        );

        Optional<NotificationRecord> existing = notificationRepository.findByApplicationIdAndIdempotencyKey(
                application.id(), idempotencyKey
        );
        if (existing.isPresent()) {
            return fromExisting(existing.get(), event.id(), canonicalPayload.hash());
        }

        NotificationTransactionCommand transactionCommand = new NotificationTransactionCommand(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                application.id(),
                event.id(),
                route.id(),
                route.endpointId(),
                idempotencyKey,
                canonicalPayload.json(),
                canonicalPayload.hash(),
                traceId(command.traceId())
        );

        TransactionDispatchResult result = transactionProducer.send(transactionCommand);
        if (result == TransactionDispatchResult.COMMITTED) {
            return new NotificationAcceptance(
                    transactionCommand.notificationId(), NotificationStatus.ACCEPTED, false
            );
        }

        return resolveAfterUncertainTransaction(application.id(), idempotencyKey, event.id(), canonicalPayload.hash());
    }

    private AuthenticatedApplication authenticate(String accessKey, String secret) {
        AuthenticatedApplication application = applicationRepository.findActiveByAccessKey(accessKey)
                .orElseThrow(NotificationAuthenticationException::new);
        if (!secretVerifier.matches(secret, application.secretHash())) {
            throw new NotificationAuthenticationException();
        }
        return application;
    }

    private ConfiguredEvent findEvent(long applicationId, String eventCode) {
        return eventRepository.findActiveByApplicationIdAndEventCode(applicationId, eventCode)
                .orElseThrow(() -> new NotificationConfigurationException("Active event definition was not found"));
    }

    private ActiveDeliveryRoute findOnlyActiveRoute(long eventDefinitionId) {
        List<ActiveDeliveryRoute> routes = routeRepository.findActiveByEventDefinitionId(eventDefinitionId);
        if (routes.isEmpty()) {
            throw new NotificationConfigurationException("Active notification route was not found");
        }
        if (routes.size() > 1) {
            throw new NotificationConfigurationException("Initial version requires exactly one active notification route");
        }
        return routes.getFirst();
    }

    private NotificationAcceptance resolveAfterUncertainTransaction(
            long applicationId,
            String idempotencyKey,
            long eventDefinitionId,
            String payloadHash
    ) {
        return notificationRepository.findByApplicationIdAndIdempotencyKey(applicationId, idempotencyKey)
                .map(existing -> fromExisting(existing, eventDefinitionId, payloadHash))
                .orElseThrow(NotificationAcceptanceException::new);
    }

    private NotificationAcceptance fromExisting(
            NotificationRecord existing,
            long eventDefinitionId,
            String payloadHash
    ) {
        if (existing.eventDefinitionId() != eventDefinitionId || !existing.payloadHash().equals(payloadHash)) {
            throw new IdempotencyConflictException();
        }
        return new NotificationAcceptance(existing.id(), existing.status(), true);
    }

    private String traceId(String suppliedTraceId) {
        if (suppliedTraceId == null || suppliedTraceId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String traceId = suppliedTraceId.trim();
        if (traceId.length() > 128) {
            throw new IllegalArgumentException("X-Trace-Id must not exceed 128 characters");
        }
        return traceId;
    }
}
