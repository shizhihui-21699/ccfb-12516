package com.secaware.phishdrill.service;

import com.secaware.phishdrill.domain.Campaign;
import com.secaware.phishdrill.domain.CampaignTarget;
import com.secaware.phishdrill.domain.Department;
import com.secaware.phishdrill.domain.Employee;
import com.secaware.phishdrill.repo.CampaignRepository;
import com.secaware.phishdrill.repo.DepartmentRepository;
import com.secaware.phishdrill.repo.EmployeeRepository;
import com.secaware.phishdrill.repo.TargetRepository;
import com.secaware.phishdrill.service.stats.CampaignStat;
import com.secaware.phishdrill.service.stats.DeptStat;
import com.secaware.phishdrill.service.stats.TrendPoint;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计聚合：所有比例在 Java 侧按事件时间戳计算，
 * 指标定义——
 *   打开率 = openedAt 非空 / 投递数
 *   点击率 = clickedAt 非空 / 投递数（核心 KPI，目标是逐期下降）
 *   提交率 = submittedAt 非空 / 投递数（最严重的一档）
 *   上报率 = reportedAt 非空 / 投递数（正向指标，目标是逐期上升）
 */
@Singleton
public class StatsService {

    private final CampaignRepository campaigns;
    private final TargetRepository targets;
    private final EmployeeRepository employees;
    private final DepartmentRepository departments;

    public StatsService(CampaignRepository campaigns,
                        TargetRepository targets,
                        EmployeeRepository employees,
                        DepartmentRepository departments) {
        this.campaigns = campaigns;
        this.targets = targets;
        this.employees = employees;
        this.departments = departments;
    }

    /** 全部演练的整体漏斗。 */
    public List<CampaignStat> campaignStats() {
        Map<Long, Employee> employeeCache = new HashMap<>();
        List<CampaignStat> out = new ArrayList<>();
        for (Campaign c : campaigns.findAllOrderByStartedAtDesc()) {
            List<CampaignTarget> list = targets.findByCampaignIdOrderByIdAsc(c.id());
            long total = list.size();
            long opened = list.stream().filter(t -> t.openedAt() != null).count();
            long clicked = list.stream().filter(t -> t.clickedAt() != null).count();
            long submitted = list.stream().filter(t -> t.submittedAt() != null).count();
            long reported = list.stream().filter(t -> t.reportedAt() != null).count();
            out.add(new CampaignStat(c.id(), c.name(), c.status(),
                    c.startedAt() == null ? 0 : c.startedAt().toEpochMilli(),
                    total, opened, clicked, submitted, reported,
                    pct(opened, total), pct(clicked, total), pct(submitted, total), pct(reported, total)));
        }
        return out;
    }

    /** 单次演练按部门拆分的漏斗对比。 */
    public List<DeptStat> departmentStats(long campaignId) {
        Map<Long, Department> deptMap = new LinkedHashMap<>();
        departments.findAll().forEach(d -> deptMap.put(d.id(), d));
        Map<Long, long[]> counters = new LinkedHashMap<>(); // deptId -> [total, opened, clicked, submitted, reported]
        deptMap.keySet().forEach(id -> counters.put(id, new long[5]));

        for (CampaignTarget t : targets.findByCampaignIdOrderByIdAsc(campaignId)) {
            Employee e = employees.findById(t.employeeId()).orElseThrow();
            long[] c = counters.get(e.departmentId());
            if (c == null) {
                continue;
            }
            c[0]++;
            if (t.openedAt() != null) c[1]++;
            if (t.clickedAt() != null) c[2]++;
            if (t.submittedAt() != null) c[3]++;
            if (t.reportedAt() != null) c[4]++;
        }

        List<DeptStat> out = new ArrayList<>();
        for (Map.Entry<Long, long[]> e : counters.entrySet()) {
            long[] c = e.getValue();
            Department d = deptMap.get(e.getKey());
            out.add(new DeptStat(d.id(), d.name(), c[0], c[1], c[2], c[3], c[4],
                    pct(c[2], c[0]), pct(c[3], c[0]), pct(c[4], c[0])));
        }
        out.sort(Comparator.comparingLong(DeptStat::total).reversed());
        return out;
    }

    /**
     * 跨演练 × 部门趋势：每个演练每个部门一行，按演练开始时间升序，
     * 前端按部门画折线，观察点击率随演练轮次的变化。
     */
    public List<TrendPoint> trend() {
        Map<Long, Department> deptMap = new LinkedHashMap<>();
        departments.findAll().forEach(d -> deptMap.put(d.id(), d));

        List<TrendPoint> out = new ArrayList<>();
        List<Campaign> ordered = new ArrayList<>();
        campaigns.findAllOrderByStartedAtDesc().forEach(ordered::add);
        // findOrderByStartedAtDesc 是新→旧，趋势需要旧→新
        for (int i = ordered.size() - 1; i >= 0; i--) {
            Campaign c = ordered.get(i);
            Map<Long, long[]> counters = new LinkedHashMap<>();
            deptMap.keySet().forEach(id -> counters.put(id, new long[4])); // total, clicked, submitted, reported

            for (CampaignTarget t : targets.findByCampaignIdOrderByIdAsc(c.id())) {
                Employee e = employees.findById(t.employeeId()).orElseThrow();
                long[] arr = counters.get(e.departmentId());
                if (arr == null) {
                    continue;
                }
                arr[0]++;
                if (t.clickedAt() != null) arr[1]++;
                if (t.submittedAt() != null) arr[2]++;
                if (t.reportedAt() != null) arr[3]++;
            }
            for (Map.Entry<Long, long[]> en : counters.entrySet()) {
                long[] arr = en.getValue();
                Department d = deptMap.get(en.getKey());
                out.add(new TrendPoint(c.id(), c.name(),
                        c.startedAt() == null ? 0 : c.startedAt().toEpochMilli(),
                        d.id(), d.name(), arr[0], arr[1], arr[2], arr[3],
                        pct(arr[1], arr[0]), pct(arr[2], arr[0]), pct(arr[3], arr[0])));
            }
        }
        return out;
    }

    private static double pct(long part, long total) {
        return total == 0 ? 0.0 : Math.round(part * 10000.0 / total) / 100.0;
    }
}
