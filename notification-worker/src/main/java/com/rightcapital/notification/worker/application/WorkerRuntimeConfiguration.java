package com.rightcapital.notification.worker.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.rightcapital.notification.worker.infrastructure.http.DeliverySecurityProperties;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(DeliverySecurityProperties.class)
class WorkerRuntimeConfiguration {

    @Bean
    Clock deliveryClock() {
        return Clock.systemUTC();
    }
}
