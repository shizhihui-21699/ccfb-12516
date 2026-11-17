package com.secaware.phishdrill.web;

/** HTML 输出小工具：统一转义与管理后台页面骨架。 */
public final class Html {

    private Html() {
    }

    public static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String pct(double v) {
        return String.format("%.2f%%", v);
    }

    /** 管理后台页面骨架。 */
    public static String adminPage(String title, String activeNav, String body) {
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s · 钓鱼演练平台</title>
                <link rel="stylesheet" href="/assets/admin.css">
                </head>
                <body>
                <header class="topbar">
                  <div class="brand">🎣 安全意识钓鱼演练平台</div>
                  <nav>
                    %s
                    <a class="nav" href="/mailbox">模拟邮箱 ↗</a>
                  </nav>
                </header>
                <main class="container">
                %s
                </main>
                <footer class="foot">内部授权演练系统 · 追踪数据仅记录事件类型，不保存任何输入口令</footer>
                </body>
                </html>
                """.formatted(esc(title), nav(activeNav), body);
    }

    private static String nav(String active) {
        String[][] items = {
                {"/admin", "总览"},
                {"/admin/campaigns/new", "发起演练"},
                {"/admin/trends", "部门趋势"},
                {"/admin/training", "短课分派"},
        };
        StringBuilder sb = new StringBuilder();
        for (String[] item : items) {
            String cls = item[0].equals(active) || ("/admin/campaigns".equals(active) && item[0].equals("/admin/campaigns/new"))
                    ? "nav active" : "nav";
            sb.append("<a class=\"").append(cls).append("\" href=\"")
              .append(item[0]).append("\">").append(item[1]).append("</a>");
        }
        return sb.toString();
    }
}
