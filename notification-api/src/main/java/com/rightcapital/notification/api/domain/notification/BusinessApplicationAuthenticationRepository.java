package com.rightcapital.notification.api.domain.notification;

import java.util.Optional;

public interface BusinessApplicationAuthenticationRepository {

    Optional<AuthenticatedApplication> findActiveByAccessKey(String accessKey);
}
