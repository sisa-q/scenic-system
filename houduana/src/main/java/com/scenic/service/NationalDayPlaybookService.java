package com.scenic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 国庆 10 天模拟剧本数据服务（与前端 dataSimulator.js 同源，由 tools/gen_curves.mjs 导出）。
 * 运营决策 / 应急调度智能体的数据底座：只读剧本，不依赖业务库，模拟与真实业务隔离。
 */
@Service
public class NationalDayPlaybookService {

    public static final int CAPACITY = 80000;                    // 故宫日承载上限
    public static final double AVG_PRICE = 36.55;                // 加权客单价：成人60%*50+儿童20%*10+学生15%*30+优待5%*1
    public static final double BASE_REFUND_RATE = 0.02;          // 平日退款率基线

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode days = null;

    @PostConstruct
    public void load() {
        try (InputStream in = new ClassPathResource("data/national_day_curves.json").getInputStream()) {
            days = objectMapper.readTree(in).path("days");
        } catch (Exception e) {
            days = null;
        }
    }

    public boolean available() { return days != null && days.size() > 0; }

    public int dayCount() { return days == null ? 0 : days.size(); }

    public JsonNode day(int index) {
        if (days == null || index < 0 || index >= days.size()) return null;
        return days.get(index);
    }

    public int indexOf(String date) {
        if (days == null || date == null || date.isBlank()) return -1;
        for (int i = 0; i < days.size(); i++) {
            if (date.equals(days.get(i).path("date").asText())) return i;
        }
        return -1;
    }

    // ==================== 派生指标 ====================
    public static double carryRate(double entries) { return entries / CAPACITY * 100; }

    public static double salesOf(double entries) { return entries * AVG_PRICE; }

    public static double refundsOf(double entries, double rate) { return entries * rate; }

    public String peakHourOf(JsonNode day) {
        int best = -1;
        double max = -1;
        for (JsonNode h : day.path("hourly")) {
            double v = h.path("inPark").asDouble();
            if (v > max) { max = v; best = h.path("hour").asInt(); }
        }
        return best < 0 ? "-" : String.format("%02d:00", best);
    }

    // ==================== 全窗口概览（注入提示词） ====================
    public String overview() {
        if (!available()) return "（国庆模拟数据未加载）";
        StringBuilder sb = new StringBuilder();
        sb.append("【国庆 10 天模拟数据窗口 2026-09-28 ~ 2026-10-07，故宫日承载上限 80000】\n");
        sb.append("日期 | 阶段 | 日入园 | 承载率 | 在园峰值 | 上午/下午预约率 | 退款率\n");
        for (int i = 0; i < days.size(); i++) {
            JsonNode d = days.get(i);
            double entries = d.path("totalEntries").asDouble();
            sb.append(d.path("date").asText()).append(" | ").append(d.path("phase").asText())
              .append(" | ").append((int) entries).append(" | ").append(r1(carryRate(entries))).append("%")
              .append(" | ").append(d.path("peakInPark").asInt())
              .append(" | ").append(pct(d.path("amBookedRatio").asDouble())).append("/").append(pct(d.path("pmBookedRatio").asDouble()))
              .append(" | ").append(pct(d.path("refundRate").asDouble())).append("\n");
        }
        return sb.toString();
    }

    // ==================== 运营分析：销售 ====================
    public String salesSummary(String from, String to) {
        List<JsonNode> list = range(from, to);
        if (list.isEmpty()) return "日期范围不在 2026-09-28 ~ 2026-10-07 窗口内";
        StringBuilder sb = new StringBuilder();
        double orders = 0, tickets = 0, sales = 0, refunds = 0, refundAmt = 0;
        for (JsonNode d : list) {
            double entries = d.path("totalEntries").asDouble();
            double rate = d.path("refundRate").asDouble();
            orders += d.path("totalOrders").asDouble();
            tickets += entries;
            sales += salesOf(entries);
            refunds += refundsOf(entries, rate);
            refundAmt += salesOf(entries) * rate;
            sb.append(d.path("date").asText()).append(" 订单 ").append(d.path("totalOrders").asInt())
              .append(" 张票 ").append((int) entries).append(" 销售额 ").append(yuan(salesOf(entries)))
              .append(" 退款率 ").append(pct(rate)).append("\n");
        }
        sb.append("汇总：订单 ").append((long) orders).append(" 张票 ").append((long) tickets)
          .append(" 销售额约 ").append(yuan(sales)).append(" 退款申请约 ").append((long) refunds)
          .append(" 笔（").append(yuan(refundAmt)).append("）");
        return sb.toString();
    }

