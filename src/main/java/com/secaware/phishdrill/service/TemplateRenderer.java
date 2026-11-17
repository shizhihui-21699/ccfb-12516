package com.secaware.phishdrill.service;

import jakarta.inject.Singleton;

import java.util.Map;

/**
 * 极简 {{placeholder}} 占位符渲染，自带 HTML 转义。
 * 演练内容固定且受控，无需引入完整模板引擎。
 */
@Singleton
public class TemplateRenderer {

    public String render(String template, Map<String, String> values) {
        String out = template;
        for (Map.Entry<String, String> e : values.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", escape(e.getValue()));
        }
        return out;
    }

    /** 不转义的替换，仅用于已经渲染好的 HTML 片段。 */
    public String renderRaw(String template, Map<String, String> values) {
        String out = template;
        for (Map.Entry<String, String> e : values.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return out;
    }

    private static String escape(String s) {
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
}
