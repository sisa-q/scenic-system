package com.scenic.controller;

import com.scenic.entity.Notice;
import com.scenic.service.NoticeService;
import com.scenic.vo.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 公告控制器测试 */
@ExtendWith(MockitoExtension.class)
@DisplayName("公告控制器")
class NoticeControllerTest {

    @Mock private NoticeService noticeService;
    @InjectMocks private NoticeController noticeController;

    @Test
    void list_ok() {
        when(noticeService.listAll()).thenReturn(List.of(new Notice()));

        Result r = noticeController.list(null, null);

        assertThat(r.getCode()).isEqualTo(200);
    }

    @Test
    void list_page() {
        when(noticeService.pageAll(1, 10))
                .thenReturn(java.util.Map.of("list", List.of(), "total", 0L));

        Result r = noticeController.list(1, 10);

        assertThat(r.getCode()).isEqualTo(200);
    }

    @Test
    void detail_notFound() {
        when(noticeService.getById(1L)).thenReturn(null);

        Result r = noticeController.detail(1L);

        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.getMsg()).contains("公告不存在");
    }

    @Test
    void add_ok() {
        when(noticeService.add(any(Notice.class))).thenAnswer(inv -> inv.getArgument(0));

        Result r = noticeController.add(new Notice());

        assertThat(r.getCode()).isEqualTo(200);
    }

    @Test
    void delete_ok() {
        Result r = noticeController.delete(1L);

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).isEqualTo("删除成功");
    }
}