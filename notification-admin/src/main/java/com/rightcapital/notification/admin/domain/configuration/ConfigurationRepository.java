package com.rightcapital.notification.admin.domain.configuration;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port shared by the configuration aggregates.
 *
 * @param <T> configuration aggregate type
 */
public interface ConfigurationRepository<T> {

    T insert(T entity);

    T update(T entity);

    Optional<T> findById(long id);

    List<T> findAll();

    boolean existsById(long id);

    boolean deleteById(long id);
}
