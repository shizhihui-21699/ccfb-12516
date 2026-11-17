package com.secaware.phishdrill.web;

import com.secaware.phishdrill.domain.Campaign;
import com.secaware.phishdrill.domain.CampaignTarget;
import com.secaware.phishdrill.domain.EmailTemplate;
import com.secaware.phishdrill.domain.Employee;
import com.secaware.phishdrill.domain.TrainingAssignment;
import com.secaware.phishdrill.repo.CampaignRepository;
import com.secaware.phishdrill.repo.EmailTemplateRepository;
import com.secaware.phishdrill.repo.EmployeeRepository;
import com.secaware.phishdrill.repo.TargetRepository;
import com.secaware.phishdrill.service.CampaignService;
import com.secaware.phishdrill.service.TrackingService;
import com.secaware.phishdrill.service.TrainingService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 本地模拟邮箱：员工用 ?email=xxx 选择身份后查看“收到”的演练邮件。
 * 不连接任何真实 SMTP，邮件就是 target + 模板的实时渲染。
 */
@Secured(SecurityRule.IS_ANONYMOUS)
@Produces(MediaType.TEXT_HTML)
@Controller("/mailbox")
public class MailboxController {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final EmployeeRepository employees;
    private final TargetRepository targets;
    private final CampaignRepository campaigns;
    private final EmailTemplateRepository templates;
    private final CampaignService campaignService;
    private final TrackingService tracking;
    private final TrainingService training;

    public MailboxController(EmployeeRepository employees,
                             TargetRepository targets,
                             CampaignRepository campaigns,
                             EmailTemplateRepository templates,
                             CampaignService campaignService,
                             TrackingService tracking,
                             TrainingService training) {
        this.employees = employees;
        this.targets = targets;
        this.campaigns = campaigns;
        this.templates = templates;
        this.campaignService = campaignService;
        this.tracking = tracking;
        this.training = training;
    }

    /** 入口页：选择身份进入模拟邮箱。 */
    @Get
    public String index() {
        StringBuilder opts = new StringBuilder();
        employees.findAll().forEach(e -> opts.append("""
                <option value="%s">%s（%s）</option>""".formatted(Html.esc(e.email()), Html.esc(e.name()), Html.esc(e.email()))));
        return page("模拟邮箱登录", """
                <div class="card" style="max-width:460px;margin:40px auto">
                  <h1 style="font-size:18px">📬 本地模拟邮箱</h1>
                  <p class="muted">演练邮件只投递到本模拟邮箱，不经过真实邮件系统。选择您的身份进入：</p>
                  <form method="get" action="/mailbox/inbox">
                    <label>员工身份</label>
                    <select name="email">%s</select>
                    <div style="margin-top:18px"><button class="btn" type="submit">进入邮箱</button></div>
                  </form>
                </div>
                """.formatted(opts));
    }

    /** 收件箱：列出该员工所有 target 对应的演练邮件。 */
    @Get("/inbox")
    public HttpResponse<String> inbox(@Nullable @QueryValue String email) {
        Employee employee = employees.findByEmail(email == null ? "" : email).orElse(null);
        if (employee == null) {
            return HttpResponse.seeOther(java.net.URI.create("/mailbox"));
        }
        List<CampaignTarget> list = targets.findByEmployeeIdOrderBySentAtDesc(employee.id());
        StringBuilder rows = new StringBuilder();
        for (CampaignTarget t : list) {
            Campaign c = campaigns.findById(t.campaignId()).orElseThrow();
            EmailTemplate tpl = templates.findById(c.emailTemplateId()).orElseThrow();
            String state = statePill(t);
            rows.append("""
                    <tr>
                      <td><a href="/mailbox/message/%d?email=%s">%s</a></td>
                      <td>%s</td>
                      <td style="white-space:nowrap">%s</td>
                      <td>%s</td>
                    </tr>""".formatted(t.id(), Html.esc(employee.email()),
                    Html.esc(tpl.subject()), Html.esc(tpl.fromName()),
                    FMT.format(t.sentAt()), state));
        }
        if (list.isEmpty()) {
            rows.append("<tr><td colspan=\"4\" class=\"muted\" style=\"text-align:center;padding:24px\">暂无演练邮件</td></tr>");
        }
        String body = """
                <div class="card">
                  <div style="display:flex;justify-content:space-between;align-items:center">
                    <h1 style="font-size:18px;margin:0">📬 %s 的模拟收件箱（%d 封）</h1>
                    <a class="btn secondary small" href="/mailbox">切换身份</a>
                  </div>
                  <table style="margin-top:14px">
                    <thead><tr><th>主题</th><th>发件人</th><th>时间</th><th>我的状态</th></tr></thead>
                    <tbody>%s</tbody>
                  </table>
                </div>
                <div class="card">
                  <h2>我的安全短课</h2>
                  %s
                </div>
                """.formatted(Html.esc(employee.name()), list.size(), rows, trainingCard(employee));
        return HttpResponse.ok(page(employee.name() + " 的收件箱", body)).contentType(MediaType.TEXT_HTML);
    }

