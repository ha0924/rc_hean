package com.rightcapital.notification.api.application.notification;

public class NotificationAcceptanceException extends RuntimeException {

    public NotificationAcceptanceException() {
        super("Notification acceptance could not be confirmed; retry with the same idempotency key");
    }
}
