package com.secaware.phishdrill.repo;

import com.secaware.phishdrill.domain.Employee;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
public interface EmployeeRepository extends CrudRepository<Employee, Long> {

    List<Employee> findByDepartmentIdInOrderByNameAsc(List<Long> departmentIds);

    Optional<Employee> findByEmail(String email);
}
