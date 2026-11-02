package com.rightcapital.notification.api.application.notification;

public class NotificationMessagingException extends RuntimeException {

    public NotificationMessagingException(Throwable cause) {
        super("RocketMQ transaction message could not be sent", cause);
    }
}
