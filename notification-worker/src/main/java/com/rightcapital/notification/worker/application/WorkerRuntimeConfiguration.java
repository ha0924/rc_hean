package com.rightcapital.notification.worker.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class WorkerRuntimeConfiguration {

    @Bean
    Clock deliveryClock() {
        return Clock.systemUTC();
    }
}
