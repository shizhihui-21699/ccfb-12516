package com.secaware.phishdrill.repo;

import com.secaware.phishdrill.domain.TrackingEvent;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
public interface EventRepository extends CrudRepository<TrackingEvent, Long> {

    List<TrackingEvent> findByTargetIdOrderByAtAsc(Long targetId);

    long countByTargetIdAndType(Long targetId, String type);
}
