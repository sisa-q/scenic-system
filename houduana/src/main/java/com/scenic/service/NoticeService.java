package com.scenic.service;

import com.scenic.entity.Notice;
import java.util.List;
import java.util.Map;

public interface NoticeService {
    List<Notice> listAll();

    /** 分页查询公告（数据库分页），返回 {list, total} */
    Map<String, Object> pageAll(int page, int size);
    Notice getById(Long id);
    Notice add(Notice notice);
    Notice update(Notice notice);
    void delete(Long id);
}