package com.rightcapital.notification.admin.domain.configuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationEntityTest {

    @Test
    void trimsRequiredBusinessApplicationFields() {
        BusinessApplication application = new BusinessApplication(
                null,
                " order-service ",
                " Order Service ",
                " access-key ",
                " secret-hash ",
                ConfigurationStatus.ACTIVE,
                null,
                null
        );

        assertThat(application.code()).isEqualTo("order-service");
        assertThat(application.name()).isEqualTo("Order Service");
    }

    @Test
    void rejectsEndpointWithoutPositiveConcurrency() {
        assertThatThrownBy(() -> endpoint(0, EndpointAuthType.NONE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxConcurrency must be positive");
    }

    @Test
    void requiresAuthenticationConfigurationWhenAuthenticationIsEnabled() {
        assertThatThrownBy(() -> endpoint(10, EndpointAuthType.BEARER, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("authConfig must not be blank");
    }

    @Test
    void defaultsMissingHeadersToEmptyJsonObject() {
        ExternalEndpoint endpoint = endpoint(10, EndpointAuthType.NONE, null);

        assertThat(endpoint.defaultHeaders()).isEqualTo("{}");
    }

    private ExternalEndpoint endpoint(
            int maxConcurrency,
            EndpointAuthType authType,
            String authConfig
    ) {
        return new ExternalEndpoint(
                null,
                "crm-update-contact",
                "CRM Update Contact",
                EndpointHttpMethod.POST,
                "https://crm.example.com/contacts/{contactId}",
                authType,
                authConfig,
                null,
                5_000,
                maxConcurrency,
                "{\"statuses\":[200,204]}",
                "{\"statuses\":[408,429,500,502,503,504]}",
                ConfigurationStatus.ACTIVE,
                null,
                null
        );
    }
}
