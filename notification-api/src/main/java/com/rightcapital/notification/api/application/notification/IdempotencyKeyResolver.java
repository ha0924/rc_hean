package com.rightcapital.notification.api.application.notification;

import org.springframework.stereotype.Component;

@Component
class IdempotencyKeyResolver {

    private static final int MAX_CLIENT_KEY_LENGTH = 120;

    private final CanonicalJsonHasher hasher;

    IdempotencyKeyResolver(CanonicalJsonHasher hasher) {
        this.hasher = hasher;
    }

    String resolve(
            String suppliedIdempotencyKey,
            String requestId,
            long applicationId,
            long eventDefinitionId,
            String canonicalPayload
    ) {
        if (suppliedIdempotencyKey != null && !suppliedIdempotencyKey.isBlank()) {
            String normalized = suppliedIdempotencyKey.trim();
            if (normalized.length() > MAX_CLIENT_KEY_LENGTH) {
                throw new IllegalArgumentException("Idempotency-Key must not exceed 120 characters");
            }
            return "client:" + normalized;
        }
        if (requestId == null || requestId.isBlank()) {
            throw new MissingIdempotencyKeyException();
        }
        String material = applicationId + "\n" + eventDefinitionId + "\n" + requestId.trim() + "\n" + canonicalPayload;
        return "request:" + hasher.sha256(material);
    }
}
