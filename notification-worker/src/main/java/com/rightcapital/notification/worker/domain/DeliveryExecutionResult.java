package com.rightcapital.notification.worker.domain;

/** The acknowledgement action that the RocketMQ listener must take. */
public enum DeliveryExecutionResult {
    ACKNOWLEDGE,
    RETRY
}
