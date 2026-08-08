package com.scenic.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** JWT 工具测试：生成 / 解析 / 校验 */
@DisplayName("JWT 工具")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // @Value 字段在单元测试里手动注入
        ReflectionTestUtils.setField(jwtUtil, "secret", "abcdefghijklmnopqrstuvwxyz123456");
        ReflectionTestUtils.setField(jwtUtil, "expire", 3600000L);
    }

    @Test
    @DisplayName("生成并解析 token：用户ID/用户名/角色可还原")
    void generateAndParse() {
        String token = jwtUtil.generateToken("100", "admin", "admin");

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.getUserIdFromToken(token)).isEqualTo("100");
        assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo("admin");
        assertThat(jwtUtil.getRoleFromToken(token)).isEqualTo("admin");
        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("生成并解析 token：jti 唯一标识与剩余时间") 
    void generateAndParse_jti() {
        String token = jwtUtil.generateToken("100", "admin", "admin");

        assertThat(jwtUtil.getJtiFromToken(token)).isNotBlank();
        assertThat(jwtUtil.getRemainingMillis(token)).isGreaterThan(0);
    }

    @Test
    @DisplayName("默认角色生成：游客")
    void generateToken_defaultRole() {
        String token = jwtUtil.generateToken("100", "user1");

        assertThat(jwtUtil.getRoleFromToken(token)).isEqualTo("user");
    }

    @Test
    @DisplayName("非法 token 校验失败")
    void validateToken_invalid() {
        assertThat(jwtUtil.validateToken("garbage.token.value")).isFalse();
    }
}