package com.secaware.phishdrill.repo;

import com.secaware.phishdrill.domain.CampaignTarget;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
public interface TargetRepository extends CrudRepository<CampaignTarget, Long> {

    Optional<CampaignTarget> findByToken(String token);

    List<CampaignTarget> findByCampaignIdOrderByIdAsc(Long campaignId);

    List<CampaignTarget> findByEmployeeIdOrderBySentAtDesc(Long employeeId);

    Optional<CampaignTarget> findByIdAndEmployeeId(Long id, Long employeeId);

    long countByCampaignId(Long campaignId);

    long countByCampaignIdAndReportedAtIsNotNull(Long campaignId);

    void updateStatusAndOpenedAtById(Long id, String status, Instant openedAt);

    void updateStatusAndClickedAtById(Long id, String status, Instant clickedAt);

    void updateStatusAndSubmittedAtById(Long id, String status, Instant submittedAt);

    void updateReportedAtById(Long id, Instant reportedAt);
}
