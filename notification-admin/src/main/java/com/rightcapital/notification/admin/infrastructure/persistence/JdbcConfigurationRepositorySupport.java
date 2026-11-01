package com.rightcapital.notification.admin.infrastructure.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.util.Objects;

abstract class JdbcConfigurationRepositorySupport {

    protected final NamedParameterJdbcTemplate jdbc;

    JdbcConfigurationRepositorySupport(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    protected long insertAndReturnId(String sql, MapSqlParameterSource parameters) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, parameters, keyHolder, new String[]{"id"});
        return Objects.requireNonNull(keyHolder.getKey(), "generated id must not be null").longValue();
    }
}
