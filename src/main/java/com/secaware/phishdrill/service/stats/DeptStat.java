package com.secaware.phishdrill.service.stats;

/** 部门维度的演练漏斗计数。 */
public record DeptStat(
        long departmentId,
        String departmentName,
        long total,
        long opened,
        long clicked,
        long submitted,
        long reported,
        double clickRate,
        double submitRate,
        double reportRate
) {
}
