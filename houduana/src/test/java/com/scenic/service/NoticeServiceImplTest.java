package com.scenic.service;

import com.scenic.entity.Notice;
import com.scenic.repository.NoticeRepository;
import com.scenic.service.impl.NoticeServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 公告服务测试：列表 / 详情 / 增删改 */
@ExtendWith(MockitoExtension.class)
@DisplayName("公告服务")
class NoticeServiceImplTest {

    @Mock private NoticeRepository noticeRepository;
    @InjectMocks private NoticeServiceImpl noticeService;

    @Test
    @DisplayName("列表：按发布时间倒序")
    void listAll() {
        when(noticeRepository.findAllByOrderByPublishTimeDesc()).thenReturn(List.of(new Notice()));

        assertThat(noticeService.listAll()).hasSize(1);
    }

    @Test
    @DisplayName("详情：存在返回对象")
    void getById_found() {
        Notice notice = new Notice();
        notice.setId(1L);
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(notice));

        assertThat(noticeService.getById(1L)).isSameAs(notice);
    }

    @Test
    @DisplayName("详情：不存在返回 null")
    void getById_notFound() {
        when(noticeRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(noticeService.getById(1L)).isNull();
    }

    @Test
    @DisplayName("新增：保存并返回")
    void add() {
        Notice notice = new Notice();
        when(noticeRepository.save(any(Notice.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(noticeService.add(notice)).isSameAs(notice);
    }

    @Test
    @DisplayName("删除：调用仓库删除")
    void delete() {
        noticeService.delete(1L);
        verify(noticeRepository).deleteById(1L);
    }
}