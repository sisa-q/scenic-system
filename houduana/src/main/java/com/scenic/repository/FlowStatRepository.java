package com.scenic.repository;

import com.scenic.entity.FlowStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FlowStatRepository extends JpaRepository<FlowStat, Long> {

    /** 查找某景点某日的客流统计（statTime 为当日 0 点） */
    java.util.Optional<FlowStat> findBySpotIdAndStatTime(Long spotId, java.util.Date statTime);
}