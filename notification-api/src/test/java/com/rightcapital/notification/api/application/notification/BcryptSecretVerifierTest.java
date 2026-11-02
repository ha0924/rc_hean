package com.rightcapital.notification.api.application.notification;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class BcryptSecretVerifierTest {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final BcryptSecretVerifier verifier = new BcryptSecretVerifier();

    @Test
    void acceptsMatchingSecretWithinBcryptByteLimit() {
        String secret = "correct-secret";

        assertThat(verifier.matches(secret, passwordEncoder.encode(secret))).isTrue();
    }

    @Test
    void rejectsWrongOrMissingCredentials() {
        String hash = passwordEncoder.encode("correct-secret");

        assertThat(verifier.matches("wrong-secret", hash)).isFalse();
        assertThat(verifier.matches(null, hash)).isFalse();
        assertThat(verifier.matches("correct-secret", null)).isFalse();
    }

    @Test
    void rejectsSecretsLongerThan72Utf8BytesBeforeBcryptCanTruncateThem() {
        String acceptedPrefix = "a".repeat(72);
        String oversizedSecret = acceptedPrefix + "x";

        assertThat(verifier.matches(oversizedSecret, passwordEncoder.encode(acceptedPrefix))).isFalse();
    }
}
