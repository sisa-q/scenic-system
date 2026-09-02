package com.scenic.repository;

import com.scenic.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByStatus(Integer status);

    List<Order> findByUserId(Long userId);

    // ✅ 新增：查询游客可见的订单（userVisible = 1）
    List<Order> findByUserIdAndUserVisible(Long userId, Integer userVisible);

    // ✅ 新增：查询特定状态的游客可见订单
    List<Order> findByUserIdAndStatusAndUserVisible(Long userId, Integer status, Integer userVisible);

    // 查询多个状态的游客可见订单（已支付标签同时包含退款申请中）
    List<Order> findByUserIdAndStatusInAndUserVisible(Long userId, java.util.List<Integer> statuses, Integer userVisible);

    // ====== 流客统计 / 核销用查询 ======
    java.util.Optional<Order> findByOrderNo(String orderNo);

    // 按创建时间查询（今日订单量）
    List<Order> findByCreateTimeBetween(Date start, Date end);

    // 按核销时间查询（入园人数趋势）
    List<Order> findByStatusAndUseTimeBetween(Integer status, Date start, Date end);

    // 累计有效订单数（已支付/已核销）
    long countByStatusGreaterThanEqual(Integer status);

    // 待处理退款计数（已申请待退款）
    long countByStatus(Integer status);

    // 某时段已支付订单数（用于库存校验）
    long countBySlotIdAndStatusIn(Long slotId, List<Integer> statuses);

    // ====== 数据库分页查询 ======
    Page<Order> findByStatus(Integer status, Pageable pageable);

    Page<Order> findByOrderNoContaining(String key, Pageable pageable);

    Page<Order> findByStatusAndOrderNoContaining(Integer status, String key, Pageable pageable);

    Page<Order> findByUserIdAndUserVisible(Long userId, Integer userVisible, Pageable pageable);

    Page<Order> findByUserIdAndUserVisibleAndOrderNoContaining(Long userId, Integer userVisible, String key, Pageable pageable);

    Page<Order> findByUserIdAndStatusAndUserVisible(Long userId, Integer status, Integer userVisible, Pageable pageable);

    Page<Order> findByUserIdAndStatusAndUserVisibleAndOrderNoContaining(Long userId, Integer status, Integer userVisible, String key, Pageable pageable);

    Page<Order> findByUserIdAndStatusInAndUserVisible(Long userId, java.util.List<Integer> statuses, Integer userVisible, Pageable pageable);

    Page<Order> findByUserIdAndStatusInAndUserVisibleAndOrderNoContaining(Long userId, java.util.List<Integer> statuses, Integer userVisible, String key, Pageable pageable);
}