package com.scenic.controller;

import com.scenic.entity.VerifyRecord;
import com.scenic.service.VerifyService;
import com.scenic.vo.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/** 核销控制器测试 */
@ExtendWith(MockitoExtension.class)
@DisplayName("核销控制器")
class VerifyControllerTest {

    @Mock private VerifyService verifyService;
    @InjectMocks private VerifyController verifyController;

    @Test
    void list_ok() {
        when(verifyService.listAll()).thenReturn(List.of(new VerifyRecord()));

        Result r = verifyController.list();

        assertThat(r.getCode()).isEqualTo(200);
    }

    @Test
    void execute_ok() {
        Map<String, String> params = new HashMap<>();
        params.put("code", "NO1");

        Result r = verifyController.execute(params);

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).isEqualTo("核销成功");
    }

    @Test
    void execute_error() {
        Map<String, String> params = new HashMap<>();
        params.put("code", "X");
        doThrow(new RuntimeException("核销码不存在")).when(verifyService).verify("X");

        Result r = verifyController.execute(params);

        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.getMsg()).contains("核销码不存在");
    }
}