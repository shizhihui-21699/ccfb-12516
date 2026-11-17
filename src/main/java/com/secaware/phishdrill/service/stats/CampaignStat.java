package com.secaware.phishdrill.service.stats;

/** 单次演练整体漏斗，管理后台列表与详情使用。 */
public record CampaignStat(
        long campaignId,
        String campaignName,
        String status,
        long startedEpoch,
        long total,
        long opened,
        long clicked,
        long submitted,
        long reported,
        double openRate,
        double clickRate,
        double submitRate,
        double reportRate
) {
}
