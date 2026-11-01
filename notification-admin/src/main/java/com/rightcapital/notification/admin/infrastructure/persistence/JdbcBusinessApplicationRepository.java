package com.rightcapital.notification.admin.infrastructure.persistence;

import com.rightcapital.notification.admin.domain.configuration.BusinessApplication;
import com.rightcapital.notification.admin.domain.configuration.BusinessApplicationRepository;
import com.rightcapital.notification.admin.domain.configuration.ConfigurationStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
class JdbcBusinessApplicationRepository extends JdbcConfigurationRepositorySupport
        implements BusinessApplicationRepository {

    private static final String COLUMNS = """
            id, code, name, access_key, secret_hash, status, created_at, updated_at
            """;

    private static final RowMapper<BusinessApplication> ROW_MAPPER = JdbcBusinessApplicationRepository::mapRow;

    JdbcBusinessApplicationRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public BusinessApplication insert(BusinessApplication entity) {
        long id = insertAndReturnId("""
                INSERT INTO business_application (code, name, access_key, secret_hash, status)
                VALUES (:code, :name, :accessKey, :secretHash, :status)
                """, parameters(entity));
        return findById(id).orElseThrow();
    }

    @Override
    public BusinessApplication update(BusinessApplication entity) {
        int updated = jdbc.update("""
                UPDATE business_application
                SET code = :code,
                    name = :name,
                    access_key = :accessKey,
                    secret_hash = :secretHash,
                    status = :status
                WHERE id = :id
                """, parameters(entity));
        if (updated == 0) {
            return null;
        }
        return findById(entity.id()).orElseThrow();
    }

    @Override
    public Optional<BusinessApplication> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM business_application WHERE id = :id",
                new MapSqlParameterSource("id", id), ROW_MAPPER).stream().findFirst();
    }

    @Override
    public List<BusinessApplication> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM business_application ORDER BY id", ROW_MAPPER);
    }

    @Override
    public boolean existsById(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM business_application WHERE id = :id)",
                new MapSqlParameterSource("id", id),
                Boolean.class
        ));
    }

    @Override
    public boolean deleteById(long id) {
        return jdbc.update("DELETE FROM business_application WHERE id = :id", new MapSqlParameterSource("id", id)) == 1;
    }

    private MapSqlParameterSource parameters(BusinessApplication entity) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("code", entity.code())
                .addValue("name", entity.name())
                .addValue("accessKey", entity.accessKey())
                .addValue("secretHash", entity.secretHash())
                .addValue("status", entity.status().name());
        if (entity.id() != null) {
            parameters.addValue("id", entity.id());
        }
        return parameters;
    }

    private static BusinessApplication mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new BusinessApplication(
                resultSet.getLong("id"),
                resultSet.getString("code"),
                resultSet.getString("name"),
                resultSet.getString("access_key"),
                resultSet.getString("secret_hash"),
                ConfigurationStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("created_at", java.time.LocalDateTime.class),
                resultSet.getObject("updated_at", java.time.LocalDateTime.class)
        );
    }
}
