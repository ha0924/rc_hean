package com.rightcapital.notification.common.constants;

public final class MqConstants {

    public static final String DELIVERY_TOPIC = "notification-delivery";
    public static final String DELIVERY_TAG = "delivery-task";
    public static final String TRANSACTION_PRODUCER_GROUP = "notification-tx-producer";
    public static final String DELIVERY_CONSUMER_GROUP = "notification-delivery-worker";

    private MqConstants() {
    }
}
