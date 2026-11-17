package com.secaware.phishdrill.service;

import com.secaware.phishdrill.domain.EmailTemplate;
import com.secaware.phishdrill.domain.LandingPage;
import com.secaware.phishdrill.repo.EmailTemplateRepository;
import com.secaware.phishdrill.repo.LandingPageRepository;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 启动时把邮件模板与落地页从 classpath 灌入数据库（已存在则跳过）。
 * 表结构由 DatabaseInitializer 的 @PostConstruct 先行创建，保证本监听器执行时表已就绪。
 */
@Singleton
public class DataSeeder implements ApplicationEventListener<ServerStartupEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(DataSeeder.class);

    private final EmailTemplateRepository emailTemplates;
    private final LandingPageRepository landingPages;

    public DataSeeder(EmailTemplateRepository emailTemplates, LandingPageRepository landingPages) {
        this.emailTemplates = emailTemplates;
        this.landingPages = landingPages;
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        seedEmailTemplates();
        seedLandingPages();
    }

    private void seedEmailTemplates() {
        if (emailTemplates.count() > 0) {
            return;
        }
        emailTemplates.save(new EmailTemplate(null,
                "邮箱配额告警（仿冒 IT 通知）",
                "【紧急】您的企业邮箱将在24小时内停止收发",
                readClasspath("templates/email-quota.html"),
                "企业共享服务中心",
                "ess-service@corp-portal.local"));
        emailTemplates.save(new EmailTemplate(null,
                "电子工资单核验（仿冒 HR 系统）",
                "您的2026年10月电子工资单待核验",
                readClasspath("templates/email-payroll.html"),
                "工资单系统",
                "payroll-noreply@corp-hr.local"));
        LOG.info("已注入 {} 个邮件模板", emailTemplates.count());
    }

    private void seedLandingPages() {
        if (landingPages.count() > 0) {
            return;
        }
        landingPages.save(new LandingPage(null,
                "企业 SSO 统一认证登录页（仿冒）",
                readClasspath("templates/landing-sso.html")));
        landingPages.save(new LandingPage(null,
                "邮箱容量验证中心（仿冒）",
                readClasspath("templates/landing-quota.html")));
        LOG.info("已注入 {} 个落地页", landingPages.count());
    }

    private static String readClasspath(String path) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("classpath 资源缺失: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取 classpath 资源失败: " + path, e);
        }
    }
}