    // ==================== 运营分析：客流 ====================
    public String flowSummary(String from, String to) {
        List<JsonNode> list = range(from, to);
        if (list.isEmpty()) return "日期范围不在窗口内";
        StringBuilder sb = new StringBuilder();
        double total = 0, peak = 0;
        String peakDay = "-";
        for (JsonNode d : list) {
            double entries = d.path("totalEntries").asDouble();
            double pk = d.path("peakInPark").asDouble();
            total += entries;
            if (pk > peak) { peak = pk; peakDay = d.path("date").asText(); }
            sb.append(d.path("date").asText()).append(" 阶段[").append(d.path("phase").asText())
              .append("] 日入园 ").append((int) entries).append(" 承载率 ").append(r1(carryRate(entries))).append("%")
              .append(" 在园峰值 ").append((int) pk).append("（").append(r1(carryRate(pk))).append("%）")
              .append(" 峰值时刻 ").append(peakHourOf(d)).append("\n");
        }
        sb.append("汇总：区间入园合计约 ").append((long) total).append(" 人，日均 ").append((long) (total / list.size()))
          .append(" 人；在园峰值 ").append((int) peak).append(" 人（").append(peakDay).append("）");
        return sb.toString();
    }

    // ==================== 运营分析：时段预约率 ====================
    public String occupancySummary(String from, String to) {
        List<JsonNode> list = range(from, to);
        if (list.isEmpty()) return "日期范围不在窗口内";
        StringBuilder sb = new StringBuilder();
        int soldOut = 0, warn = 0;
        for (JsonNode d : list) {
            double am = d.path("amBookedRatio").asDouble(), pm = d.path("pmBookedRatio").asDouble();
            int amPct = (int) Math.round(am * 100), pmPct = (int) Math.round(pm * 100);
            if (amPct >= 100 || pmPct >= 100) soldOut++;
            else if (amPct >= 95 || pmPct >= 95) warn++;
            sb.append(d.path("date").asText()).append(" 上午 ").append(amPct).append("%（").append((int) (d.path("amQuota").asDouble() * am))
              .append("/").append(d.path("amQuota").asInt()).append("） 下午 ").append(pmPct).append("%（")
              .append((int) (d.path("pmQuota").asDouble() * pm)).append("/").append(d.path("pmQuota").asInt()).append("）")
              .append(amPct >= 100 || pmPct >= 100 ? "【售罄】" : (amPct >= 95 || pmPct >= 95 ? "【近售罄】" : "")).append("\n");
        }
        sb.append("汇总：售罄时段日 ").append(soldOut).append(" 天，接近售罄 ").append(warn).append(" 天");
        return sb.toString();
    }

    // ==================== 运营分析：退款 ====================
    public String refundSummary(String from, String to) {
        List<JsonNode> list = range(from, to);
        if (list.isEmpty()) return "日期范围不在窗口内";
        StringBuilder sb = new StringBuilder();
        double base = BASE_REFUND_RATE;
        for (JsonNode d : list) {
            double entries = d.path("totalEntries").asDouble();
            double rate = d.path("refundRate").asDouble();
            double multi = rate / base;
            sb.append(d.path("date").asText()).append(" 退款率 ").append(pct(rate)).append("（基线 ").append(pct(base))
              .append("，").append(r1(multi)).append(" 倍） 退款申请约 ").append((long) refundsOf(entries, rate)).append(" 笔")
              .append(rate >= base * 3 ? "【退款激增】" : "").append("\n");
        }
        return sb.toString();
    }

