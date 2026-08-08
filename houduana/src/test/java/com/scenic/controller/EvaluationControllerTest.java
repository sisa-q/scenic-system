package com.scenic.controller;

import com.scenic.entity.Evaluation;
import com.scenic.service.EvaluationService;
import com.scenic.util.JwtUtil;
import com.scenic.vo.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 评价控制器测试 */
@ExtendWith(MockitoExtension.class)
@DisplayName("评价控制器")
class EvaluationControllerTest {

    @Mock private EvaluationService evaluationService;
    @Mock private JwtUtil jwtUtil;
    @InjectMocks private EvaluationController evaluationController;

    @Test
    void list_ok() {
        when(evaluationService.listAll(1, 10, null, null, null, null, null)).thenReturn(Map.of());

        Result r = evaluationController.list(1, 10, null, null, null, null, null);

        assertThat(r.getCode()).isEqualTo(200);
    }

    @Test
    void getByOrder_ok() {
        when(evaluationService.getByOrderId(1L)).thenReturn(new Evaluation());

        Result r = evaluationController.getByOrder(1L);

        assertThat(r.getCode()).isEqualTo(200);
    }

    @Test
    void submit_ok() {
        Map<String, Object> params = new HashMap<>();
        params.put("orderId", 1L);
        params.put("content", "很好");
        params.put("score", 5);
        when(evaluationService.submit(any(Evaluation.class), any())).thenReturn(new Evaluation());

        Result r = evaluationController.submit(params, null);

        assertThat(r.getCode()).isEqualTo(200);
    }

    @Test
    void delete_ok() {
        Result r = evaluationController.delete(1L);

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).isEqualTo("删除成功");
    }
}