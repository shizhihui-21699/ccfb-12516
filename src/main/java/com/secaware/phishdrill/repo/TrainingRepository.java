package com.secaware.phishdrill.repo;

import com.secaware.phishdrill.domain.TrainingAssignment;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
public interface TrainingRepository extends CrudRepository<TrainingAssignment, Long> {

    Optional<TrainingAssignment> findByEmployeeId(Long employeeId);

    List<TrainingAssignment> findAllOrderByAssignedAtDesc();

    long countByCompletedAtIsNotNull();

    void updateCompletedAtById(Long id, Instant completedAt);
}
