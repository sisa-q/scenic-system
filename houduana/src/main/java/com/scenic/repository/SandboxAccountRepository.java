package com.scenic.repository;

import com.scenic.entity.SandboxAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SandboxAccountRepository extends JpaRepository<SandboxAccount, Long> {
    Optional<SandboxAccount> findByRole(String role);
}