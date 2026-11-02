package com.rightcapital.notification.api.application.notification;

public class NotificationAuthenticationException extends RuntimeException {

    public NotificationAuthenticationException() {
        super("Invalid access key or secret");
    }
}
