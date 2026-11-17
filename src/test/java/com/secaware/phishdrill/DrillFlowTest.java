package com.secaware.phishdrill;

import com.secaware.phishdrill.domain.Campaign;
import com.secaware.phishdrill.domain.CampaignTarget;
import com.secaware.phishdrill.repo.CampaignRepository;
import com.secaware.phishdrill.repo.TargetRepository;
import com.secaware.phishdrill.repo.TrainingRepository;
import com.secaware.phishdrill.service.CampaignService;
import com.secaware.phishdrill.service.StatsService;
import com.secaware.phishdrill.service.stats.DeptStat;
import com.secaware.phishdrill.service.stats.TrendPoint;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端演练链路测试：发起 → 打开信标 → 点击 → 提交（带真实口令）→ 上报 → 自动派课 → 统计。
 * 其中包含合规断言：数据库里不存在任何能存放口令的列，提交的口令无法被持久化。
 */
@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DrillFlowTest implements TestPropertyProvider {

    @Override
    public Map<String, String> getProperties() {
        // 文件型 H2：所有连接（初始化器 unwrap 的连接 + 仓储的事务连接）指向同一个物理库，
        // 彻底避免内存库在连接池下出现多实例、表互不可见的问题。
        String dbPath = System.getProperty("java.io.tmpdir") + "/phishdrill-test-" + System.nanoTime();
        return Map.ofEntries(
                Map.entry("datasources.default.url", "jdbc:h2:file:" + dbPath + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE"),
                Map.entry("datasources.default.driver-class-name", "org.h2.Driver"),
                Map.entry("datasources.default.username", "sa"),
                Map.entry("datasources.default.password", ""),
                Map.entry("datasources.default.dialect", "H2"),
                Map.entry("micronaut.server.port", "-1"),
                Map.entry("micronaut.security.basic-auth.enabled", "true"),
                Map.entry("micronaut.security.intercept-url-map[0].pattern", "/o/**"),
                Map.entry("micronaut.security.intercept-url-map[0].access[0]", "isAnonymous()"),
                Map.entry("micronaut.security.intercept-url-map[1].pattern", "/c/**"),
                Map.entry("micronaut.security.intercept-url-map[1].access[0]", "isAnonymous()"),
                Map.entry("micronaut.security.intercept-url-map[2].pattern", "/reveal/**"),
                Map.entry("micronaut.security.intercept-url-map[2].access[0]", "isAnonymous()"),
                Map.entry("micronaut.security.intercept-url-map[3].pattern", "/mailbox/**"),
                Map.entry("micronaut.security.intercept-url-map[3].access[0]", "isAnonymous()"),
                Map.entry("micronaut.security.intercept-url-map[4].pattern", "/assets/**"),
                Map.entry("micronaut.security.intercept-url-map[4].access[0]", "isAnonymous()"),
                Map.entry("micronaut.security.intercept-url-map[5].pattern", "/admin/**"),
                Map.entry("micronaut.security.intercept-url-map[5].access[0]", "ROLE_ADMIN"),
                Map.entry("app.base-url", "http://localhost:8080"),
                Map.entry("admin.username", "admin"),
                Map.entry("admin.password", "changeme")
        );
    }


    private static final String ADMIN = "Basic " + Base64.getEncoder().encodeToString("admin:changeme".getBytes());
    private static final String FAKE_PASSWORD = "SuperSecret-P@ssw0rd-2026";

    @Inject
    @Client("/")
    HttpClient client;

    @Inject CampaignService campaignService;
    @Inject CampaignRepository campaigns;
    @Inject TargetRepository targets;
    @Inject TrainingRepository training;
    @Inject StatsService stats;
    @Inject DataSource dataSource;

    @Test
    void fullDrillLifecycle_andPasswordIsNeverStored() throws Exception {
        // 1. 管理后台必须认证
        HttpClientResponseException ex = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange("/admin"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());

        // 2. 发起演练（财务部 id=1，种子数据 4 人）
        HttpResponse<?> launch = client.toBlocking().exchange(
                HttpRequest.POST("/admin/campaigns",
                                "name=测试演练-第一轮&emailTemplateId=1&landingPageId=1&departmentIds=1")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED));
        assertEquals(HttpStatus.SEE_OTHER, launch.status());

        Campaign campaign = campaigns.findAllOrderByStartedAtDesc().get(0);
        List<CampaignTarget> targetList = targets.findByCampaignIdOrderByIdAsc(campaign.id());
        assertEquals(4, targetList.size());

        // 3. 每人 token 独立且互不相同
        Set<String> tokens = new HashSet<>();
        targetList.forEach(t -> assertTrue(tokens.add(t.token())));
        assertEquals(4, tokens.size());

        CampaignTarget t = targetList.get(0);

        // 4. 打开信标 → GIF
        HttpResponse<byte[]> pixel = client.toBlocking().exchange(
                HttpRequest.GET("/o/" + t.token()), Argument.of(byte[].class));
        assertEquals(HttpStatus.OK, pixel.status());
        assertEquals(MediaType.IMAGE_GIF, pixel.header("Content-Type"));
        assertEquals("OPENED", targets.findById(t.id()).orElseThrow().status());

        // 5. 点击链接 → 仿冒登录页，表单 action 带本人 token
        HttpResponse<String> landing = client.toBlocking().exchange(
                HttpRequest.GET("/c/" + t.token()), Argument.STRING);
        assertEquals(HttpStatus.OK, landing.status());
        assertTrue(landing.body().contains("/c/" + t.token()));
        assertEquals("CLICKED", targets.findById(t.id()).orElseThrow().status());

        // 6. 提交“口令”——请求体里确实带了真实口令，但控制器不接收 body
        HttpResponse<?> submit = client.toBlocking().exchange(
                HttpRequest.POST("/c/" + t.token(),
                                "username=wang.fang&password=" + FAKE_PASSWORD)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED));
        assertEquals(HttpStatus.SEE_OTHER, submit.status());
        assertEquals("/reveal/" + t.token(), submit.header("Location"));
        assertEquals("SUBMITTED", targets.findById(t.id()).orElseThrow().status());

        // 7. 合规断言①：追踪表与目标表的列结构里没有任何可存放凭证/请求体的列
        assertColumnsExactly("tracking_events", Set.of("ID", "TARGET_ID", "TYPE", "AT"));
        assertColumnsExactly("campaign_targets",
                Set.of("ID", "CAMPAIGN_ID", "EMPLOYEE_ID", "TOKEN", "STATUS",
                        "SENT_AT", "OPENED_AT", "CLICKED_AT", "SUBMITTED_AT", "REPORTED_AT"));

        // 8. 合规断言②：全库文本列扫描，确认口令字符串不存在于任何表
        assertPasswordNowhereInDatabase(FAKE_PASSWORD);

        // 9. 主动上报（与点击独立统计）
        HttpResponse<?> report = client.toBlocking().exchange(
                HttpRequest.POST("/mailbox/report/" + t.id(), "email=wang.fang@corp.local")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED));
        assertEquals(HttpStatus.OK, report.status());
        assertNotNull(targets.findById(t.id()).orElseThrow().reportedAt());

        // 10. 点击已自动分派短课，且只派一次
        assertEquals(1, training.count());
        var assignment = training.findAll().iterator().next();
        assertNull(assignment.completedAt());

        HttpResponse<?> complete = client.toBlocking().exchange(
                HttpRequest.POST("/mailbox/" + t.employeeId() + "/training/complete", "")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED));
        assertEquals(HttpStatus.SEE_OTHER, complete.status());
        assertNotNull(training.findById(assignment.id()).orElseThrow().completedAt());

        // 11. 统计：部门漏斗与趋势
        List<DeptStat> deptStats = stats.departmentStats(campaign.id());
        DeptStat finance = deptStats.stream().filter(d -> d.departmentId() == 1).findFirst().orElseThrow();
        assertEquals(4, finance.total());
        assertEquals(1, finance.clicked());
        assertEquals(1, finance.submitted());
        assertEquals(1, finance.reported());
        assertEquals(25.0, finance.clickRate());

        // 12. 再发起一轮，趋势应包含两轮数据
        client.toBlocking().exchange(
                HttpRequest.POST("/admin/campaigns",
                                "name=测试演练-第二轮&emailTemplateId=2&landingPageId=2&departmentIds=1&departmentIds=2")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED));
        List<TrendPoint> trend = stats.trend();
        assertTrue(trend.size() >= 10); // 2 轮 × 5 部门
        long rounds = trend.stream().map(TrendPoint::campaignId).distinct().count();
        assertEquals(2, rounds);
    }

    @Test
    void unknownTokenReturns404() {
        HttpClientResponseException ex = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/c/does-not-exist"), Argument.STRING));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void launchWithoutDepartmentRejected() {
        HttpClientResponseException ex = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(
                        HttpRequest.POST("/admin/campaigns",
                                        "name=空演练&emailTemplateId=1&landingPageId=1")
                                .header("Authorization", ADMIN)
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    private void assertColumnsExactly(String table, Set<String> expected) throws Exception {
        Set<String> actual = new HashSet<>();
        // 用 ResultSetMetaData 取列名，规避 H2 DATABASE_TO_LOWER 下元数据标识符大小写问题
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + table + " WHERE 1 = 0")) {
            java.sql.ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                actual.add(md.getColumnName(i).toUpperCase());
            }
        }
        assertEquals(expected, actual, "表 " + table + " 的列结构与预期不符");
    }

    private void assertPasswordNowhereInDatabase(String secret) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            String[] tables = {"departments", "employees", "email_templates", "landing_pages",
                    "campaigns", "campaign_targets", "tracking_events", "training_assignments"};
            for (String table : tables) {
                try (ResultSet rs = c.createStatement().executeQuery("SELECT * FROM " + table)) {
                    int cols = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        for (int i = 1; i <= cols; i++) {
                            String value = rs.getString(i);
                            if (value != null) {
                                assertFalse(value.contains(secret),
                                        "口令出现在表 " + table + " 的第 " + i + " 列——合规红线被破坏");
                            }
                        }
                    }
                }
            }
        }
    }
}
