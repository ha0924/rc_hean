package com.rightcapital.notification.admin.infrastructure.persistence;

import com.rightcapital.notification.admin.domain.configuration.ConfigurationStatus;
import com.rightcapital.notification.admin.domain.configuration.EventDefinition;
import com.rightcapital.notification.admin.domain.configuration.EventDefinitionRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
class JdbcEventDefinitionRepository extends JdbcConfigurationRepositorySupport
        implements EventDefinitionRepository {

    private static final String COLUMNS = """
            id, application_id, event_code, name, payload_schema, status, created_at, updated_at
            """;

    private static final RowMapper<EventDefinition> ROW_MAPPER = JdbcEventDefinitionRepository::mapRow;

    JdbcEventDefinitionRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public EventDefinition insert(EventDefinition entity) {
        long id = insertAndReturnId("""
                INSERT INTO event_definition (application_id, event_code, name, payload_schema, status)
                VALUES (:applicationId, :eventCode, :name, :payloadSchema, :status)
                """, parameters(entity));
        return findById(id).orElseThrow();
    }

    @Override
    public EventDefinition update(EventDefinition entity) {
        int updated = jdbc.update("""
                UPDATE event_definition
                SET application_id = :applicationId,
                    event_code = :eventCode,
                    name = :name,
                    payload_schema = :payloadSchema,
                    status = :status
                WHERE id = :id
                """, parameters(entity));
        if (updated == 0) {
            return null;
        }
        return findById(entity.id()).orElseThrow();
    }

    @Override
    public Optional<EventDefinition> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM event_definition WHERE id = :id",
                new MapSqlParameterSource("id", id), ROW_MAPPER).stream().findFirst();
    }

    @Override
    public List<EventDefinition> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM event_definition ORDER BY id", ROW_MAPPER);
    }

    @Override
    public boolean existsById(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM event_definition WHERE id = :id)",
                new MapSqlParameterSource("id", id),
                Boolean.class
        ));
    }

    @Override
    public boolean deleteById(long id) {
        return jdbc.update("DELETE FROM event_definition WHERE id = :id", new MapSqlParameterSource("id", id)) == 1;
    }

    private MapSqlParameterSource parameters(EventDefinition entity) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("applicationId", entity.applicationId())
                .addValue("eventCode", entity.eventCode())
                .addValue("name", entity.name())
                .addValue("payloadSchema", entity.payloadSchema())
                .addValue("status", entity.status().name());
        if (entity.id() != null) {
            parameters.addValue("id", entity.id());
        }
        return parameters;
    }

    private static EventDefinition mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new EventDefinition(
                resultSet.getLong("id"),
                resultSet.getLong("application_id"),
                resultSet.getString("event_code"),
                resultSet.getString("name"),
                resultSet.getString("payload_schema"),
                ConfigurationStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("created_at", java.time.LocalDateTime.class),
                resultSet.getObject("updated_at", java.time.LocalDateTime.class)
        );
    }
}
