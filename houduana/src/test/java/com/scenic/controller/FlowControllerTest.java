package com.scenic.controller;

import com.scenic.service.FlowStatService;
import com.scenic.vo.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** 客流统计控制器测试 */
@ExtendWith(MockitoExtension.class)
@DisplayName("客流统计控制器")
class FlowControllerTest {

    @Mock private FlowStatService flowService;
    @InjectMocks private FlowController flowController;

    @Test
    void stats_ok() {
        when(flowService.getStats()).thenReturn(Map.of());

        Result r = flowController.stats();

        assertThat(r.getCode()).isEqualTo(200);
    }

    @Test
    void realtime_ok() {
        when(flowService.getRealtime()).thenReturn(Map.of());

        Result r = flowController.realtime();

        assertThat(r.getCode()).isEqualTo(200);
    }
}