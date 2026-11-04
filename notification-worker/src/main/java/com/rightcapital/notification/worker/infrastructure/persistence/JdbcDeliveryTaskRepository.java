package com.rightcapital.notification.worker.infrastructure.persistence;

import com.rightcapital.notification.common.enums.DeliveryTaskStatus;
import com.rightcapital.notification.common.enums.NotificationStatus;
import com.rightcapital.notification.worker.domain.DeliveryTaskDetail;
import com.rightcapital.notification.worker.domain.DeliveryTaskRepository;
import com.rightcapital.notification.worker.domain.HttpDeliveryResult;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
class JdbcDeliveryTaskRepository implements DeliveryTaskRepository {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;
    private static final int MAX_RESPONSE_EXCERPT_LENGTH = 2048;

    private static final RowMapper<DeliveryTaskDetail> TASK_ROW_MAPPER = (resultSet, rowNum) -> new DeliveryTaskDetail(
            resultSet.getString("task_id"),
            resultSet.getString("notification_id"),
            resultSet.getLong("endpoint_id"),
            resultSet.getString("payload"),
            resultSet.getString("trace_id"),
            resultSet.getString("http_method"),
            resultSet.getString("url_template"),
            resultSet.getString("auth_type"),
            resultSet.getString("auth_config"),
            resultSet.getString("default_headers"),
            resultSet.getInt("timeout_ms"),
            resultSet.getInt("max_concurrency"),
            resultSet.getString("success_rule"),
            resultSet.getString("retryable_rule"),
            resultSet.getString("request_mapping")
    );

    private final NamedParameterJdbcTemplate jdbc;

    JdbcDeliveryTaskRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DeliveryTaskDetail> findByTaskId(String taskId) {
        return jdbc.query("""
                SELECT task.id AS task_id,
                       task.notification_id,
                       task.endpoint_id,
                       notification.payload,
                       notification.trace_id,
                       endpoint.http_method,
                       endpoint.url_template,
                       endpoint.auth_type,
                       endpoint.auth_config,
                       endpoint.default_headers,
                       endpoint.timeout_ms,
                       endpoint.max_concurrency,
                       endpoint.success_rule,
                       endpoint.retryable_rule,
                       route.request_mapping
                FROM delivery_task task
                JOIN notification notification ON notification.id = task.notification_id
                JOIN notification_route route ON route.id = task.route_id
                JOIN external_endpoint endpoint ON endpoint.id = task.endpoint_id
                WHERE task.id = :taskId
                """, new MapSqlParameterSource("taskId", taskId), TASK_ROW_MAPPER).stream().findFirst();
    }

    @Override
    public boolean tryClaim(String taskId, String workerId, String lockToken, LocalDateTime lockUntil) {
        return jdbc.update("""
                UPDATE delivery_task task
                JOIN notification notification ON notification.id = task.notification_id
                SET task.status = :processingStatus,
                    task.attempt_count = task.attempt_count + 1,
                    task.locked_by = :workerId,
                    task.lock_token = :lockToken,
                    task.lock_until = :lockUntil,
                    task.first_attempt_at = COALESCE(task.first_attempt_at, CURRENT_TIMESTAMP(3)),
                    task.last_attempt_at = CURRENT_TIMESTAMP(3),
                    notification.status = :notificationStatus,
                    notification.finished_at = NULL
                WHERE task.id = :taskId
                  AND (
                      task.status IN (:pendingStatus, :retryWaitStatus)
                      OR (task.status = :processingStatus AND task.lock_until < CURRENT_TIMESTAMP(3))
                  )
                """, new MapSqlParameterSource()
                .addValue("taskId", taskId)
                .addValue("workerId", workerId)
                .addValue("lockToken", lockToken)
                .addValue("lockUntil", lockUntil)
                .addValue("pendingStatus", DeliveryTaskStatus.PENDING.name())
                .addValue("retryWaitStatus", DeliveryTaskStatus.RETRY_WAIT.name())
                .addValue("processingStatus", DeliveryTaskStatus.PROCESSING.name())
                .addValue("notificationStatus", NotificationStatus.PROCESSING.name())) > 0;
    }

