CREATE TABLE notification (
    id CHAR(36) NOT NULL,
    application_id BIGINT UNSIGNED NOT NULL,
    event_definition_id BIGINT UNSIGNED NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    mq_message_id VARCHAR(128) NULL,
    trace_id VARCHAR(128) NOT NULL,
    accepted_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_app_idempotency (application_id, idempotency_key),
    KEY idx_notification_app_created (application_id, created_at),
    KEY idx_notification_status_created (status, created_at),
    KEY idx_notification_trace (trace_id),
    CONSTRAINT fk_notification_application
        FOREIGN KEY (application_id) REFERENCES business_application (id),
    CONSTRAINT fk_notification_event
        FOREIGN KEY (event_definition_id) REFERENCES event_definition (id),
    CONSTRAINT chk_notification_status
        CHECK (status IN ('ACCEPTED', 'PROCESSING', 'SUCCEEDED', 'FAILED'))
) ENGINE = InnoDB;

CREATE TABLE delivery_task (
    id CHAR(36) NOT NULL,
    notification_id CHAR(36) NOT NULL,
    route_id BIGINT UNSIGNED NOT NULL,
    endpoint_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    locked_by VARCHAR(128) NULL,
    lock_token VARCHAR(128) NULL,
    lock_until DATETIME(3) NULL,
    first_attempt_at DATETIME(3) NULL,
    last_attempt_at DATETIME(3) NULL,
    last_http_status INT NULL,
    last_duration_ms BIGINT UNSIGNED NULL,
    last_error_type VARCHAR(64) NULL,
    last_error_message VARCHAR(1024) NULL,
    last_response_excerpt VARCHAR(2048) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_delivery_task_notification_route (notification_id, route_id),
    KEY idx_delivery_task_notification (notification_id),
    KEY idx_delivery_task_status_updated (status, updated_at),
    KEY idx_delivery_task_endpoint_status (endpoint_id, status),
    KEY idx_delivery_task_lock_until (lock_until),
    CONSTRAINT fk_delivery_task_notification
        FOREIGN KEY (notification_id) REFERENCES notification (id),
    CONSTRAINT fk_delivery_task_route
        FOREIGN KEY (route_id) REFERENCES notification_route (id),
    CONSTRAINT fk_delivery_task_endpoint
        FOREIGN KEY (endpoint_id) REFERENCES external_endpoint (id),
    CONSTRAINT chk_delivery_task_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_delivery_task_http_status
        CHECK (last_http_status IS NULL OR last_http_status BETWEEN 100 AND 599)
) ENGINE = InnoDB;
