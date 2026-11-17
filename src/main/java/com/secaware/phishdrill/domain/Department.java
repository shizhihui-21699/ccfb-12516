package com.secaware.phishdrill.domain;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity("departments")
public record Department(
        @Id @GeneratedValue(GeneratedValue.Type.IDENTITY) Long id,
        String name
) {
}
