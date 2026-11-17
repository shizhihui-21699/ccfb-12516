package com.secaware.phishdrill.service;

import com.secaware.phishdrill.domain.Campaign;
import com.secaware.phishdrill.domain.CampaignTarget;
import com.secaware.phishdrill.domain.Employee;
import com.secaware.phishdrill.repo.CampaignRepository;
import com.secaware.phishdrill.repo.EmployeeRepository;
import com.secaware.phishdrill.repo.TargetRepository;
import io.micronaut.context.annotation.Value;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 演练发起与邮件内容渲染。演练“发信”即创建带独立 token 的 target 记录，
 * 邮件投递到本地模拟邮箱（不经过任何真实 SMTP）。
 */
@Singleton
public class CampaignService {

    private final CampaignRepository campaigns;
    private final EmployeeRepository employees;
    private final TargetRepository targets;
    private final TokenService tokens;
    private final TemplateRenderer renderer;
    private final String baseUrl;

    public CampaignService(CampaignRepository campaigns,
                           EmployeeRepository employees,
                           TargetRepository targets,
                           TokenService tokens,
                           TemplateRenderer renderer,
                           @Value("${app.base-url}") String baseUrl) {
        this.campaigns = campaigns;
        this.employees = employees;
        this.targets = targets;
        this.tokens = tokens;
        this.renderer = renderer;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public Campaign launch(String name, Long emailTemplateId, Long landingPageId, List<Long> departmentIds) {
        List<Employee> recipients = employees.findByDepartmentIdInOrderByNameAsc(departmentIds);
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("所选部门下没有可投递的员工");
        }
        Instant now = Instant.now();
        Campaign saved = campaigns.save(new Campaign(null, name, emailTemplateId, landingPageId, "DRAFT", null));
        for (Employee e : recipients) {
            targets.save(new CampaignTarget(null, saved.id(), e.id(), tokens.newToken(),
                    "SENT", now, null, null, null, null));
        }
        campaigns.updateStatusAndStartedAtById(saved.id(), "LAUNCHED", now);
        return new Campaign(saved.id(), name, emailTemplateId, landingPageId, "LAUNCHED", now);
    }

    public String trackingLink(String token) {
        return baseUrl + "/c/" + token;
    }

    public String pixelLink(String token) {
        return baseUrl + "/o/" + token;
    }

    /** 用模板 + 收件人信息渲染邮件正文，链接替换为该收件人独立的追踪链接。 */
    public String renderEmailBody(CampaignTarget target, String templateBody, Employee employee) {
        Map<String, String> values = new HashMap<>();
        values.put("name", employee.name());
        values.put("link", trackingLink(target.token()));
        values.put("shortToken", target.token().substring(0, 8));
        return renderer.render(templateBody, values);
    }
}
