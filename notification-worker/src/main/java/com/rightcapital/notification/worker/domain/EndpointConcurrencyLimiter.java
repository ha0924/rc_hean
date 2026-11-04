package com.rightcapital.notification.worker.domain;

/** Limits concurrent outbound calls to one endpoint inside a Worker process. */
public interface EndpointConcurrencyLimiter {

    Permit acquire(long endpointId, int maxConcurrency) throws InterruptedException;

    interface Permit extends AutoCloseable {

        @Override
        void close();
    }
}
