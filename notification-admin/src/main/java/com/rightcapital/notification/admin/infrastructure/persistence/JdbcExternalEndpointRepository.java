package com.rightcapital.notification.admin.infrastructure.persistence;

import com.rightcapital.notification.admin.domain.configuration.ConfigurationStatus;
import com.rightcapital.notification.admin.domain.configuration.EndpointAuthType;
import com.rightcapital.notification.admin.domain.configuration.EndpointHttpMethod;
import com.rightcapital.notification.admin.domain.configuration.ExternalEndpoint;
import com.rightcapital.notification.admin.domain.configuration.ExternalEndpointRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
class JdbcExternalEndpointRepository extends JdbcConfigurationRepositorySupport
        implements ExternalEndpointRepository {

    private static final String COLUMNS = """
            id, code, name, http_method, url_template, auth_type, auth_config, default_headers,
            timeout_ms, max_concurrency, success_rule, retryable_rule, status, created_at, updated_at
            """;

    private static final RowMapper<ExternalEndpoint> ROW_MAPPER = JdbcExternalEndpointRepository::mapRow;

    JdbcExternalEndpointRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public ExternalEndpoint insert(ExternalEndpoint entity) {
        long id = insertAndReturnId("""
                INSERT INTO external_endpoint (
                    code, name, http_method, url_template, auth_type, auth_config, default_headers,
                    timeout_ms, max_concurrency, success_rule, retryable_rule, status
                ) VALUES (
                    :code, :name, :httpMethod, :urlTemplate, :authType, :authConfig, :defaultHeaders,
                    :timeoutMs, :maxConcurrency, :successRule, :retryableRule, :status
                )
                """, parameters(entity));
        return findById(id).orElseThrow();
    }

    @Override
    public ExternalEndpoint update(ExternalEndpoint entity) {
        int updated = jdbc.update("""
                UPDATE external_endpoint
                SET code = :code,
                    name = :name,
                    http_method = :httpMethod,
                    url_template = :urlTemplate,
                    auth_type = :authType,
                    auth_config = :authConfig,
                    default_headers = :defaultHeaders,
                    timeout_ms = :timeoutMs,
                    max_concurrency = :maxConcurrency,
                    success_rule = :successRule,
                    retryable_rule = :retryableRule,
                    status = :status
                WHERE id = :id
                """, parameters(entity));
        if (updated == 0) {
            return null;
        }
        return findById(entity.id()).orElseThrow();
    }

    @Override
    public Optional<ExternalEndpoint> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM external_endpoint WHERE id = :id",
                new MapSqlParameterSource("id", id), ROW_MAPPER).stream().findFirst();
    }

    @Override
    public List<ExternalEndpoint> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM external_endpoint ORDER BY id", ROW_MAPPER);
    }

    @Override
    public boolean existsById(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM external_endpoint WHERE id = :id)",
                new MapSqlParameterSource("id", id),
                Boolean.class
        ));
    }

    @Override
    public boolean deleteById(long id) {
        return jdbc.update("DELETE FROM external_endpoint WHERE id = :id", new MapSqlParameterSource("id", id)) == 1;
    }

    private MapSqlParameterSource parameters(ExternalEndpoint entity) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("code", entity.code())
                .addValue("name", entity.name())
                .addValue("httpMethod", entity.httpMethod().name())
                .addValue("urlTemplate", entity.urlTemplate())
                .addValue("authType", entity.authType().name())
                .addValue("authConfig", entity.authConfig())
                .addValue("defaultHeaders", entity.defaultHeaders())
                .addValue("timeoutMs", entity.timeoutMs())
                .addValue("maxConcurrency", entity.maxConcurrency())
                .addValue("successRule", entity.successRule())
                .addValue("retryableRule", entity.retryableRule())
                .addValue("status", entity.status().name());
        if (entity.id() != null) {
            parameters.addValue("id", entity.id());
        }
        return parameters;
    }

    private static ExternalEndpoint mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new ExternalEndpoint(
                resultSet.getLong("id"),
                resultSet.getString("code"),
                resultSet.getString("name"),
                EndpointHttpMethod.valueOf(resultSet.getString("http_method")),
                resultSet.getString("url_template"),
                EndpointAuthType.valueOf(resultSet.getString("auth_type")),
                resultSet.getString("auth_config"),
                resultSet.getString("default_headers"),
                resultSet.getInt("timeout_ms"),
                resultSet.getInt("max_concurrency"),
                resultSet.getString("success_rule"),
                resultSet.getString("retryable_rule"),
                ConfigurationStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("created_at", java.time.LocalDateTime.class),
                resultSet.getObject("updated_at", java.time.LocalDateTime.class)
        );
    }
}
