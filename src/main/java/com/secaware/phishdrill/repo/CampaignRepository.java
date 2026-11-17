package com.secaware.phishdrill.repo;

import com.secaware.phishdrill.domain.Campaign;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
public interface CampaignRepository extends CrudRepository<Campaign, Long> {

    List<Campaign> findAllOrderByStartedAtDesc();

    void updateStatusAndStartedAtById(Long id, String status, Instant startedAt);
}
