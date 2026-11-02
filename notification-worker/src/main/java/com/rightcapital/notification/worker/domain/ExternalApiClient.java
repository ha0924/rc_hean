package com.rightcapital.notification.worker.domain;

public interface ExternalApiClient {

    HttpDeliveryResult deliver(DeliveryTaskDetail task);
}