    // ==================== 天气 ====================
    public String weatherForecast(int daysAhead) {
        if (!available()) return "（无数据）";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < days.size(); i++) {
            JsonNode d = days.get(i);
            JsonNode w = d.path("weather");
            sb.append(d.path("date").asText()).append(" ").append(w.path("text").asText()).append(" ")
              .append(w.path("temp").asInt()).append("℃ 降雨概率 ").append(w.path("rainProb").asInt()).append("% 风力 ")
              .append(w.path("windLevel").asInt()).append("级");
            sb.append(alertOf(w)).append("\n");
        }
        return sb.toString();
    }

    private String alertOf(JsonNode w) {
        List<String> a = new ArrayList<>();
        if (w.path("rainProb").asInt() >= 70) a.add("降雨预警");
        if (w.path("temp").asInt() >= 38) a.add("高温预警");
        if (w.path("windLevel").asInt() >= 5) a.add("大风预警");
        if (w.path("temp").asInt() <= -10) a.add("严寒预警");
        return a.isEmpty() ? "" : "【" + String.join("+", a) + "】";
    }

    // ==================== 应急扫描 ====================
    public String emergencyScan(String date) {
        List<JsonNode> list = date == null || date.isBlank() ? all() : range(date, date);
        if (list.isEmpty()) return "日期不在窗口内";
        StringBuilder sb = new StringBuilder("【应急态势扫描】\n");
        int danger = 0;
        for (JsonNode d : list) {
            String tag = d.path("date").asText() + " ";
            double entries = d.path("totalEntries").asDouble();
            double cr = carryRate(entries);
            double pk = d.path("peakInPark").asDouble();
            int am = (int) Math.round(d.path("amBookedRatio").asDouble() * 100);
            int pm = (int) Math.round(d.path("pmBookedRatio").asDouble() * 100);
            double refund = d.path("refundRate").asDouble();
            boolean hit = false;
            if (cr > 90) { sb.append(tag).append("【超限】承载率 ").append(r1(cr)).append("% >90%，需立即限流\n"); danger++; hit = true; }
            else if (cr > 85) { sb.append(tag).append("【危险】承载率 ").append(r1(cr)).append("%（85-90%），启动限流准备\n"); danger++; hit = true; }
            else if (cr > 65) { sb.append(tag).append("【警戒】承载率 ").append(r1(cr)).append("%（65-85%），自动瘦身/错峰引导\n"); hit = true; }
            if (pk > 0.85 * CAPACITY) { sb.append(tag).append("【危险】在园瞬时峰值 ").append((int) pk).append(" 人 > 85% 承载\n"); danger++; hit = true; }
            if (am >= 100 || pm >= 100) { sb.append(tag).append("【危险】时段售罄（上午 ").append(am).append("%/下午 ").append(pm).append("%），建议加开时段\n"); danger++; hit = true; }
            else if (am >= 95 || pm >= 95) { sb.append(tag).append("【警戒】时段接近售罄（上午 ").append(am).append("%/下午 ").append(pm).append("%）\n"); hit = true; }
            if (refund >= 0.06) { sb.append(tag).append("【危险】退款率 ").append(pct(refund)).append(" 为基线 3 倍，退款激增，排查原因\n"); danger++; hit = true; }
            String w = alertOf(d.path("weather"));
            if (!w.isEmpty()) { sb.append(tag).append("【警戒】").append(w).append("\n"); hit = true; }
            if (!hit) sb.append(tag).append("安全\n");
        }
        sb.append("汇总：共识别异常 ").append(danger).append(" 项");
        return sb.toString();
    }

    // ==================== 工具 ====================
    private List<JsonNode> all() {
        List<JsonNode> list = new ArrayList<>();
        if (days != null) for (JsonNode d : days) list.add(d);
        return list;
    }

    private List<JsonNode> range(String from, String to) {
        List<JsonNode> list = new ArrayList<>();
        if (days == null) return list;
        int a = from == null || from.isBlank() ? 0 : indexOf(from);
        int b = to == null || to.isBlank() ? days.size() - 1 : indexOf(to);
        if (a < 0) a = 0;
        if (b < 0) b = days.size() - 1;
        if (a > b) { int t = a; a = b; b = t; }
        for (int i = a; i <= b && i < days.size(); i++) list.add(days.get(i));
        return list;
    }

    private static String pct(double r) { return Math.round(r * 100) + "%"; }

    private static double r1(double v) { return Math.round(v * 10) / 10.0; }

    private static String yuan(double v) { return String.format("%,.0f 元", v); }
}
