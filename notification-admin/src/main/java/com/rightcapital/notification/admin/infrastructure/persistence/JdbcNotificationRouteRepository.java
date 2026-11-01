package com.rightcapital.notification.admin.infrastructure.persistence;

import com.rightcapital.notification.admin.domain.configuration.ConfigurationStatus;
import com.rightcapital.notification.admin.domain.configuration.NotificationRoute;
import com.rightcapital.notification.admin.domain.configuration.NotificationRouteRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
class JdbcNotificationRouteRepository extends JdbcConfigurationRepositorySupport
        implements NotificationRouteRepository {

    private static final String COLUMNS = """
            id, event_definition_id, external_endpoint_id, request_mapping, status, created_at, updated_at
            """;

    private static final RowMapper<NotificationRoute> ROW_MAPPER = JdbcNotificationRouteRepository::mapRow;

    JdbcNotificationRouteRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public NotificationRoute insert(NotificationRoute entity) {
        long id = insertAndReturnId("""
                INSERT INTO notification_route (event_definition_id, external_endpoint_id, request_mapping, status)
                VALUES (:eventDefinitionId, :externalEndpointId, :requestMapping, :status)
                """, parameters(entity));
        return findById(id).orElseThrow();
    }

    @Override
    public NotificationRoute update(NotificationRoute entity) {
        int updated = jdbc.update("""
                UPDATE notification_route
                SET event_definition_id = :eventDefinitionId,
                    external_endpoint_id = :externalEndpointId,
                    request_mapping = :requestMapping,
                    status = :status
                WHERE id = :id
                """, parameters(entity));
        if (updated == 0) {
            return null;
        }
        return findById(entity.id()).orElseThrow();
    }

    @Override
    public Optional<NotificationRoute> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM notification_route WHERE id = :id",
                new MapSqlParameterSource("id", id), ROW_MAPPER).stream().findFirst();
    }

    @Override
    public List<NotificationRoute> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM notification_route ORDER BY id", ROW_MAPPER);
    }

    @Override
    public boolean existsById(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM notification_route WHERE id = :id)",
                new MapSqlParameterSource("id", id),
                Boolean.class
        ));
    }

    @Override
    public boolean deleteById(long id) {
        return jdbc.update("DELETE FROM notification_route WHERE id = :id", new MapSqlParameterSource("id", id)) == 1;
    }

    private MapSqlParameterSource parameters(NotificationRoute entity) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("eventDefinitionId", entity.eventDefinitionId())
                .addValue("externalEndpointId", entity.externalEndpointId())
                .addValue("requestMapping", entity.requestMapping())
                .addValue("status", entity.status().name());
        if (entity.id() != null) {
            parameters.addValue("id", entity.id());
        }
        return parameters;
    }

    private static NotificationRoute mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new NotificationRoute(
                resultSet.getLong("id"),
                resultSet.getLong("event_definition_id"),
                resultSet.getLong("external_endpoint_id"),
                resultSet.getString("request_mapping"),
                ConfigurationStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("created_at", java.time.LocalDateTime.class),
                resultSet.getObject("updated_at", java.time.LocalDateTime.class)
        );
    }
}
