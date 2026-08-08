package com.scenic.service;

import com.scenic.entity.Evaluation;

import java.util.Map;

public interface EvaluationService {
    /** 分页 + 多条件筛选（管理端） */
    Map<String, Object> listAll(int page, int size, String orderNo, String spotName, Integer rating,
                                String startDate, String endDate);

    /** 提交评价 */
    Evaluation submit(Evaluation evaluation, Long userId);

    /** 更新评价（支持传 id 或仅传 orderId） */
    Evaluation update(Evaluation evaluation);

    /** 查找某订单的评价，无则返回 null */
    Evaluation getByOrderId(Long orderId);

    void delete(Long id);
}
