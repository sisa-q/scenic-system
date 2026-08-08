package com.scenic.repository;

import com.scenic.entity.TicketPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketPolicyRepository extends JpaRepository<TicketPolicy, Long> {

    // 根据景点ID查询票种（用于分时时段按景点分组）
    List<TicketPolicy> findBySpotId(Long spotId);
}