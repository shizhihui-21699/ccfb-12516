package com.secaware.phishdrill.domain;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.Instant;

@MappedEntity("training_assignments")
public record TrainingAssignment(
        @Id @GeneratedValue(GeneratedValue.Type.IDENTITY) Long id,
        Long employeeId,
        Long targetId,
        Instant assignedAt,
        @Nullable Instant completedAt
) {
}
