package com.scenic.repository;

import com.scenic.entity.SandboxFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SandboxFlowRepository extends JpaRepository<SandboxFlow, Long> {
    boolean existsByOrderNoAndBizType(String orderNo, String bizType);
}