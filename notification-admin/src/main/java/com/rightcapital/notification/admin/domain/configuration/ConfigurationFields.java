package com.rightcapital.notification.admin.domain.configuration;

import java.util.Objects;

final class ConfigurationFields {

    private ConfigurationFields() {
    }

    static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    static String optionalJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }

    static long requiredId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    static int positive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    static <T> T required(T value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " must not be null");
    }
}
