-- 钓鱼演练平台数据模型（H2，MySQL 兼容模式）
-- 所有追踪数据只记录“发生了什么事件”，绝不记录任何输入内容。

CREATE TABLE IF NOT EXISTS departments (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS employees (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(80)  NOT NULL,
    email         VARCHAR(160) NOT NULL UNIQUE,
    department_id BIGINT       NOT NULL,
    CONSTRAINT fk_emp_dept FOREIGN KEY (department_id) REFERENCES departments (id)
);

CREATE TABLE IF NOT EXISTS email_templates (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    subject     VARCHAR(200) NOT NULL,
    body_html   CLOB         NOT NULL,
    from_name   VARCHAR(120) NOT NULL,
    from_email  VARCHAR(160) NOT NULL
);

CREATE TABLE IF NOT EXISTS landing_pages (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(120) NOT NULL,
    html      CLOB         NOT NULL
);

CREATE TABLE IF NOT EXISTS campaigns (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(160) NOT NULL,
    email_template_id BIGINT       NOT NULL,
    landing_page_id   BIGINT       NOT NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    started_at        TIMESTAMP,
    CONSTRAINT fk_camp_tpl FOREIGN KEY (email_template_id) REFERENCES email_templates (id),
    CONSTRAINT fk_camp_lp  FOREIGN KEY (landing_page_id)   REFERENCES landing_pages (id)
);

-- 每个“演练 × 收件人”一行，token 全局唯一，驱动全部追踪链接
CREATE TABLE IF NOT EXISTS campaign_targets (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    campaign_id        BIGINT      NOT NULL,
    employee_id        BIGINT      NOT NULL,
    token              VARCHAR(64) NOT NULL UNIQUE,
    status             VARCHAR(16) NOT NULL DEFAULT 'SENT', -- SENT < OPENED < CLICKED < SUBMITTED
    sent_at            TIMESTAMP   NOT NULL,
    opened_at          TIMESTAMP,
    clicked_at         TIMESTAMP,
    submitted_at       TIMESTAMP,
    reported_at        TIMESTAMP,
    CONSTRAINT fk_tgt_camp FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    CONSTRAINT fk_tgt_emp  FOREIGN KEY (employee_id) REFERENCES employees (id)
);
CREATE INDEX IF NOT EXISTS idx_targets_campaign ON campaign_targets (campaign_id);
CREATE INDEX IF NOT EXISTS idx_targets_employee ON campaign_targets (employee_id);

-- 追加型事件日志：只存事件类型，不存任何请求体/表单值
CREATE TABLE IF NOT EXISTS tracking_events (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_id BIGINT      NOT NULL,
    type      VARCHAR(16) NOT NULL, -- OPEN / CLICK / SUBMIT / REPORT
    at        TIMESTAMP   NOT NULL,
    CONSTRAINT fk_evt_tgt FOREIGN KEY (target_id) REFERENCES campaign_targets (id)
);
CREATE INDEX IF NOT EXISTS idx_events_target ON tracking_events (target_id);

CREATE TABLE IF NOT EXISTS training_assignments (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT    NOT NULL UNIQUE,
    target_id   BIGINT    NOT NULL,
    assigned_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT fk_train_emp FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_train_tgt FOREIGN KEY (target_id)  REFERENCES campaign_targets (id)
);
