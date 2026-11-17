package com.secaware.phishdrill.service.stats;

/** 单次演练 × 单个部门的一行趋势数据。 */
public record TrendPoint(
        long campaignId,
        String campaignName,
        long campaignStartedEpoch,
        long departmentId,
        String departmentName,
        long total,
        long clicked,
        long submitted,
        long reported,
        double clickRate,
        double submitRate,
        double reportRate
) {
}
