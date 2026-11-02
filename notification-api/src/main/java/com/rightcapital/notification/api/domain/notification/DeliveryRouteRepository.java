package com.rightcapital.notification.api.domain.notification;

import java.util.List;

public interface DeliveryRouteRepository {

    List<ActiveDeliveryRoute> findActiveByEventDefinitionId(long eventDefinitionId);
}
