package com.rightcapital.notification.api.application.notification;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
class BcryptSecretVerifier {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    boolean matches(String rawSecret, String secretHash) {
        return rawSecret != null
                && secretHash != null
                && rawSecret.getBytes(StandardCharsets.UTF_8).length <= 72
                && passwordEncoder.matches(rawSecret, secretHash);
    }
}
