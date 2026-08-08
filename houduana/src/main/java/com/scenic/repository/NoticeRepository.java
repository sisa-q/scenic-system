package com.scenic.repository;

import com.scenic.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {
    /** 按发布时间倒序 */
    List<Notice> findAllByOrderByPublishTimeDesc();

    /** 分页查询（数据库分页） */
    Page<Notice> findAllByOrderByPublishTimeDesc(Pageable pageable);
}