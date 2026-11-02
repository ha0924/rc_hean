package com.rightcapital.notification.api.infrastructure.persistence;

import com.rightcapital.notification.api.application.notification.LocalNotificationTransactionService;
import com.rightcapital.notification.api.domain.notification.NotificationTransactionCommand;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class LocalNotificationTransactionIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("notification_platform")
            .withUsername("notification")
            .withPassword("notification");

    private NamedParameterJdbcTemplate jdbc;
    private LocalNotificationTransactionService transactionService;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDirectory())
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDirectory())
                .cleanDisabled(true)
                .load()
                .migrate();

        jdbc = new NamedParameterJdbcTemplate(dataSource);
        insertConfiguration();
        transactionService = new LocalNotificationTransactionService(
                new JdbcNotificationRepository(jdbc), new DataSourceTransactionManager(dataSource)
        );
    }

    @Test
    void atomicallyCreatesNotificationAndPendingDeliveryTask() {
        NotificationTransactionCommand command = new NotificationTransactionCommand(
                "notification-1",
                "task-1",
                1L,
                2L,
                4L,
                3L,
                "client:payment-1",
                "{\"contactId\":\"C-1\"}",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "trace-1"
        );

        transactionService.persist(command);

        assertThat(transactionService.isCommitted("task-1")).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT status
                FROM notification
                WHERE id = :notificationId
                """, new MapSqlParameterSource("notificationId", "notification-1"), String.class))
                .isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject("""
                SELECT status
                FROM delivery_task
                WHERE id = :taskId
                """, new MapSqlParameterSource("taskId", "task-1"), String.class))
                .isEqualTo("PENDING");
    }

    private void insertConfiguration() {
        jdbc.update("""
                INSERT INTO business_application (id, code, name, access_key, secret_hash, status)
                VALUES (1, 'subscription-service', 'Subscription Service', 'subscription-access-key', '$2a$10$hash', 'ACTIVE')
                """, new MapSqlParameterSource());
        jdbc.update("""
                INSERT INTO event_definition (id, application_id, event_code, name, payload_schema, status)
                VALUES (2, 1, 'subscription.payment_succeeded', 'Payment succeeded', :payloadSchema, 'ACTIVE')
                """, new MapSqlParameterSource("payloadSchema", "{}"));
        jdbc.update("""
                INSERT INTO external_endpoint (
                    id, code, name, http_method, url_template, auth_type, timeout_ms, max_concurrency,
                    success_rule, retryable_rule, status
                ) VALUES (
                    3, 'crm-update-contact', 'CRM Update Contact', 'POST', 'https://crm.example.com/contacts', 'NONE',
                    5000, 8, :successRule, :retryableRule, 'ACTIVE'
                )
                """, new MapSqlParameterSource()
                .addValue("successRule", "{\"statuses\":[200]}")
                .addValue("retryableRule", "{\"statuses\":[500]}"));
        jdbc.update("""
                INSERT INTO notification_route (id, event_definition_id, external_endpoint_id, request_mapping, status)
                VALUES (4, 2, 3, :requestMapping, 'ACTIVE')
                """, new MapSqlParameterSource("requestMapping", "{}"));
    }

    private String migrationDirectory() {
        return Path.of("..", "notification-admin", "src", "main", "resources", "db", "migration")
                .toAbsolutePath()
                .normalize()
                .toString();
    }
}
