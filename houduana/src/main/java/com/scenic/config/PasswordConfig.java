package com.scenic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Provides the BCryptPasswordEncoder bean used to hash and verify user passwords.
 * Only the spring-security-crypto module is used; no security filter chain is activated.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // strength=12：提升抗暴力破解能力
    }
}