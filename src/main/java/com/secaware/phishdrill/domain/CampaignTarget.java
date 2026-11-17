package com.secaware.phishdrill.domain;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.Instant;

/**
 * 演练中单个收件人的追踪记录。token 全局唯一，邮件里的所有链接都带它。
 * status 是只进不退的阶段：SENT → OPENED → CLICKED → SUBMITTED。
 */
@MappedEntity("campaign_targets")
public record CampaignTarget(
        @Id @GeneratedValue(GeneratedValue.Type.IDENTITY) Long id,
        Long campaignId,
        Long employeeId,
        String token,
        String status,
        Instant sentAt,
        @Nullable Instant openedAt,
        @Nullable Instant clickedAt,
        @Nullable Instant submittedAt,
        @Nullable Instant reportedAt
) {
}
