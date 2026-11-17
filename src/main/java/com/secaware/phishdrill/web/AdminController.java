package com.secaware.phishdrill.web;

import com.secaware.phishdrill.domain.CampaignTarget;
import com.secaware.phishdrill.domain.Department;
import com.secaware.phishdrill.domain.EmailTemplate;
import com.secaware.phishdrill.domain.Employee;
import com.secaware.phishdrill.domain.LandingPage;
import com.secaware.phishdrill.domain.TrainingAssignment;
import com.secaware.phishdrill.repo.DepartmentRepository;
import com.secaware.phishdrill.repo.EmailTemplateRepository;
import com.secaware.phishdrill.repo.EmployeeRepository;
import com.secaware.phishdrill.repo.LandingPageRepository;
import com.secaware.phishdrill.repo.TargetRepository;
import com.secaware.phishdrill.service.CampaignService;
import com.secaware.phishdrill.service.StatsService;
import com.secaware.phishdrill.service.TrainingService;
import com.secaware.phishdrill.service.stats.CampaignStat;
import com.secaware.phishdrill.service.stats.DeptStat;
import com.secaware.phishdrill.service.stats.TrendPoint;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 管理后台：全部接口要求 ROLE_ADMIN（HTTP Basic 认证）。 */
@Secured("ROLE_ADMIN")
@Produces(MediaType.TEXT_HTML)
@Controller("/admin")
public class AdminController {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final DepartmentRepository departments;
    private final EmployeeRepository employees;
    private final EmailTemplateRepository emailTemplates;
    private final LandingPageRepository landingPages;
    private final TargetRepository targets;
    private final CampaignService campaignService;
    private final StatsService stats;
    private final TrainingService training;

    public AdminController(DepartmentRepository departments,
                           EmployeeRepository employees,
                           EmailTemplateRepository emailTemplates,
                           LandingPageRepository landingPages,
                           TargetRepository targets,
                           CampaignService campaignService,
                           StatsService stats,
                           TrainingService training) {
        this.departments = departments;
        this.employees = employees;
        this.emailTemplates = emailTemplates;
        this.landingPages = landingPages;
        this.targets = targets;
        this.campaignService = campaignService;
        this.stats = stats;
        this.training = training;
    }

    /** 总览：全局 KPI + 历次演练漏斗表。 */
    @Get
    public String dashboard() {
        List<CampaignStat> all = stats.campaignStats();
        long total = all.stream().mapToLong(CampaignStat::total).sum();
        long clicked = all.stream().mapToLong(CampaignStat::clicked).sum();
        long submitted = all.stream().mapToLong(CampaignStat::submitted).sum();
        long reported = all.stream().mapToLong(CampaignStat::reported).sum();
        double clickRate = total == 0 ? 0 : Math.round(clicked * 10000.0 / total) / 100.0;
        double submitRate = total == 0 ? 0 : Math.round(submitted * 10000.0 / total) / 100.0;
        double reportRate = total == 0 ? 0 : Math.round(reported * 10000.0 / total) / 100.0;

        StringBuilder rows = new StringBuilder();
        for (CampaignStat c : all) {
            rows.append("""
                    <tr>
                      <td><a href="/admin/campaigns/%d">%s</a></td>
                      <td style="white-space:nowrap">%s</td>
                      <td>%d</td>
                      <td>%s</td>
                      <td><b style="color:#b45309">%s</b></td>
                      <td><b style="color:#b91c1c">%s</b></td>
                      <td><b style="color:#15803d">%s</b></td>
                    </tr>""".formatted(c.campaignId(), Html.esc(c.campaignName()),
                    FMT.format(Instant.ofEpochMilli(c.startedEpoch())), c.total(),
                    Html.pct(c.openRate()), Html.pct(c.clickRate()),
                    Html.pct(c.submitRate()), Html.pct(c.reportRate())));
        }
        if (all.isEmpty()) {
            rows.append("<tr><td colspan=\"7\" class=\"muted\" style=\"text-align:center;padding:26px\">还没有演练，去「发起演练」创建第一轮吧</td></tr>");
        }

        String body = """
                <h1>演练总览</h1>
                <div class="grid">
                  <div class="kpi"><div class="label">累计投递</div><div class="num">%d</div></div>
                  <div class="kpi warn"><div class="label">累计点击率（核心 KPI ↓）</div><div class="num">%s</div></div>
                  <div class="kpi danger"><div class="label">累计凭证提交率 ↓</div><div class="num">%s</div></div>
                  <div class="kpi ok"><div class="label">累计主动上报率 ↑</div><div class="num">%s</div></div>
                  <div class="kpi"><div class="label">短课完成 / 分派</div><div class="num">%d / %d</div></div>
                </div>
                <h2>历次演练漏斗</h2>
                <div class="card"><table>
                  <thead><tr><th>演练</th><th>开始时间</th><th>投递</th><th>打开率</th><th>点击率</th><th>提交率</th><th>上报率</th></tr></thead>
                  <tbody>%s</tbody>
                </table></div>
                <p><a class="btn" href="/admin/campaigns/new">+ 发起新一轮演练</a></p>
                """.formatted(total, Html.pct(clickRate), Html.pct(submitRate), Html.pct(reportRate),
                training.completedCount(), training.listAll().size(), rows);
        return Html.adminPage("总览", "/admin", body);
    }

