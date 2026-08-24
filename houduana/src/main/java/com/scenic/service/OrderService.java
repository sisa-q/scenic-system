package com.scenic.service;

import com.scenic.entity.Order;
import java.util.List;
import java.util.Map;

public interface OrderService {
    List<Order> listOrders(Integer status, String key, Long userId, String role);

    /** 分页查询订单（数据库分页），返回 {list, total} */
    Map<String, Object> pageOrders(Integer status, String key, Long userId, String role, int page, int size);
    Order getById(Long id);
    Order createOrder(Order order, Long userId);  // 增加 userId 参数
    /** \u652f\u4ed8\uff08\u6821\u9a8c\u64cd\u4f5c\u4eba\u8eab\u4efd\uff0c\u975e\u7ba1\u7406\u5458\u53ea\u80fd\u652f\u4ed8\u81ea\u5df1\u7684\u8ba2\u5355\uff09 */
    void payOrder(Long id, Long operatorId, String role);

    /** 申请退款（游客端提交退款申请，仅登记“退款申请中”，绝不自动退款） */
    void applyRefund(Long id, Long operatorId);

    /** 管理员退款（仅管理员角色可真正退款） */
    void refundOrder(Long id, Long operatorId, String role);

    /** \u53d6\u6d88\u9000\u6b3e\u7533\u8bf7\uff08\u6e38\u5ba2\u6216\u7ba1\u7406\u5458\u53ef\u64cd\u4f5c\uff0c\u4ec5\u9000\u6b3e\u7533\u8bf7\u4e2d\u8ba2\u5355\uff09 */
    void cancelRefund(Long id, Long operatorId, String role);

    /** \u5b9a\u65f6\u4efb\u52a1\uff1a\u8d85\u65f6\u672a\u652f\u4ed8\u8ba2\u5355\u81ea\u52a8\u8fc7\u671f */
    int expirePendingOrders();

    int batchDelete(List<Long> ids);
    /** 一键清空订单数据（含支付流水/核销/评价/客流/时段库存/钱包/沙箱镜像），保证关联一致 */
    void clearOrderData();
}