    @Override
    public boolean markSucceeded(String taskId, String lockToken, HttpDeliveryResult result) {
        return updateFinalStatus(taskId, lockToken, result, DeliveryTaskStatus.SUCCEEDED, NotificationStatus.SUCCEEDED);
    }

    @Override
    public boolean markRetryWait(String taskId, String lockToken, HttpDeliveryResult result) {
        return jdbc.update("""
                UPDATE delivery_task
                SET status = :retryWaitStatus,
                    lock_token = NULL,
                    locked_by = NULL,
                    lock_until = NULL,
                    last_http_status = :httpStatus,
                    last_duration_ms = :durationMs,
                    last_error_type = :errorType,
                    last_error_message = :errorMessage,
                    last_response_excerpt = :responseExcerpt
                WHERE id = :taskId
                  AND lock_token = :lockToken
                """, taskResultParameters(taskId, lockToken, result)
                .addValue("retryWaitStatus", DeliveryTaskStatus.RETRY_WAIT.name())) == 1;
    }

    @Override
    public boolean markFailed(String taskId, String lockToken, HttpDeliveryResult result) {
        return updateFinalStatus(taskId, lockToken, result, DeliveryTaskStatus.FAILED, NotificationStatus.FAILED);
    }

    @Override
    public void markFailedFromDeadLetter(String taskId, String reason) {
        jdbc.update("""
                UPDATE delivery_task task
                JOIN notification notification ON notification.id = task.notification_id
                SET task.status = :failedTaskStatus,
                    task.lock_token = NULL,
                    task.locked_by = NULL,
                    task.lock_until = NULL,
                    task.last_error_type = 'RETRY_EXHAUSTED',
                    task.last_error_message = :reason,
                    notification.status = :failedNotificationStatus,
                    notification.finished_at = CURRENT_TIMESTAMP(3)
                WHERE task.id = :taskId
                  AND task.status <> :succeededStatus
                """, new MapSqlParameterSource()
                .addValue("taskId", taskId)
                .addValue("reason", truncate(reason, MAX_ERROR_MESSAGE_LENGTH))
                .addValue("failedTaskStatus", DeliveryTaskStatus.FAILED.name())
                .addValue("succeededStatus", DeliveryTaskStatus.SUCCEEDED.name())
                .addValue("failedNotificationStatus", NotificationStatus.FAILED.name()));
    }

    private boolean updateFinalStatus(
            String taskId,
            String lockToken,
            HttpDeliveryResult result,
            DeliveryTaskStatus taskStatus,
            NotificationStatus notificationStatus
    ) {
        return jdbc.update("""
                UPDATE delivery_task task
                JOIN notification notification ON notification.id = task.notification_id
                SET task.status = :taskStatus,
                    task.lock_token = NULL,
                    task.locked_by = NULL,
                    task.lock_until = NULL,
                    task.last_http_status = :httpStatus,
                    task.last_duration_ms = :durationMs,
                    task.last_error_type = :errorType,
                    task.last_error_message = :errorMessage,
                    task.last_response_excerpt = :responseExcerpt,
                    task.finished_at = CURRENT_TIMESTAMP(3),
                    notification.status = :notificationStatus,
                    notification.finished_at = CURRENT_TIMESTAMP(3)
                WHERE task.id = :taskId
                  AND task.lock_token = :lockToken
                """, taskResultParameters(taskId, lockToken, result)
                .addValue("taskStatus", taskStatus.name())
                .addValue("notificationStatus", notificationStatus.name())) > 0;
    }

    private MapSqlParameterSource taskResultParameters(String taskId, String lockToken, HttpDeliveryResult result) {
        return new MapSqlParameterSource()
                .addValue("taskId", taskId)
                .addValue("lockToken", lockToken)
                .addValue("httpStatus", result.httpStatus())
                .addValue("durationMs", result.durationMs())
                .addValue("errorType", truncate(result.errorType(), 64))
                .addValue("errorMessage", truncate(result.errorMessage(), MAX_ERROR_MESSAGE_LENGTH))
                .addValue("responseExcerpt", truncate(result.responseExcerpt(), MAX_RESPONSE_EXCERPT_LENGTH));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
