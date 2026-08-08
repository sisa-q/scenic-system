package com.scenic.repository;

import com.scenic.entity.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    /** 查找某订单的评价 */
    java.util.Optional<Evaluation> findByOrderId(Long orderId);
}