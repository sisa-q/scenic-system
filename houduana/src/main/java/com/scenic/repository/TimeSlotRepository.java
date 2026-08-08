package com.scenic.repository;

import com.scenic.entity.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    // 根据票种ID查询时段
    List<TimeSlot> findByPolicyId(Long policyId);

    // 批量查询（用于按景点获取所有时段）
    List<TimeSlot> findByPolicyIdIn(List<Long> policyIds);
}