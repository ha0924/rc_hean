package com.rightcapital.notification.api.infrastructure.persistence;

import com.rightcapital.notification.api.domain.notification.AuthenticatedApplication;
import com.rightcapital.notification.api.domain.notification.BusinessApplicationAuthenticationRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class JdbcBusinessApplicationAuthenticationRepository implements BusinessApplicationAuthenticationRepository {

    private static final RowMapper<AuthenticatedApplication> ROW_MAPPER = (resultSet, rowNum) -> new AuthenticatedApplication(
            resultSet.getLong("id"),
            resultSet.getString("code"),
            resultSet.getString("secret_hash")
    );

    private final NamedParameterJdbcTemplate jdbc;

    JdbcBusinessApplicationAuthenticationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AuthenticatedApplication> findActiveByAccessKey(String accessKey) {
        return jdbc.query("""
                SELECT id, code, secret_hash
                FROM business_application
                WHERE access_key = :accessKey
                  AND status = 'ACTIVE'
                """, new MapSqlParameterSource("accessKey", accessKey), ROW_MAPPER).stream().findFirst();
    }
}