    /** 发起演练表单。 */
    @Get("/campaigns/new")
    public String launchForm() {
        StringBuilder deptChecks = new StringBuilder();
        for (Department d : departments.findAll()) {
            long count = employees.findByDepartmentIdInOrderByNameAsc(List.of(d.id())).size();
            deptChecks.append("""
                    <label><input type="checkbox" name="departmentIds" value="%d" checked> %s（%d 人）</label>"""
                    .formatted(d.id(), Html.esc(d.name()), count));
        }
        StringBuilder tplOpts = new StringBuilder();
        for (EmailTemplate t : emailTemplates.findAll()) {
            tplOpts.append("<option value=\"%d\">%s</option>".formatted(t.id(), Html.esc(t.name())));
        }
        StringBuilder lpOpts = new StringBuilder();
        for (LandingPage p : landingPages.findAll()) {
            lpOpts.append("<option value=\"%d\">%s</option>".formatted(p.id(), Html.esc(p.name())));
        }
        String body = """
                <h1>发起新一轮钓鱼演练</h1>
                <div class="card">
                  <form method="post" action="/admin/campaigns">
                    <label>演练名称</label>
                    <input type="text" name="name" required placeholder="例如：2026 Q4 全员钓鱼演练">
                    <label>邮件模板</label>
                    <select name="emailTemplateId">%s</select>
                    <label>仿冒落地页</label>
                    <select name="landingPageId">%s</select>
                    <label>投递部门（可多选，按部门挑人）</label>
                    <div class="checkbox-grid">%s</div>
                    <div style="margin-top:22px">
                      <button class="btn" type="submit">立即发起并投递到模拟邮箱</button>
                    </div>
                    <p class="muted" style="margin-top:14px">每个收件人会获得独立追踪链接；系统只记录打开/点击/提交/上报事件，<b>不保存任何输入的口令</b>。</p>
                  </form>
                </div>
                """.formatted(tplOpts, lpOpts, deptChecks);
        return Html.adminPage("发起演练", "/admin/campaigns", body);
    }

    /** 发起演练提交（表单绑定到 POJO）。 */
    @Post(value = "/campaigns", consumes = MediaType.APPLICATION_FORM_URLENCODED)
    public HttpResponse<?> launch(@Body LaunchForm form) {
        if (form.departmentIds() == null || form.departmentIds().isEmpty()) {
            return HttpResponse.badRequest(Html.adminPage("发起失败", "/admin/campaigns",
                    "<div class=\"flash err\">请至少选择一个部门。</div>"));
        }
        try {
            var campaign = campaignService.launch(form.name(), form.emailTemplateId(),
                    form.landingPageId(), form.departmentIds());
            return HttpResponse.seeOther(java.net.URI.create("/admin/campaigns/" + campaign.id()));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest(Html.adminPage("发起失败", "/admin/campaigns",
                    "<div class=\"flash err\">" + Html.esc(e.getMessage()) + "</div>"));
        }
    }

    /** 单次演练详情：部门漏斗 + 每个收件人的明细。 */
    @Get("/campaigns/{id}")
    public String campaignDetail(@PathVariable Long id) {
        List<DeptStat> deptStats = stats.departmentStats(id);
        StringBuilder deptRows = new StringBuilder();
        for (DeptStat d : deptStats) {
            deptRows.append("""
                    <tr>
                      <td>%s</td><td>%d</td><td>%d</td>
                      <td><div class="bar"><span style="width:%f%%"></span></div><div class="barval">%s</div></td>
                      <td><div class="bar danger"><span style="width:%f%%"></span></div><div class="barval">%s</div></td>
                      <td><div class="bar ok"><span style="width:%f%%"></span></div><div class="barval">%s</div></td>
                    </tr>""".formatted(Html.esc(d.departmentName()), d.total(), d.opened(),
                    d.clickRate(), Html.pct(d.clickRate()),
                    d.submitRate(), Html.pct(d.submitRate()),
                    d.reportRate(), Html.pct(d.reportRate())));
        }

        Map<Long, Employee> empCache = new HashMap<>();
        Map<Long, String> deptNames = new HashMap<>();
        departments.findAll().forEach(d -> deptNames.put(d.id(), d.name()));

        StringBuilder targetRows = new StringBuilder();
        for (CampaignTarget t : targets.findByCampaignIdOrderByIdAsc(id)) {
            Employee e = empCache.computeIfAbsent(t.employeeId(),
                    k -> employees.findById(k).orElseThrow());
            targetRows.append("""
                    <tr>
                      <td>%s</td><td>%s</td><td>%s</td>
                      <td>%s</td><td style="font-family:monospace;font-size:11px">%s</td>
                      <td>%s</td>
                    </tr>""".formatted(Html.esc(e.name()), Html.esc(e.email()),
                    Html.esc(deptNames.getOrDefault(e.departmentId(), "-")),
                    statePill(t), Html.esc(t.token().substring(0, 12)) + "…",
                    t.reportedAt() == null ? "—" : "<span class=\"pill reported\">已主动上报</span>"));
        }

        String body = """
                <p><a class="btn secondary small" href="/admin">← 返回总览</a></p>
                <h1>演练 #%d 部门对比</h1>
                <div class="card"><table>
                  <thead><tr><th>部门</th><th>投递</th><th>打开</th><th>点击率</th><th>提交率</th><th>上报率</th></tr></thead>
                  <tbody>%s</tbody>
                </table></div>
                <h2>收件人明细（每人独立 token）</h2>
                <div class="card"><table>
                  <thead><tr><th>姓名</th><th>邮箱</th><th>部门</th><th>当前阶段</th><th>Token</th><th>上报</th></tr></thead>
                  <tbody>%s</tbody>
                </table></div>
                """.formatted(id, deptRows, targetRows);
        return Html.adminPage("演练详情", "/admin", body);
    }