    /** 单封邮件：渲染完整 HTML（含打开信标），顶部提供「上报可疑邮件」。 */
    @Get("/message/{targetId}")
    public HttpResponse<String> message(@PathVariable Long targetId, @Nullable @QueryValue String email) {
        Employee employee = employees.findByEmail(email == null ? "" : email).orElse(null);
        CampaignTarget target = targets.findByIdAndEmployeeId(targetId, employee == null ? -1L : employee.id())
                .orElse(null);
        if (employee == null || target == null) {
            return HttpResponse.seeOther(java.net.URI.create("/mailbox"));
        }
        Campaign campaign = campaigns.findById(target.campaignId()).orElseThrow();
        EmailTemplate tpl = templates.findById(campaign.emailTemplateId()).orElseThrow();
        String bodyHtml = campaignService.renderEmailBody(target, tpl.bodyHtml(), employee);
        // 打开信标：图片请求落到 /o/{token}
        String pixel = "<img src=\"" + campaignService.pixelLink(target.token())
                + "\" width=\"1\" height=\"1\" alt=\"\" style=\"display:none\">";

        String body = """
                <p><a class="btn secondary small" href="/mailbox/inbox?email=%s">← 返回收件箱</a></p>
                <div class="card" style="padding:0;overflow:hidden">
                  <div style="display:flex;justify-content:space-between;align-items:center;background:#f8fafc;padding:12px 18px;border-bottom:1px solid #e2e8f0">
                    <div>
                      <div style="font-weight:700">%s</div>
                      <div class="muted" style="font-size:12px">发件人：%s &lt;%s&gt; · %s</div>
                    </div>
                    <form method="post" action="/mailbox/report/%d">
                      <input type="hidden" name="email" value="%s">
                      <button class="btn small" style="background:#15803d" type="submit">🚩 上报可疑邮件</button>
                    </form>
                  </div>
                  <div style="padding:22px 24px">%s</div>
                </div>
                %s
                """.formatted(Html.esc(employee.email()),
                Html.esc(tpl.subject()), Html.esc(tpl.fromName()), Html.esc(tpl.fromEmail()),
                FMT.format(target.sentAt()),
                target.id(), Html.esc(employee.email()),
                bodyHtml, pixel);
        return HttpResponse.ok(page(tpl.subject(), body)).contentType(MediaType.TEXT_HTML);
    }

    /** 主动上报：记录 REPORT 事件，显示感谢页（上报与是否点击互相独立统计）。
     *  仅用路径上的 targetId 定位，不依赖表单字段，避免请求体绑定的歧义。 */
    @Post("/report/{targetId}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public HttpResponse<?> report(@PathVariable Long targetId) {
        CampaignTarget target = targets.findById(targetId).orElse(null);
        if (target == null) {
            return HttpResponse.seeOther(java.net.URI.create("/mailbox"));
        }
        Employee employee = employees.findById(target.employeeId()).orElseThrow();
        tracking.record(target, TrackingService.REPORT);
        String body = """
                <div class="card" style="max-width:520px;margin:40px auto;text-align:center">
                  <div style="font-size:40px">✅</div>
                  <h1 style="font-size:19px">上报成功，感谢您的警惕！</h1>
                  <p>您主动识别并上报了一封演练钓鱼邮件，已计入<b>主动上报</b>正向指标。</p>
                  <p class="muted">这正是安全意识培训希望形成的肌肉记忆：可疑 → 核实 → 上报。</p>
                  <a class="btn" href="/mailbox/inbox?email=%s">返回收件箱</a>
                </div>
                """.formatted(Html.esc(employee.email()));
        return HttpResponse.ok(page("上报成功", body)).contentType(MediaType.TEXT_HTML);
    }

