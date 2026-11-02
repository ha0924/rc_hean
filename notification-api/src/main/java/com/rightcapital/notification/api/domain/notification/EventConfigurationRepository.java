package com.rightcapital.notification.api.domain.notification;

import java.util.Optional;

public interface EventConfigurationRepository {

    Optional<ConfiguredEvent> findActiveByApplicationIdAndEventCode(long applicationId, String eventCode);
}
