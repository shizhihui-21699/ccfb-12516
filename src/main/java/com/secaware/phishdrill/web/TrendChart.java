package com.secaware.phishdrill.web;

import com.secaware.phishdrill.service.stats.TrendPoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯 Java 生成内联 SVG 多系列折线图：
 * X 轴 = 演练轮次（按开始时间排序），每条线一个部门，Y 轴 = 点击率百分比。
 * 不依赖任何 JS / 图表库。
 */
public final class TrendChart {

    private static final String[] COLORS = {
            "#1d4ed8", "#b91c1c", "#15803d", "#b45309", "#7c3aed", "#0e7490"
    };

    private TrendChart() {
    }

    public static String clickRateSvg(List<TrendPoint> points) {
        // 按出现顺序收集演练（X 轴）和部门（系列）
        Map<Long, String> campaignNames = new LinkedHashMap<>();
        Map<Long, String> deptNames = new LinkedHashMap<>();
        for (TrendPoint p : points) {
            campaignNames.putIfAbsent(p.campaignId(), p.campaignName());
            deptNames.putIfAbsent(p.departmentId(), p.departmentName());
        }
        if (campaignNames.isEmpty()) {
            return "<p class=\"muted\">发起至少一轮演练后，这里会出现趋势图。</p>";
        }

        List<Long> campaignIds = new ArrayList<>(campaignNames.keySet());
        int width = 860;
        int height = 380;
        int padL = 48, padR = 180, padT = 24, padB = 70;
        int plotW = width - padL - padR;
        int plotH = height - padT - padB;

        StringBuilder svg = new StringBuilder();
        svg.append("<svg width=\"").append(width).append("\" height=\"").append(height)
           .append("\" viewBox=\"0 0 ").append(width).append(' ').append(height)
           .append("\" role=\"img\" style=\"max-width:100%;height:auto\">");

        // 网格 + Y 轴刻度（0–100%）
        for (int g = 0; g <= 4; g++) {
            int pct = g * 25;
            int y = padT + plotH - (plotH * g / 4);
            svg.append("<line x1=\"").append(padL).append("\" y1=\"").append(y)
               .append("\" x2=\"").append(padL + plotW).append("\" y2=\"").append(y)
               .append("\" stroke=\"#e2e8f0\" stroke-width=\"1\"/>");
            svg.append("<text x=\"").append(padL - 8).append("\" y=\"").append(y + 4)
               .append("\" text-anchor=\"end\" font-size=\"11\" fill=\"#64748b\">").append(pct).append("%</text>");
        }

        // X 轴演练标签
        int n = Math.max(campaignIds.size(), 1);
        for (int i = 0; i < campaignIds.size(); i++) {
            int x = padL + (campaignIds.size() == 1 ? plotW / 2 : plotW * i / (campaignIds.size() - 1));
            String label = "第" + (i + 1) + "轮";
            svg.append("<text x=\"").append(x).append("\" y=\"").append(padT + plotH + 20)
               .append("\" text-anchor=\"middle\" font-size=\"11\" fill=\"#475569\">").append(label).append("</text>");
            svg.append("<text x=\"").append(x).append("\" y=\"").append(padT + plotH + 36)
               .append("\" text-anchor=\"middle\" font-size=\"10\" fill=\"#94a3b8\">")
               .append(Html.esc(truncate(campaignNames.get(campaignIds.get(i)), 10))).append("</text>");
        }

        // 每个部门一条折线
        int colorIdx = 0;
        for (Map.Entry<Long, String> dept : deptNames.entrySet()) {
            String color = COLORS[colorIdx % COLORS.length];
            List<double[]> coords = new ArrayList<>(); // x, y, rate
            for (int i = 0; i < campaignIds.size(); i++) {
                Long campaignId = campaignIds.get(i);
                TrendPoint match = points.stream()
                        .filter(p -> p.campaignId() == campaignId && p.departmentId() == dept.getKey())
                        .findFirst().orElse(null);
                if (match == null || match.total() == 0) {
                    continue;
                }
                int x = padL + (campaignIds.size() == 1 ? plotW / 2 : plotW * i / (campaignIds.size() - 1));
                int y = padT + plotH - (int) Math.round(plotH * match.clickRate() / 100.0);
                coords.add(new double[]{x, y, match.clickRate()});
            }
            if (coords.isEmpty()) {
                colorIdx++;
                continue;
            }
            StringBuilder path = new StringBuilder();
            for (int j = 0; j < coords.size(); j++) {
                path.append(j == 0 ? "M" : "L").append((int) coords.get(j)[0])
                   .append(',').append((int) coords.get(j)[1]).append(' ');
            }
            svg.append("<path d=\"").append(path).append("\" fill=\"none\" stroke=\"").append(color)
               .append("\" stroke-width=\"2.2\"/>");
            for (double[] c : coords) {
                svg.append("<circle cx=\"").append((int) c[0]).append("\" cy=\"").append((int) c[1])
                   .append("\" r=\"3.5\" fill=\"").append(color).append("\"/>");
                svg.append("<text x=\"").append((int) c[0]).append("\" y=\"").append((int) c[1] - 8)
                   .append("\" text-anchor=\"middle\" font-size=\"10\" fill=\"").append(color).append("\">")
                   .append(String.format("%.1f%%", c[2])).append("</text>");
            }
            // 图例
            int legendY = padT + 22 * colorIdx;
            svg.append("<rect x=\"").append(padL + plotW + 18).append("\" y=\"").append(legendY - 9)
               .append("\" width=\"14\" height=\"4\" fill=\"").append(color).append("\"/>");
            svg.append("<text x=\"").append(padL + plotW + 38).append("\" y=\"").append(legendY - 4)
               .append("\" font-size=\"12\" fill=\"#334155\">").append(Html.esc(dept.getValue())).append("</text>");
            colorIdx++;
        }

        svg.append("<text x=\"").append(padL).append("\" y=\"14\" font-size=\"12\" fill=\"#64748b\">")
           .append("点击率（%）· 数值越低越好").append("</text>");
        svg.append("</svg>");
        return svg.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
