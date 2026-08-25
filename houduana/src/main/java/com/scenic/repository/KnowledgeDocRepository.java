package com.scenic.repository;

import com.scenic.entity.KnowledgeDoc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KnowledgeDocRepository extends JpaRepository<KnowledgeDoc, Long> {
}
