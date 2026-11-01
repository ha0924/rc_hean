CREATE TABLE business_application (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    access_key VARCHAR(128) NOT NULL,
    secret_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_business_application_code (code),
    UNIQUE KEY uk_business_application_access_key (access_key),
    KEY idx_business_application_status (status),
    CONSTRAINT chk_business_application_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB;

CREATE TABLE event_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_id BIGINT UNSIGNED NOT NULL,
    event_code VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    payload_schema JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_definition_app_event (application_id, event_code),
    KEY idx_event_definition_app_status (application_id, status),
    CONSTRAINT fk_event_definition_application
        FOREIGN KEY (application_id) REFERENCES business_application (id),
    CONSTRAINT chk_event_definition_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB;

CREATE TABLE external_endpoint (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    http_method VARCHAR(16) NOT NULL,
    url_template VARCHAR(2048) NOT NULL,
    auth_type VARCHAR(32) NOT NULL,
    auth_config TEXT NULL,
    default_headers JSON NULL,
    timeout_ms INT UNSIGNED NOT NULL,
    max_concurrency INT UNSIGNED NOT NULL,
    success_rule JSON NOT NULL,
    retryable_rule JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_external_endpoint_code (code),
    KEY idx_external_endpoint_status (status),
    CONSTRAINT chk_external_endpoint_http_method
        CHECK (http_method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE')),
    CONSTRAINT chk_external_endpoint_auth_type
        CHECK (auth_type IN ('NONE', 'API_KEY', 'BEARER', 'BASIC')),
    CONSTRAINT chk_external_endpoint_timeout
        CHECK (timeout_ms > 0),
    CONSTRAINT chk_external_endpoint_concurrency
        CHECK (max_concurrency > 0),
    CONSTRAINT chk_external_endpoint_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB;

CREATE TABLE notification_route (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_definition_id BIGINT UNSIGNED NOT NULL,
    external_endpoint_id BIGINT UNSIGNED NOT NULL,
    request_mapping JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_route_event_endpoint (event_definition_id, external_endpoint_id),
    KEY idx_notification_route_event_status (event_definition_id, status),
    CONSTRAINT fk_notification_route_event
        FOREIGN KEY (event_definition_id) REFERENCES event_definition (id),
    CONSTRAINT fk_notification_route_endpoint
        FOREIGN KEY (external_endpoint_id) REFERENCES external_endpoint (id),
    CONSTRAINT chk_notification_route_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB;
