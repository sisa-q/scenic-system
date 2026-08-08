package com.scenic.controller;

import com.scenic.entity.ScenicSpot;
import com.scenic.service.ScenicSpotService;
import com.scenic.vo.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** 景点控制器测试 */
@ExtendWith(MockitoExtension.class)
@DisplayName("景点控制器")
class SpotControllerTest {

    @Mock private ScenicSpotService spotService;
    @InjectMocks private SpotController spotController;

    @Test
    void list_ok() {
        when(spotService.listAll()).thenReturn(List.of(new ScenicSpot()));

        Result r = spotController.list();

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).isInstanceOf(List.class);
    }

    @Test
    void detail_notFound() {
        when(spotService.getById(1L)).thenReturn(null);

        Result r = spotController.detail(1L);

        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.getMsg()).contains("不存在");
    }

    @Test
    void add_ok() {
        ScenicSpot spot = new ScenicSpot();
        when(spotService.add(spot)).thenReturn(spot);

        Result r = spotController.add(spot);

        assertThat(r.getCode()).isEqualTo(200);
    }

    @Test
    void delete_ok() {
        Result r = spotController.delete(1L);

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).isEqualTo("删除成功");
    }
}