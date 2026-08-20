package com.scenic.repository;

import com.scenic.entity.PayTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayTransactionRepository extends JpaRepository<PayTransaction, Long> {
    Optional<PayTransaction> findByTransactionId(String transactionId);
    Optional<PayTransaction> findByOrderNo(String orderNo);
    java.util.List<PayTransaction> findByOrderNoIn(java.util.Collection<String> orderNos);
}
