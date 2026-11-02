package com.rightcapital.notification.worker.domain;

/** A non-retryable endpoint configuration error discovered before a request is sent. */
public class DeliveryConfigurationException extends RuntimeException {

    public DeliveryConfigurationException(String message) {
        super(message);
    }

    public DeliveryConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
