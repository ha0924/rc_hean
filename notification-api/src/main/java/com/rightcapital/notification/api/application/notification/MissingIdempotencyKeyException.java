package com.rightcapital.notification.api.application.notification;

public class MissingIdempotencyKeyException extends RuntimeException {

    public MissingIdempotencyKeyException() {
        super("Idempotency-Key or requestId must be provided");
    }
}
