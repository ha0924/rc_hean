package com.rightcapital.notification.api.infrastructure.persistence;

import com.rightcapital.notification.api.domain.notification.ConfiguredEvent;
import com.rightcapital.notification.api.domain.notification.EventConfigurationRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class JdbcEventConfigurationRepository implements EventConfigurationRepository {

    private static final RowMapper<ConfiguredEvent> ROW_MAPPER = (resultSet, rowNum) -> new ConfiguredEvent(
            resultSet.getLong("id"),
            resultSet.getString("event_code")
    );

    private final NamedParameterJdbcTemplate jdbc;

    JdbcEventConfigurationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ConfiguredEvent> findActiveByApplicationIdAndEventCode(long applicationId, String eventCode) {
        return jdbc.query("""
                SELECT id, event_code
                FROM event_definition
                WHERE application_id = :applicationId
                  AND event_code = :eventCode
                  AND status = 'ACTIVE'
                """, new MapSqlParameterSource()
                .addValue("applicationId", applicationId)
                .addValue("eventCode", eventCode), ROW_MAPPER).stream().findFirst();
    }
}
