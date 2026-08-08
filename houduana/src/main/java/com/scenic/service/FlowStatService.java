package com.scenic.service;

import java.util.Map;

public interface FlowStatService {
    Map<String, Object> getStats();
    Map<String, Object> getRealtime();

    /** 核销入园后记录客流（驱动客流统计模块更新） */
    void recordEntry(com.scenic.entity.Order order);
}