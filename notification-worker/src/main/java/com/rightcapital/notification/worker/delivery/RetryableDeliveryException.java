package com.rightcapital.notification.worker.delivery;

/** Throwing this exception from a RocketMQ listener requests broker-managed redelivery. */
class RetryableDeliveryException extends RuntimeException {

    RetryableDeliveryException(String message) {
        super(message);
    }
}
