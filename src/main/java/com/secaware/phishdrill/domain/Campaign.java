package com.secaware.phishdrill.domain;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.Instant;

@MappedEntity("campaigns")
public record Campaign(
        @Id @GeneratedValue(GeneratedValue.Type.IDENTITY) Long id,
        String name,
        Long emailTemplateId,
        Long landingPageId,
        String status,
        @Nullable Instant startedAt
) {
}
