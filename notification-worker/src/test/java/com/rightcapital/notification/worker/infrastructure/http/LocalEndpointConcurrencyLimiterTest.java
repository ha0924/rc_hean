package com.rightcapital.notification.worker.infrastructure.http;

import com.rightcapital.notification.worker.domain.DeliveryConfigurationException;
import com.rightcapital.notification.worker.domain.EndpointConcurrencyLimiter;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalEndpointConcurrencyLimiterTest {

    @Test
    void waitsForAnEndpointPermitBeforeAllowingAnotherDelivery() throws Exception {
        LocalEndpointConcurrencyLimiter limiter = new LocalEndpointConcurrencyLimiter();
        EndpointConcurrencyLimiter.Permit first = limiter.acquire(9L, 1);
        CompletableFuture<EndpointConcurrencyLimiter.Permit> second = CompletableFuture.supplyAsync(() -> {
            try {
                return limiter.acquire(9L, 1);
            } catch (InterruptedException exception) {
                throw new RuntimeException(exception);
            }
        });

        Thread.sleep(50);
        assertThat(second).isNotCompleted();
        first.close();
        EndpointConcurrencyLimiter.Permit secondPermit = second.get(1, TimeUnit.SECONDS);
        secondPermit.close();
    }

    @Test
    void rejectsAnInvalidRuntimeConcurrencyValue() {
        LocalEndpointConcurrencyLimiter limiter = new LocalEndpointConcurrencyLimiter();

        assertThatThrownBy(() -> limiter.acquire(9L, 0))
                .isInstanceOf(DeliveryConfigurationException.class);
    }
}
