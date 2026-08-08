package com.scenic.service.impl;

import com.scenic.entity.Notice;
import com.scenic.repository.NoticeRepository;
import com.scenic.service.NoticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NoticeServiceImpl implements NoticeService {

    @Autowired
    private NoticeRepository noticeRepository;

    @Override
    public List<Notice> listAll() {
        return noticeRepository.findAllByOrderByPublishTimeDesc();
    }

    @Override
    public Map<String, Object> pageAll(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Page<Notice> result = noticeRepository.findAllByOrderByPublishTimeDesc(PageRequest.of(safePage - 1, safeSize));
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getContent());
        data.put("total", result.getTotalElements());
        return data;
    }

    @Override
    public Notice getById(Long id) {
        return noticeRepository.findById(id).orElse(null);
    }

    @Override
    public Notice add(Notice notice) {
        return noticeRepository.save(notice);
    }

    @Override
    public Notice update(Notice notice) {
        return noticeRepository.save(notice);
    }

    @Override
    public void delete(Long id) {
        noticeRepository.deleteById(id);
    }
}