package com.scenic;

import com.scenic.entity.Order;
import com.scenic.entity.TimeSlot;
import com.scenic.repository.OrderRepository;
import com.scenic.repository.TimeSlotRepository;
import com.scenic.service.OrderService;
import com.scenic.service.VerifyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全链路集成测试：下单 → 支付 → 核销
 *
 * 使用 H2 内存数据库 + 真实 Spring 上下文（@SpringBootTest），
 * 不依赖 MySQL / Redis，跑的是真实仓储、真实事务。
 *
 * @Transactional：每个测试在事务中执行，结束自动回滚，互不污染。
 */
@SpringBootTest
@Transactional
@DisplayName("全链路集成测试：下单→支付→核销")
class OrderFlowIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private VerifyService verifyService;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("下单→支付→核销 全链路状态流转")
    void fullFlow() {
        TimeSlot slot = findFutureSlot();
        assertThat(slot).isNotNull();

        // ---------- 1. 下单 ----------
        Order order = new Order();
        order.setSlotId(slot.getId());
        order.setQuantity(2);
        Order created = orderService.createOrder(order, 100L);

        assertThat(created.getStatus()).isZero();                 // 待支付
        assertThat(created.getTotalAmount()).isNotNull();         // 金额已计算
        assertThat(created.getOrderNo()).isNotBlank();

        // ---------- 2. 支付 ----------
        int bookedBefore = slot.getBooked() == null ? 0 : slot.getBooked();
        orderService.payOrder(created.getId(), 100L, "user");

        Order paid = orderRepository.findById(created.getId()).orElse(null);
        assertThat(paid).isNotNull();
        assertThat(paid.getStatus()).isEqualTo(1);                // 已支付

        TimeSlot afterPay = timeSlotRepository.findById(slot.getId()).orElse(null);
        assertThat(afterPay).isNotNull();
        assertThat(afterPay.getBooked()).isEqualTo(bookedBefore + 2); // 预约数 +2

        // ---------- 3. 核销 ----------
        verifyService.verify(paid.getOrderNo());

        Order used = orderRepository.findById(paid.getId()).orElse(null);
        assertThat(used).isNotNull();
        assertThat(used.getStatus()).isEqualTo(2);                // 已使用
        assertThat(used.getUseTime()).isNotNull();                // 记录核销时间
    }

    @Test
    @DisplayName("游客申请退款只到'申请退款中'，管理员审批后才真正退款")
    void refundApprovalFlow() {
        TimeSlot slot = findFutureSlot();
        assertThat(slot).isNotNull();

        // 下单 + 支付
        Order order = new Order();
        order.setSlotId(slot.getId());
        order.setQuantity(1);
        Order created = orderService.createOrder(order, 200L);
        orderService.payOrder(created.getId(), 200L, "user");

        // 游客申请退款
        orderService.applyRefund(created.getId(), 200L);
        Order applied = orderRepository.findById(created.getId()).orElse(null);
        assertThat(applied.getStatus()).isEqualTo(5);             // 申请退款中
        assertThat(applied.getRefundTime()).isNull();             // 没有真正退款

        // 管理员审批退款
        int bookedBefore = slot.getBooked() == null ? 0 : slot.getBooked();
        orderService.refundOrder(created.getId(), 200L, "admin");

        Order refunded = orderRepository.findById(created.getId()).orElse(null);
        assertThat(refunded.getStatus()).isEqualTo(3);            // 已退款
        TimeSlot after = timeSlotRepository.findById(slot.getId()).orElse(null);
        assertThat(after.getBooked()).isEqualTo(bookedBefore - 1); // 预约数释放
    }

    /** 找一个未来开放、且有余量的时段（由 DataInitializer 种子数据提供） */
    private TimeSlot findFutureSlot() {
        List<TimeSlot> slots = timeSlotRepository.findAll();
        return slots.stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == 1)
                .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(LocalDateTime.now()))
                .filter(s -> s.getQuota() != null && s.getQuota() > 0)
                .findFirst()
                .orElse(null);
    }
}