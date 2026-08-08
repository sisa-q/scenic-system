package com.scenic.service;

import com.scenic.entity.Evaluation;
import com.scenic.entity.Order;
import com.scenic.repository.EvaluationRepository;
import com.scenic.repository.OrderRepository;
import com.scenic.repository.ScenicSpotRepository;
import com.scenic.repository.TicketPolicyRepository;
import com.scenic.service.impl.EvaluationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 评价服务测试：提交 / 更新 / 分页列表 */
@ExtendWith(MockitoExtension.class)
@DisplayName("评价服务")
class EvaluationServiceImplTest {

    @Mock private EvaluationRepository evaluationRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private TicketPolicyRepository ticketPolicyRepository;
    @Mock private ScenicSpotRepository scenicSpotRepository;
    @InjectMocks private EvaluationServiceImpl evaluationService;

    private Order usedOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(100L);
        order.setStatus(2); // 已核销
        return order;
    }

    @Test
    @DisplayName("提交评价成功：记录情感得分")
    void submit_success() {
        Order order = usedOrder();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(evaluationRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(evaluationRepository.save(any(Evaluation.class))).thenAnswer(inv -> inv.getArgument(0));

        Evaluation evaluation = new Evaluation();
        evaluation.setOrderId(1L);
        evaluation.setContent("很棒，很满意！");
        evaluation.setScore(5);

        Evaluation saved = evaluationService.submit(evaluation, 100L);

        assertThat(saved.getUserId()).isEqualTo(100L);
        assertThat(saved.getSentimentScore()).isNotNull(); // 情感得分被计算
    }

    @Test
    @DisplayName("提交失败：订单不存在")
    void submit_orderNotFound() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        Evaluation evaluation = new Evaluation();
        evaluation.setOrderId(1L);

        assertThatThrownBy(() -> evaluationService.submit(evaluation, 100L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("订单不存在");
    }

    @Test
    @DisplayName("提交失败：无权评价他人订单")
    void submit_notOwner() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(usedOrder()));

        Evaluation evaluation = new Evaluation();
        evaluation.setOrderId(1L);

        assertThatThrownBy(() -> evaluationService.submit(evaluation, 200L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权评价");
    }

    @Test
    @DisplayName("提交失败：仅已核销订单可评价")
    void submit_notUsed() {
        Order order = usedOrder();
        order.setStatus(1);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        Evaluation evaluation = new Evaluation();
        evaluation.setOrderId(1L);

        assertThatThrownBy(() -> evaluationService.submit(evaluation, 100L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("已核销");
    }

    @Test
    @DisplayName("提交失败：评分超出 0-5")
    void submit_scoreOutOfRange() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(usedOrder()));
        when(evaluationRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        Evaluation evaluation = new Evaluation();
        evaluation.setOrderId(1L);
        evaluation.setContent("不错");
        evaluation.setScore(6);

        assertThatThrownBy(() -> evaluationService.submit(evaluation, 100L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("0-5");
    }

    @Test
    @DisplayName("更新评价成功")
    void update_success() {
        Evaluation existing = new Evaluation();
        existing.setId(1L);
        existing.setContent("旧内容");
        existing.setScore(3);
        when(evaluationRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(evaluationRepository.save(any(Evaluation.class))).thenAnswer(inv -> inv.getArgument(0));

        Evaluation update = new Evaluation();
        update.setId(1L);
        update.setContent("很棒，非常满意");
        update.setScore(5);

        Evaluation saved = evaluationService.update(update);

        assertThat(saved.getContent()).isEqualTo("很棒，非常满意");
        assertThat(saved.getScore()).isEqualTo(5);
        assertThat(saved.getSentimentScore()).isNotNull();
    }

    @Test
    @DisplayName("分页列表：返回 total 与 list")
    void listAll_pagination() {
        Evaluation e1 = new Evaluation();
        Evaluation e2 = new Evaluation();
        when(evaluationRepository.findAll()).thenReturn(List.of(e1, e2));

        Map<String, Object> data = evaluationService.listAll(1, 10, null, null, null, null, null);

        assertThat((Integer) data.get("total")).isEqualTo(2);
        assertThat((List<?>) data.get("list")).hasSize(2);
    }
}