package com.secaware.phishdrill.repo;

import com.secaware.phishdrill.domain.EmailTemplate;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.H2)
public interface EmailTemplateRepository extends CrudRepository<EmailTemplate, Long> {
}