    /** 员工的短课页：点击后自动分派，学完点按钮标记完成。 */
    @Get("/{employeeId}/training")
    public HttpResponse<String> training(@PathVariable Long employeeId) {
        Employee employee = employees.findById(employeeId).orElse(null);
        if (employee == null) {
            return HttpResponse.notFound("员工不存在");
        }
        TrainingAssignment assignment = training.forEmployee(employee.id()).orElse(null);
        String status;
        if (assignment == null) {
            status = "<p class=\"muted\">您当前没有待完成的短课（只有点击演练链接后才会自动分派）。</p>";
        } else if (assignment.completedAt() != null) {
            status = "<div class=\"flash ok\">✅ 本课已于 " + FMT.format(assignment.completedAt()) + " 完成，感谢配合！</div>";
        } else {
            status = """
                    <div class="flash">📖 本课因您在演练中点击了钓鱼链接而自动分派，预计学习时长 5 分钟。</div>
                    <form method="post" action="/mailbox/%d/training/complete">
                      <button class="btn" type="submit">我已学完，标记完成</button>
                    </form>""".formatted(employee.id());
        }
        String body = """
                <p><a class="btn secondary small" href="/mailbox/inbox?email=%s">← 返回收件箱</a></p>
                <div class="card">
                  <h1 style="font-size:18px">🎓 安全意识短课：三步识破钓鱼邮件</h1>
                  %s
                </div>
                <div class="card">
                  <h2>第一步：看发件人</h2>
                  <p>真实内部邮件来自企业正式域名；仿冒邮件常使用形近域名、额外前缀（如 <code>corp-portal.local</code> 代替 <code>corp.local</code>）或外部免费邮箱。</p>
                  <h2>第二步：看链接</h2>
                  <p>把鼠标悬停在按钮上即可看到真实地址。按钮文字与实际域名不符、出现 IP 地址或一长串随机字符，都要高度警惕。</p>
                  <h2>第三步：看话术</h2>
                  <p>“紧急”“24 小时内停用”“账户异常”制造恐慌，是钓鱼邮件逼你来不及思考的常用手段。越催越要慢下来核实。</p>
                  <h2>记住一句话</h2>
                  <p><b>任何邮件都不会、也不应该要求你在链接打开的页面里输入真实口令。</b> 遇到可疑邮件，使用「上报可疑邮件」按钮。</p>
                </div>
                """.formatted(Html.esc(employee.email()), status);
        return HttpResponse.ok(page("安全短课", body)).contentType(MediaType.TEXT_HTML);
    }

    /** 短课完成：按员工定位其唯一的分派记录并标记完成。 */
    @Post("/{employeeId}/training/complete")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public HttpResponse<?> complete(@PathVariable Long employeeId) {
        Employee employee = employees.findById(employeeId).orElse(null);
        if (employee == null) {
            return HttpResponse.notFound("员工不存在");
        }
        training.forEmployee(employee.id()).ifPresent(a -> training.complete(a.id()));
        return HttpResponse.seeOther(java.net.URI.create("/mailbox/" + employeeId + "/training"));
    }

    private String trainingCard(Employee employee) {
        return training.forEmployee(employee.id())
                .map(a -> a.completedAt() == null
                        ? "<p>📖 有 1 节待完成短课 · <a href=\"/mailbox/" + employee.id() + "/training\">立即学习</a></p>"
                        : "<p class=\"muted\">✅ 短课已完成（" + FMT.format(a.completedAt()) + "）</p>")
                .orElse("<p class=\"muted\">暂无分派</p>");
    }

    private static String statePill(CampaignTarget t) {
        if (t.submittedAt() != null) return "<span class=\"pill submitted\">已提交凭证</span>";
        if (t.clickedAt() != null) return "<span class=\"pill clicked\">已点击链接</span>";
        if (t.openedAt() != null) return "<span class=\"pill opened\">已打开</span>";
        return "<span class=\"pill sent\">已投递</span>";
    }

    private static String page(String title, String inner) {
        return """
                <!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s · 模拟邮箱</title>
                <link rel="stylesheet" href="/assets/admin.css">
                </head><body>
                <header class="topbar"><div class="brand">📬 本地模拟邮箱（演练专用）</div>
                <nav><a class="nav" href="/mailbox">切换身份</a><a class="nav" href="/admin">管理后台 ↗</a></nav></header>
                <main class="container">%s</main>
                <footer class="foot">演练环境 · 邮件不会发送到真实邮箱</footer>
                </body></html>
                """.formatted(Html.esc(title), inner);
    }
}
