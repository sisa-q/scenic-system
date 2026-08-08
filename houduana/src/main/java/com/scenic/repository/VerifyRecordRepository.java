package com.scenic.repository;

import com.scenic.entity.VerifyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VerifyRecordRepository extends JpaRepository<VerifyRecord, Long> {
}