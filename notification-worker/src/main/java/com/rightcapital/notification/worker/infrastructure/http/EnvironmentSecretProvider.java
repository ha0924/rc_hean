package com.rightcapital.notification.worker.infrastructure.http;

import com.rightcapital.notification.worker.domain.DeliveryConfigurationException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Development provider; production deployments can replace it with a Vault or KMS-backed implementation. */
@Component
class EnvironmentSecretProvider implements SecretProvider {

    private final Environment environment;

    EnvironmentSecretProvider(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String resolve(String secretRef) {
        if (secretRef == null || !secretRef.startsWith("env:") || secretRef.length() == 4) {
            throw new DeliveryConfigurationException("secretRef must use the env:NAME format");
        }
        String secret = environment.getProperty(secretRef.substring(4));
        if (secret == null || secret.isBlank()) {
            throw new DeliveryConfigurationException("Endpoint credential reference could not be resolved");
        }
        return secret;
    }
}