    /** 跨演练部门趋势：内联 SVG 折线图（点击率逐期对比）。 */
    @Get("/trends")
    public String trends() {
        List<TrendPoint> points = stats.trend();
        String body = """
                <h1>部门趋势对比</h1>
                <div class="card">
                  <h2 style="margin-top:0">各部门点击率随演练轮次的变化（目标：逐轮下降）</h2>
                  <div class="chart-wrap">%s</div>
                </div>
                <div class="card">
                  <h2 style="margin-top:0">明细数据</h2>
                  <table><thead><tr><th>演练</th><th>部门</th><th>投递</th><th>点击率</th><th>提交率</th><th>上报率</th></tr></thead>
                  <tbody>%s</tbody></table>
                </div>
                """.formatted(TrendChart.clickRateSvg(points), trendRows(points));
        return Html.adminPage("部门趋势", "/admin/trends", body);
    }

    /** 短课分派列表。 */
    @Get("/training")
    public String trainingList() {
        StringBuilder rows = new StringBuilder();
        for (TrainingAssignment a : training.listAll()) {
            Employee e = employees.findById(a.employeeId()).orElseThrow();
            rows.append("""
                    <tr>
                      <td>%s</td><td>%s</td>
                      <td style="white-space:nowrap">%s</td>
                      <td>%s</td>
                    </tr>""".formatted(Html.esc(e.name()), Html.esc(e.email()),
                    FMT.format(a.assignedAt()),
                    a.completedAt() == null
                            ? "<span class=\"pill clicked\">待完成</span>"
                            : "<span class=\"pill reported\">已完成 · " + FMT.format(a.completedAt()) + "</span>"));
        }
        if (rows.isEmpty()) {
            rows.append("<tr><td colspan=\"4\" class=\"muted\" style=\"text-align:center;padding:24px\">员工首次点击钓鱼链接后会自动分派短课</td></tr>");
        }
        String body = """
                <h1>短课分派（点击即自动派课，一人一次）</h1>
                <div class="card"><table>
                  <thead><tr><th>员工</th><th>邮箱</th><th>分派时间</th><th>状态</th></tr></thead>
                  <tbody>%s</tbody>
                </table></div>
                """.formatted(rows);
        return Html.adminPage("短课分派", "/admin/training", body);
    }

    private static String trendRows(List<TrendPoint> points) {
        StringBuilder sb = new StringBuilder();
        for (TrendPoint p : points) {
            if (p.total() == 0) continue;
            sb.append("""
                    <tr><td>%s</td><td>%s</td><td>%d</td><td>%s</td><td>%s</td><td>%s</td></tr>"""
                    .formatted(Html.esc(p.campaignName()), Html.esc(p.departmentName()),
                            p.total(), Html.pct(p.clickRate()), Html.pct(p.submitRate()), Html.pct(p.reportRate())));
        }
        return sb.toString();
    }

    private static String statePill(CampaignTarget t) {
        if (t.submittedAt() != null) return "<span class=\"pill submitted\">已提交凭证</span>";
        if (t.clickedAt() != null) return "<span class=\"pill clicked\">已点击</span>";
        if (t.openedAt() != null) return "<span class=\"pill opened\">已打开</span>";
        return "<span class=\"pill sent\">已投递</span>";
    }

    /** 发起演练表单绑定对象。 */
    public record LaunchForm(String name, Long emailTemplateId, Long landingPageId, List<Long> departmentIds) {
    }
}
