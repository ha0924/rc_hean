package com.rightcapital.notification.worker.application;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** A stable-for-process identifier recorded while a task is leased by this worker instance. */
@Component
class WorkerIdentity {

    private final String value = "delivery-worker-" + UUID.randomUUID();

    String value() {
        return value;
    }
}
