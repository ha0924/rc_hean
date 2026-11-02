package com.rightcapital.notification.api.application.notification;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("Idempotency key was already used with different notification content");
    }
}
