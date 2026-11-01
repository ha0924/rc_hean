package com.rightcapital.notification.admin.controller.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ConfigurationCrudIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("notification_platform")
            .withUsername("notification")
            .withPassword("notification");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    void managesConfigurationAndProtectsSensitiveValues() throws Exception {
        JsonNode application = postJson("/api/admin/business-applications", """
                {
                  "code": "subscription-service",
                  "name": "Subscription Service",
                  "accessKey": "subscription-access-key",
                  "secret": "subscription-secret-v1",
                  "status": "ACTIVE"
                }
                """);
        long applicationId = application.path("id").asLong();
        assertThat(application.path("secretHash").isMissingNode()).isTrue();
        String secretHash = jdbcTemplate.queryForObject(
                "SELECT secret_hash FROM business_application WHERE id = ?", String.class, applicationId
        );
        assertThat(secretHash).startsWith("$2").isNotEqualTo("subscription-secret-v1");

        JsonNode eventDefinition = postJson("/api/admin/event-definitions", """
                {
                  "applicationId": %d,
                  "eventCode": "subscription.payment_succeeded",
                  "name": "Subscription payment succeeded",
                  "payloadSchema": {"type":"object","required":["contactId"]},
                  "status": "ACTIVE"
                }
                """.formatted(applicationId));
        long eventDefinitionId = eventDefinition.path("id").asLong();

        JsonNode endpoint = postJson("/api/admin/external-endpoints", """
                {
                  "code": "crm-update-contact",
                  "name": "CRM Update Contact",
                  "httpMethod": "POST",
                  "urlTemplate": "https://crm.example.com/contacts/{contactId}",
                  "authType": "BEARER",
                  "authConfig": "vault://crm/token",
                  "defaultHeaders": {"X-Source":"subscription"},
                  "timeoutMs": 5000,
                  "maxConcurrency": 8,
                  "successRule": {"statuses":[200,204]},
                  "retryableRule": {"statuses":[408,429,500,502,503,504]},
                  "status": "ACTIVE"
                }
                """);
        long endpointId = endpoint.path("id").asLong();
        assertThat(endpoint.path("authConfig").isMissingNode()).isTrue();
        assertThat(endpoint.path("authConfigured").asBoolean()).isTrue();

        JsonNode route = postJson("/api/admin/notification-routes", """
                {
                  "eventDefinitionId": %d,
                  "externalEndpointId": %d,
                  "requestMapping": {"body":{"contactId":"$.contactId"}},
                  "status": "ACTIVE"
                }
                """.formatted(eventDefinitionId, endpointId));

        mockMvc.perform(get("/api/admin/notification-routes/{id}", route.path("id").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestMapping.body.contactId").value("$.contactId"));

        mockMvc.perform(put("/api/admin/business-applications/{id}", applicationId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "subscription-service",
                                  "name": "Subscription Service v2",
                                  "accessKey": "subscription-access-key",
                                  "secret": "subscription-secret-v2",
                                  "status": "DISABLED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Subscription Service v2"))
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(delete("/api/admin/business-applications/{id}", applicationId))
                .andExpect(status().isConflict());

        JsonNode disposableEndpoint = postJson("/api/admin/external-endpoints", """
                {
                  "code": "disposable-endpoint",
                  "name": "Disposable Endpoint",
                  "httpMethod": "POST",
                  "urlTemplate": "https://example.com/webhook",
                  "authType": "NONE",
                  "timeoutMs": 1000,
                  "maxConcurrency": 1,
                  "successRule": {"statuses":[200]},
                  "retryableRule": {"statuses":[500]},
                  "status": "DISABLED"
                }
                """);
        long disposableEndpointId = disposableEndpoint.path("id").asLong();

        mockMvc.perform(delete("/api/admin/external-endpoints/{id}", disposableEndpointId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/external-endpoints/{id}", disposableEndpointId))
                .andExpect(status().isNotFound());
    }

    private JsonNode postJson(String path, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
