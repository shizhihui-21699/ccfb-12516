package com.secaware.phishdrill.domain;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity("employees")
public record Employee(
        @Id @GeneratedValue(GeneratedValue.Type.IDENTITY) Long id,
        String name,
        String email,
        Long departmentId
) {
}
