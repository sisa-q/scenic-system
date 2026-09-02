package com.scenic.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 显存监控（nvidia-smi 周期采样）——驱动四区阈值降级：
 *   安全 ≤65% / 警戒 65-85% / 危险 85-90% / 超限 >90%（与《最终形态》文档一致）
 */
@Component
public class VramMonitor {

    public static final int SAFE = 0;
    public static final int WARN = 1;
    public static final int DANGER = 2;
    public static final int OVER = 3;

    private final AtomicLong usedMiB = new AtomicLong(0);
    private final AtomicLong totalMiB = new AtomicLong(0);
    private final AtomicLong lastOkAt = new AtomicLong(0);

    /** 每 5 秒采样一次显存占用 */
    @Scheduled(fixedDelay = 5000, initialDelay = 3000)
    public void poll() {
        try {
            Process p = new ProcessBuilder("nvidia-smi",
                    "--query-gpu=memory.used,memory.total",
                    "--format=csv,noheader,nounits")
                    .redirectErrorStream(true).start();
            String line = null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                line = br.readLine();
            }
            p.waitFor();
            if (line != null) {
                String[] parts = line.trim().split(",");
                if (parts.length >= 2) {
                    usedMiB.set(Long.parseLong(parts[0].trim()));
                    totalMiB.set(Long.parseLong(parts[1].trim()));
                    lastOkAt.set(System.currentTimeMillis());
                }
            }
        } catch (Exception e) {
            // nvidia-smi 不可用（无独显/非 Windows）→ 视为无显存约束（安全区）
        }
    }

    public boolean available() { return lastOkAt.get() > 0; }

    public long usedMiB() { return usedMiB.get(); }

    public long totalMiB() { return totalMiB.get(); }

    /** 显存占用比例 0~1 */
    public double ratio() {
        long t = totalMiB.get();
        return t > 0 ? (double) usedMiB.get() / t : 0;
    }

    /** 四区判定 */
    public int zone() {
        if (!available()) return SAFE;
        double r = ratio();
        if (r > 0.90) return OVER;
        if (r > 0.85) return DANGER;
        if (r > 0.65) return WARN;
        return SAFE;
    }

    public String zoneLabel() {
        switch (zone()) {
            case OVER: return "超限";
            case DANGER: return "危险";
            case WARN: return "警戒";
            default: return "安全";
        }
    }

    /** 供接口/前端展示的资源快照 */
    public Map<String, Object> info() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", available());
        m.put("usedMiB", usedMiB.get());
        m.put("totalMiB", totalMiB.get());
        m.put("ratio", Math.round(ratio() * 1000) / 10.0);
        m.put("zone", zone());
        m.put("zoneLabel", zoneLabel());
        return m;
    }
}
