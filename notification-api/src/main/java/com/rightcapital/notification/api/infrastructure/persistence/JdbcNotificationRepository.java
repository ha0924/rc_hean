package com.rightcapital.notification.api.infrastructure.persistence;

import com.rightcapital.notification.api.domain.notification.NotificationRecord;
import com.rightcapital.notification.api.domain.notification.NotificationRepository;
import com.rightcapital.notification.api.domain.notification.NotificationTransactionCommand;
import com.rightcapital.notification.common.enums.DeliveryTaskStatus;
import com.rightcapital.notification.common.enums.NotificationStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class JdbcNotificationRepository implements NotificationRepository {

    private static final RowMapper<NotificationRecord> NOTIFICATION_ROW_MAPPER = (resultSet, rowNum) -> new NotificationRecord(
            resultSet.getString("id"),
            resultSet.getLong("application_id"),
            resultSet.getLong("event_definition_id"),
            resultSet.getString("idempotency_key"),
            resultSet.getString("payload_hash"),
            NotificationStatus.valueOf(resultSet.getString("status"))
    );

    private final NamedParameterJdbcTemplate jdbc;

    JdbcNotificationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<NotificationRecord> findByApplicationIdAndIdempotencyKey(long applicationId, String idempotencyKey) {
        return jdbc.query("""
                SELECT id, application_id, event_definition_id, idempotency_key, payload_hash, status
                FROM notification
                WHERE application_id = :applicationId
                  AND idempotency_key = :idempotencyKey
                """, new MapSqlParameterSource()
                .addValue("applicationId", applicationId)
                .addValue("idempotencyKey", idempotencyKey), NOTIFICATION_ROW_MAPPER).stream().findFirst();
    }

    @Override
    public void insert(NotificationTransactionCommand command) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("notificationId", command.notificationId())
                .addValue("applicationId", command.applicationId())
                .addValue("eventDefinitionId", command.eventDefinitionId())
                .addValue("idempotencyKey", command.idempotencyKey())
                .addValue("payload", command.payload())
                .addValue("payloadHash", command.payloadHash())
                .addValue("notificationStatus", NotificationStatus.ACCEPTED.name())
                .addValue("traceId", command.traceId())
                .addValue("taskId", command.taskId())
                .addValue("routeId", command.routeId())
                .addValue("endpointId", command.endpointId())
                .addValue("taskStatus", DeliveryTaskStatus.PENDING.name());

        jdbc.update("""
                INSERT INTO notification (
                    id, application_id, event_definition_id, idempotency_key, payload, payload_hash,
                    status, trace_id, accepted_at
                ) VALUES (
                    :notificationId, :applicationId, :eventDefinitionId, :idempotencyKey, :payload, :payloadHash,
                    :notificationStatus, :traceId, CURRENT_TIMESTAMP(3)
                )
                """, parameters);

        jdbc.update("""
                INSERT INTO delivery_task (id, notification_id, route_id, endpoint_id, status)
                VALUES (:taskId, :notificationId, :routeId, :endpointId, :taskStatus)
                """, parameters);
    }

    @Override
    public boolean deliveryTaskExists(String taskId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM delivery_task WHERE id = :taskId)
                """, new MapSqlParameterSource("taskId", taskId), Boolean.class));
    }
}
