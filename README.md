# 安全意识钓鱼演练平台（Phish Drill）

面向企业内部的**授权安全意识演练系统**：按部门挑选员工，向本地模拟邮箱投递仿冒邮件，
追踪每人的打开 / 点击 / 凭证提交 / 主动上报行为，对点击者自动分派安全短课，
并按部门输出跨轮次趋势对比，用于持续降低员工点击钓鱼链接的比例。

> 本系统仅用于企业内部授权的安全意识培训，所有数据只记录**事件类型**，
> **绝不保存任何人输入的口令或表单内容**。

## 技术栈

- Java 17 + Micronaut 4.7（Platform BOM）
- Micronaut Data JDBC + H2 文件数据库（零外部依赖，开箱即用）
- Micronaut Security（管理后台 HTTP Basic 认证）
- 纯 Java 生成内联 SVG 图表，无前端框架依赖
- Gradle 构建，JUnit 5 集成测试

## 快速开始

```bash
# 需要 JDK 17+
gradle run                      # 默认 http://localhost:8080
MICRONAUT_SERVER_PORT=8090 gradle run   # 如 8080 被占用

# 管理后台（HTTP Basic）
#   默认账号 admin / changeme，生产环境务必用环境变量覆盖：
ADMIN_USER=xxx ADMIN_PASSWORD=yyy gradle run

# 运行测试
gradle test
gradle clean build
```

| 入口 | 地址 | 认证 |
|------|------|------|
| 管理后台总览 | `/admin` | Basic（ROLE_ADMIN） |
| 发起演练 | `/admin/campaigns/new` | Basic |
| 部门趋势 | `/admin/trends` | Basic |
| 短课分派 | `/admin/training` | Basic |
| 本地模拟邮箱 | `/mailbox` | 匿名 |

## 演练全流程

1. **发起演练**：管理员选择邮件模板、仿冒落地页、一个或多个部门，系统为每个收件人
   生成全局唯一的不可猜测 token（192 bit 熵，URL-safe Base64）。
2. **投递到模拟邮箱**：邮件不经过真实 SMTP，员工在 `/mailbox` 选择身份查看，
   邮件中的追踪链接形如 `/c/{token}`，每人独立。
3. **四类事件追踪**（追加写入 `tracking_events`，状态机只进不退）：
   - `OPEN`：邮件里的 1×1 透明 GIF 信标 `/o/{token}` 被加载
   - `CLICK`：点击链接，渲染仿冒登录页
   - `SUBMIT`：在登录页提交（**只记录“发生了提交”，不读不存任何输入**）
   - `REPORT`：员工在模拟邮箱点击「上报可疑邮件」，正向指标单独统计
4. **自动派课**：员工首次点击即自动分派一节短课（一人一次），学完可标记完成。
5. **统计趋势**：按部门输出打开率 / 点击率 / 提交率 / 上报率漏斗，
   以及跨演练轮次的点击率折线对比。

## 口令不落库的设计保障

这是本平台最重要的合规红线，从代码结构层面保证，而非仅靠约定：

1. **控制器不接收请求体**：凭证提交接口 `POST /c/{token}` 的方法签名里
   **没有 `@Body`、没有任何表单参数**，框架不会把用户名/口令绑定进应用代码，
   业务层和持久层在物理上接触不到凭证。
2. **表结构无可存放凭证的列**：`tracking_events` 只有 `target_id / type / at`，
   集成测试通过 JDBC 元数据断言列结构，并对全库做口令字符串扫描，要求 0 匹配。
3. **只存事件枚举**：凭证提交仅产生一条 `type=SUBMIT` 事件。
4. 揭示页明确提示：若员工输入了真实口令应尽快修改。

## 数据模型

| 表 | 职责 |
|----|------|
| `departments` / `employees` | 部门与员工（含种子数据：5 部门 20 人） |
| `email_templates` / `landing_pages` | 仿冒邮件模板与登录页（启动时从 classpath 注入） |
| `campaigns` | 演练批次 |
| `campaign_targets` | 演练 × 收件人，持有独立 token 与各阶段时间戳 |
| `tracking_events` | 追加型事件日志（OPEN/CLICK/SUBMIT/REPORT），不含任何内容 |
| `training_assignments` | 点击后自动分派的短课及完成状态 |

## 关键工程说明

- **数据库初始化**：当前 micronaut-jdbc-hikari 不内置 `sql-init`，由
  `DatabaseInitializer` 在 DataSource bean 创建时（`BeanCreatedEventListener`）
  unwrap 底层 Hikari 连接执行幂等的 `schema.sql` / `seed.sql`。
- **可空 record 组件**：可空时间戳字段标注 `io.micronaut.core.annotation.Nullable`，
  使数据库 NULL 能映射到 record 组件。
- **标量外键不加 `@Relation`**：`@Relation` 仅用于实体对象关联；标量 `Long` 外键
  加该注解会导致 Micronaut Data 无法识别主键、仓储编译失败。
- **安全规则**：通过 `intercept-url-map` 显式区分匿名端点（追踪/邮箱/静态资源）
  与 `ROLE_ADMIN` 端点（`/admin/**`）；表单 POST 显式声明 `@Consumes`。

## 目录结构

```
src/main/java/com/secaware/phishdrill/
├── config/       # 数据库初始化
├── domain/       # Micronaut Data record 实体
├── repo/         # JDBC 仓储
├── security/     # 管理员认证 Provider
├── service/      # 演练、追踪状态机、派课、统计、模板渲染
└── web/          # 管理后台、追踪、模拟邮箱控制器与 SVG 图表
src/main/resources/
├── application.yml
├── db/           # schema.sql + seed.sql
├── templates/    # 2 套邮件模板 + 2 个仿冒落地页
└── assets/       # 后台样式
```
