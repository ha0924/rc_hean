package com.rightcapital.notification.worker.infrastructure.persistence;

import com.rightcapital.notification.worker.domain.DeliveryTaskDetail;
import com.rightcapital.notification.worker.domain.HttpDeliveryResult;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class JdbcDeliveryTaskRepositoryIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("notification_platform")
            .withUsername("notification")
            .withPassword("notification");

    private NamedParameterJdbcTemplate jdbc;
    private JdbcDeliveryTaskRepository repository;

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
        insertTask();
        repository = new JdbcDeliveryTaskRepository(jdbc);
    }

    @Test
    void claimsPendingTaskOnlyOnceAndPersistsSuccessForBothTaskAndNotification() {
        DeliveryTaskDetail detail = repository.findByTaskId("task-1").orElseThrow();

        assertThat(detail.payload()).isEqualTo("{\"amount\": 100}");
        assertThat(detail.urlTemplate()).isEqualTo("https://crm.example.com/contacts");
        assertThat(repository.tryClaim("task-1", "worker-1", "lock-1", LocalDateTime.now().plusMinutes(1))).isTrue();
        assertThat(repository.tryClaim("task-1", "worker-2", "lock-2", LocalDateTime.now().plusMinutes(1))).isFalse();

        assertThat(repository.markSucceeded("task-1", "lock-1", HttpDeliveryResult.response(202, 12L, "accepted")))
                .isTrue();
        assertThat(value("SELECT status FROM delivery_task WHERE id = 'task-1'")).isEqualTo("SUCCEEDED");
        assertThat(value("SELECT status FROM notification WHERE id = 'notification-1'")).isEqualTo("SUCCEEDED");
        assertThat(value("SELECT attempt_count FROM delivery_task WHERE id = 'task-1'")).isEqualTo("1");
        assertThat(value("SELECT last_http_status FROM delivery_task WHERE id = 'task-1'")).isEqualTo("202");
    }

    @Test
    void permitsRecoveryOfExpiredLockAndRecordsRetryableFailure() {
        jdbc.update("""
                UPDATE delivery_task
                SET status = 'PROCESSING', lock_token = 'expired-lock', locked_by = 'crashed-worker',
                    lock_until = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 SECOND)
                WHERE id = 'task-1'
                """, new MapSqlParameterSource());

        assertThat(repository.tryClaim("task-1", "worker-2", "lock-2", LocalDateTime.now().plusMinutes(1))).isTrue();
        assertThat(repository.markRetryWait("task-1", "lock-2", HttpDeliveryResult.response(503, 20L, "unavailable")))
                .isTrue();
        assertThat(value("SELECT status FROM delivery_task WHERE id = 'task-1'")).isEqualTo("RETRY_WAIT");
        assertThat(value("SELECT lock_token FROM delivery_task WHERE id = 'task-1'")).isNull();
        assertThat(value("SELECT status FROM notification WHERE id = 'notification-1'")).isEqualTo("PROCESSING");
    }

    @Test
    void marksRetryExhaustedTaskAsFailedFromDeadLetterQueue() {
        jdbc.update("UPDATE delivery_task SET status = 'RETRY_WAIT' WHERE id = 'task-1'", new MapSqlParameterSource());

        repository.markFailedFromDeadLetter("task-1", "RocketMQ retry limit exceeded");

        assertThat(value("SELECT status FROM delivery_task WHERE id = 'task-1'")).isEqualTo("FAILED");
        assertThat(value("SELECT last_error_type FROM delivery_task WHERE id = 'task-1'")).isEqualTo("RETRY_EXHAUSTED");
        assertThat(value("SELECT status FROM notification WHERE id = 'notification-1'")).isEqualTo("FAILED");
    }

    private void insertTask() {
        jdbc.update("""
                INSERT INTO business_application (id, code, name, access_key, secret_hash, status)
                VALUES (1, 'subscription-service', 'Subscription Service', 'subscription-access-key', '$2a$10$hash', 'ACTIVE')
                """, new MapSqlParameterSource());
        jdbc.update("""
                INSERT INTO event_definition (id, application_id, event_code, name, payload_schema, status)
                VALUES (2, 1, 'subscription.payment_succeeded', 'Payment succeeded', '{}', 'ACTIVE')
                """, new MapSqlParameterSource());
        jdbc.update("""
                INSERT INTO external_endpoint (
                    id, code, name, http_method, url_template, auth_type, default_headers, timeout_ms, max_concurrency,
                    success_rule, retryable_rule, status
                ) VALUES (
                    3, 'crm-update-contact', 'CRM Update Contact', 'POST', 'https://crm.example.com/contacts', 'NONE',
                    :defaultHeaders, 5000, 8, :successRule, :retryableRule, 'ACTIVE'
                )
                """, new MapSqlParameterSource()
                .addValue("defaultHeaders", "{\"Accept\":\"application/json\"}")
                .addValue("successRule", "{\"statuses\":[200]}")
                .addValue("retryableRule", "{\"statuses\":[500]}"));
        jdbc.update("""
                INSERT INTO notification_route (id, event_definition_id, external_endpoint_id, request_mapping, status)
                VALUES (4, 2, 3, '{}', 'ACTIVE')
                """, new MapSqlParameterSource());
        jdbc.update("""
                INSERT INTO notification (
                    id, application_id, event_definition_id, idempotency_key, payload, payload_hash, status, trace_id, accepted_at
                ) VALUES (
                    'notification-1', 1, 2, 'client:payment-1', :payload,
                    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'ACCEPTED', 'trace-1', CURRENT_TIMESTAMP(3)
                )
                """, new MapSqlParameterSource("payload", "{\"amount\": 100}"));
        jdbc.update("""
                INSERT INTO delivery_task (id, notification_id, route_id, endpoint_id, status)
                VALUES ('task-1', 'notification-1', 4, 3, 'PENDING')
                """, new MapSqlParameterSource());
    }

    private String value(String sql) {
        return jdbc.queryForObject(sql, new MapSqlParameterSource(), String.class);
    }

    private String migrationDirectory() {
        return Path.of("..", "notification-admin", "src", "main", "resources", "db", "migration")
                .toAbsolutePath()
                .normalize()
                .toString();
    }
}
