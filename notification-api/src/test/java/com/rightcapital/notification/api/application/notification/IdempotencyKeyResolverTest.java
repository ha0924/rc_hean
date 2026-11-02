package com.rightcapital.notification.api.application.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyResolverTest {

    private final IdempotencyKeyResolver resolver = new IdempotencyKeyResolver(new CanonicalJsonHasher(new ObjectMapper()));

    @Test
    void prefixesAndTrimsCallerSuppliedKey() {
        String result = resolver.resolve("  payment-001  ", "request-001", 1L, 2L, "{\"amount\":100}");

        assertThat(result).isEqualTo("client:payment-001");
    }

    @Test
    void derivesStableKeyFromRequestIdAndCanonicalPayloadWhenHeaderIsAbsent() {
        String first = resolver.resolve(null, " request-001 ", 1L, 2L, "{\"amount\":100}");
        String second = resolver.resolve("", "request-001", 1L, 2L, "{\"amount\":100}");
        String differentEvent = resolver.resolve(null, "request-001", 1L, 3L, "{\"amount\":100}");

        assertThat(first).isEqualTo(second).startsWith("request:");
        assertThat(differentEvent).isNotEqualTo(first);
    }

    @Test
    void rejectsRequestWithoutEitherIdempotencyInput() {
        assertThatThrownBy(() -> resolver.resolve(null, " ", 1L, 2L, "{}"))
                .isInstanceOf(MissingIdempotencyKeyException.class);
    }

    @Test
    void rejectsClientKeyThatCannotFitDatabaseColumnWithPrefix() {
        String tooLong = "a".repeat(121);

        assertThatIllegalArgumentException().isThrownBy(
                () -> resolver.resolve(tooLong, null, 1L, 2L, "{}")
        ).withMessage("Idempotency-Key must not exceed 120 characters");
    }
}
