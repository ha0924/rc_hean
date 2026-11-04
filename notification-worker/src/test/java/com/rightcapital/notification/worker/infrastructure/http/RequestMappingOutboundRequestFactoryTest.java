package com.rightcapital.notification.worker.infrastructure.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightcapital.notification.worker.domain.DeliveryConfigurationException;
import com.rightcapital.notification.worker.domain.DeliveryTaskDetail;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestMappingOutboundRequestFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RequestMappingOutboundRequestFactory factory = new RequestMappingOutboundRequestFactory(
            objectMapper, new EndpointAuthenticationResolver(objectMapper, secretRef -> switch (secretRef) {
                case "env:CRM_TOKEN" -> "crm-token";
                case "env:CRM_KEY" -> "crm-key";
                case "env:CRM_PASSWORD" -> "crm-password";
                default -> throw new DeliveryConfigurationException("missing secret");
            }), allowedCrmPolicy()
    );

    private static EndpointUrlPolicy allowedCrmPolicy() {
        DeliverySecurityProperties properties = new DeliverySecurityProperties();
        properties.setAllowedEndpointHosts(Set.of("crm.example.com"));
        return new EndpointUrlPolicy(properties);
    }

    @Test
    void mapsBearerJsonRequestPathQueryAndHeaders() {
        OutboundHttpRequest request = factory.create(task("BEARER", "{\"secretRef\":\"env:CRM_TOKEN\"}", """
                {
                  "pathVariables":{"contactId":{"from":"/contact/id"}},
                  "query":{"source":{"value":"notification"}},
                  "headers":{"X-Plan":{"from":"/plan"}},
                  "body":{"type":"JSON","template":{"id":{"from":"/contact/id"},"amount":{"from":"/amount"}}}
                }
                """));

        assertThat(request.uri().toString()).isEqualTo("https://crm.example.com/contacts/C-100?source=notification");
        assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer crm-token");
        assertThat(request.headers().getFirst("X-Plan")).isEqualTo("gold");
        assertThat(request.headers().getFirst("Idempotency-Key")).isEqualTo("task-1");
        assertThat(request.contentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(request.body()).isEqualTo("{\"id\":\"C-100\",\"amount\":100}");
    }

    @Test
    void supportsFormXmlAndApiKeyQueryAuthentication() {
        OutboundHttpRequest form = factory.create(task("API_KEY", """
                {"secretRef":"env:CRM_KEY","in":"QUERY","name":"api_key"}
                """, """
                {"pathVariables":{"contactId":{"from":"/contact/id"}},"body":{"type":"FORM_URLENCODED","fields":{"id":{"from":"/contact/id"},"plan":{"from":"/plan"}}}}
                """));
        OutboundHttpRequest xml = factory.create(task("NONE", null, """
                {"pathVariables":{"contactId":{"from":"/contact/id"}},"body":{"type":"XML","root":"Contact","fields":{"Id":{"from":"/contact/id"},"Plan":{"from":"/plan"}}}}
                """));

        assertThat(form.uri().toString()).contains("api_key=crm-key");
        assertThat(form.contentType()).isEqualTo(new MediaType(MediaType.APPLICATION_FORM_URLENCODED, StandardCharsets.UTF_8));
        assertThat(form.body()).isEqualTo("id=C-100&plan=gold");
        assertThat(xml.contentType()).isEqualTo(new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8));
        assertThat(xml.body()).isEqualTo("<Contact><Id>C-100</Id><Plan>gold</Plan></Contact>");
    }

    @Test
    void supportsApiKeyHeaderAndBasicAuthentication() {
        OutboundHttpRequest apiKey = factory.create(task("API_KEY", """
                {"secretRef":"env:CRM_KEY","in":"HEADER","name":"X-API-Key"}
                """, """
                {"pathVariables":{"contactId":{"from":"/contact/id"}}}
                """));
        OutboundHttpRequest basic = factory.create(task("BASIC", """
                {"username":"crm-service","passwordSecretRef":"env:CRM_PASSWORD"}
                """, """
                {"pathVariables":{"contactId":{"from":"/contact/id"}}}
                """));

        assertThat(apiKey.headers().getFirst("X-API-Key")).isEqualTo("crm-key");
        assertThat(basic.headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Basic Y3JtLXNlcnZpY2U6Y3JtLXBhc3N3b3Jk");
    }

    @Test
    void failsBeforeHttpWhenRequiredMappedValueIsMissing() {
        DeliveryTaskDetail task = task("NONE", null, """
                {"headers":{"X-Customer":{"from":"/missing"}}}
                """);

        assertThatThrownBy(() -> factory.create(task))
                .isInstanceOf(DeliveryConfigurationException.class)
                .hasMessageContaining("required payload value is missing");
    }

    private DeliveryTaskDetail task(String authType, String authConfig, String requestMapping) {
        return new DeliveryTaskDetail(
                "task-1",
                "notification-1",
                3L,
                "{\"contact\":{\"id\":\"C-100\"},\"amount\":100,\"plan\":\"gold\"}",
                "trace-1",
                "POST",
                "https://crm.example.com/contacts/{contactId}",
                authType,
                authConfig,
                "{\"X-Source\":\"subscription\"}",
                5000,
                8,
                "{}",
                "{}",
                requestMapping
        );
    }
}
