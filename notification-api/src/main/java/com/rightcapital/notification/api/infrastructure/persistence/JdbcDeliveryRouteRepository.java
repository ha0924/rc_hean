package com.rightcapital.notification.api.infrastructure.persistence;

import com.rightcapital.notification.api.domain.notification.ActiveDeliveryRoute;
import com.rightcapital.notification.api.domain.notification.DeliveryRouteRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class JdbcDeliveryRouteRepository implements DeliveryRouteRepository {

    private static final RowMapper<ActiveDeliveryRoute> ROW_MAPPER = (resultSet, rowNum) -> new ActiveDeliveryRoute(
            resultSet.getLong("id"),
            resultSet.getLong("external_endpoint_id")
    );

    private final NamedParameterJdbcTemplate jdbc;

    JdbcDeliveryRouteRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ActiveDeliveryRoute> findActiveByEventDefinitionId(long eventDefinitionId) {
        return jdbc.query("""
                SELECT route.id, route.external_endpoint_id
                FROM notification_route route
                JOIN external_endpoint endpoint ON endpoint.id = route.external_endpoint_id
                WHERE route.event_definition_id = :eventDefinitionId
                  AND route.status = 'ACTIVE'
                  AND endpoint.status = 'ACTIVE'
                ORDER BY route.id
                """, new MapSqlParameterSource("eventDefinitionId", eventDefinitionId), ROW_MAPPER);
    }
}
