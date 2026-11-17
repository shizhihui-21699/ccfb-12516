package com.secaware.phishdrill.web;

import com.secaware.phishdrill.domain.Campaign;
import com.secaware.phishdrill.domain.CampaignTarget;
import com.secaware.phishdrill.domain.Employee;
import com.secaware.phishdrill.domain.LandingPage;
import com.secaware.phishdrill.repo.CampaignRepository;
import com.secaware.phishdrill.repo.EmployeeRepository;
import com.secaware.phishdrill.repo.LandingPageRepository;
import com.secaware.phishdrill.repo.TargetRepository;
import com.secaware.phishdrill.service.TrackingService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

/**
 * 员工侧的全部追踪入口，均匿名访问。
 * 每个链接携带该收件人独有的 token，因此无需任何登录即可精确到人。
 */
@Secured(SecurityRule.IS_ANONYMOUS)
@Controller
public class TrackingController {

    /** 1×1 透明 GIF（43 字节），邮件打开信标。 */
    private static final byte[] PIXEL = new byte[]{
            (byte) 0x47, (byte) 0x49, (byte) 0x46, (byte) 0x38, (byte) 0x39, (byte) 0x61,
            0x01, 0x00, 0x01, 0x00, (byte) 0x80, 0x00, 0x00,
            0x00, 0x00, 0x00, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            0x21, (byte) 0xf9, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00,
            0x2c, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0x02, 0x02, 0x44, 0x01, 0x00, 0x3b
    };

    private final TargetRepository targets;
    private final CampaignRepository campaigns;
    private final EmployeeRepository employees;
    private final LandingPageRepository landingPages;
    private final TrackingService tracking;

    public TrackingController(TargetRepository targets,
                              CampaignRepository campaigns,
                              EmployeeRepository employees,
                              LandingPageRepository landingPages,
                              TrackingService tracking) {
        this.targets = targets;
        this.campaigns = campaigns;
        this.employees = employees;
        this.landingPages = landingPages;
        this.tracking = tracking;
    }

    /** 邮件打开信标：GET /o/{token} → 透明 GIF。 */
    @Get("/o/{token}")
    public HttpResponse<byte[]> openPixel(@PathVariable String token) {
        targets.findByToken(token).ifPresent(t -> tracking.record(t, TrackingService.OPEN));
        return HttpResponse.ok(PIXEL).contentType(MediaType.IMAGE_GIF);
    }

    /** 钓鱼链接点击：GET /c/{token} → 记录 CLICK，渲染仿冒登录页。 */
    @Get("/c/{token}")
    public HttpResponse<String> landing(@PathVariable String token) {
        Optional<CampaignTarget> found = targets.findByToken(token);
        if (found.isEmpty()) {
            return HttpResponse.notFound(unknownTokenPage());
        }
        CampaignTarget target = found.get();
        tracking.record(target, TrackingService.CLICK);

        Campaign campaign = campaigns.findById(target.campaignId()).orElseThrow();
        LandingPage page = landingPages.findById(campaign.landingPageId()).orElseThrow();
        Employee employee = employees.findById(target.employeeId()).orElseThrow();

        String html = page.html()
                .replace("{{formAction}}", "/c/" + token)
                .replace("{{email}}", employee.email());
        return HttpResponse.ok(html).contentType(MediaType.TEXT_HTML);
    }

    /**
     * 仿冒登录页表单提交。
     * 合规要点：方法签名【不声明 @Body、不声明任何表单参数】，
     * 框架不会把请求体绑定进应用代码，用户名/口令在任何一层都不可见、不落库。
     * 这里只记录“发生了一次提交”这一事实，然后跳转到培训揭示页。
     */
    @Post("/c/{token}")
    @Secured(SecurityRule.IS_ANONYMOUS)
    // 显式接受表单编码（登录页是普通 HTML form）；方法不声明任何 body 参数，
    // 框架解析后即丢弃，用户名/口令不会进入应用代码
    @Consumes({MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON})
    public HttpResponse<?> credentialSubmitted(@PathVariable String token) {
        Optional<CampaignTarget> found = targets.findByToken(token);
        if (found.isEmpty()) {
            return HttpResponse.notFound(unknownTokenPage());
        }
        tracking.record(found.get(), TrackingService.SUBMIT);
        return HttpResponse.seeOther(URI.create("/reveal/" + token));
    }

    /** 培训揭示页：告诉员工这是演练、发生了什么、下一步的短课入口。 */
    @Get("/reveal/{token}")
    public HttpResponse<String> reveal(@PathVariable String token) {
        Optional<CampaignTarget> found = targets.findByToken(token);
        if (found.isEmpty()) {
            return HttpResponse.notFound(unknownTokenPage());
        }
        CampaignTarget target = found.get();
        Employee employee = employees.findById(target.employeeId()).orElseThrow();
        String html = """
                <!doctype html>
                <html lang="zh-CN"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>安全意识演练 · 教学提示</title>
                <style>
                body{margin:0;background:#f4f6fa;font-family:"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif;color:#172033}
                .box{max-width:600px;margin:56px auto;background:#fff;border:1px solid #e2e8f0;border-radius:12px;padding:34px 36px;box-shadow:0 10px 30px rgba(15,43,92,.08)}
                h1{font-size:21px;margin:0 0 14px;color:#b91c1c}
                .tip{background:#fef3c7;border:1px solid #fcd34d;color:#92400e;padding:12px 14px;border-radius:8px;font-size:13px;margin:16px 0}
                ul{padding-left:20px;font-size:14px;line-height:1.9}
                .btn{display:inline-block;background:#1d4ed8;color:#fff;text-decoration:none;padding:11px 26px;border-radius:7px;font-weight:600;margin-top:10px}
                .muted{color:#64748b;font-size:12px;margin-top:22px}
                </style></head><body><div class="box">
                <h1>⚠️ 这是一次经授权的安全意识演练</h1>
                <p>%s 同事，您好。您刚才访问的链接来自信息安全部发起的内部钓鱼演练，<b>不是真实攻击</b>。</p>
                <div class="tip">请放心：本平台<b>不会、也无法保存您输入的任何口令</b>。系统仅记录“是否发生了提交”这一事件，用于培训统计。如果您刚才输入的是真实口令，请尽快修改该口令。</div>
                <ul>
                  <li>邮件中的链接地址与真实企业域名不符，是最常见的钓鱼信号；</li>
                  <li>制造紧迫感（“24 小时内”“立即验证”）是钓鱼邮件的典型话术；</li>
                  <li>今后遇到可疑邮件，请使用模拟邮箱顶部的「上报可疑邮件」按钮。</li>
                </ul>
                <a class="btn" href="/mailbox/%d/training">前往为您分派的安全短课 →</a>
                <p class="muted">本演练依据公司《信息安全意识培训管理制度》开展，结果仅用于培训，不与个人绩效挂钩。</p>
                </div></body></html>
                """.formatted(Html.esc(employee.name()), employee.id());
        return HttpResponse.ok(html).contentType(MediaType.TEXT_HTML);
    }

    private static String unknownTokenPage() {
        return """
                <!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
                <title>链接无效</title></head>
                <body style="font-family:sans-serif;padding:60px;text-align:center;color:#475569">
                <h2>链接无效或演练已结束</h2>
                <p>如您是从邮件中打开本页，请联系信息安全部。</p>
                </body></html>
                """;
    }
}
