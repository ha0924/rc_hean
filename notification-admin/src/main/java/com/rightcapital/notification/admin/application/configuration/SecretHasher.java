package com.rightcapital.notification.admin.application.configuration;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
class SecretHasher {

    private static final int BCRYPT_MAX_INPUT_BYTES = 72;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    String hash(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            throw new IllegalArgumentException("secret must not be blank");
        }
        if (rawSecret.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("secret must not exceed 72 UTF-8 bytes");
        }
        return passwordEncoder.encode(rawSecret);
    }
}
