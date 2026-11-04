package com.rightcapital.notification.worker.infrastructure.http;

import com.rightcapital.notification.worker.domain.EndpointConcurrencyLimiter;
import com.rightcapital.notification.worker.domain.DeliveryConfigurationException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies the configured endpoint capacity in one Worker JVM. A distributed limit needs a separate coordinator.
 */
@Component
class LocalEndpointConcurrencyLimiter implements EndpointConcurrencyLimiter {

    private final ConcurrentHashMap<Long, EndpointPermitPool> pools = new ConcurrentHashMap<>();

    @Override
    public Permit acquire(long endpointId, int maxConcurrency) throws InterruptedException {
        if (maxConcurrency < 1) {
            throw new DeliveryConfigurationException("Endpoint maxConcurrency must be positive");
        }
        EndpointPermitPool pool = pools.computeIfAbsent(endpointId, ignored -> new EndpointPermitPool());
        pool.acquire(maxConcurrency);
        return pool::release;
    }

    private static final class EndpointPermitPool {

        private int maxConcurrency;
        private int inUse;

        synchronized void acquire(int configuredMaxConcurrency) throws InterruptedException {
            maxConcurrency = configuredMaxConcurrency;
            while (inUse >= maxConcurrency) {
                wait();
            }
            inUse++;
        }

        synchronized void release() {
            inUse--;
            notifyAll();
        }
    }
}
