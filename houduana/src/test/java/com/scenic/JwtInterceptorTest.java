package com.scenic;

import com.scenic.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JWT 拦截器集成测试（MockMvc）
 *
 * 启动完整 Spring 上下文 + MockMvc，请求真实走一遍拦截器：
 *  401：无 token / token 无效
 *  403：游客访问管理员专属接口
 *  200：管理员访问管理员接口 / 公开接口无需登录
 *  OPTIONS 预检直接放行
 *
 * 使用 H2 内存库，不依赖 MySQL / Redis。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("JWT 拦截器（MockMvc）")
class JwtInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    private String token(String userId, String role) {
        return jwtUtil.generateToken(userId, "user" + userId, role);
    }

    @Test
    @DisplayName("无 token 访问受保护接口 -> 401")
    void noToken_401() throws Exception {
        mockMvc.perform(get("/api/order/list"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.msg").value("未授权，请先登录"));
    }

    @Test
    @DisplayName("无效 token -> 401")
    void invalidToken_401() throws Exception {
        mockMvc.perform(get("/api/order/list")
                        .header("Authorization", "Bearer garbage.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.msg").value("token 无效或已过期"));
    }

    @Test
    @DisplayName("游客 token 访问管理员接口 -> 403")
    void userToken_adminOnly_403() throws Exception {
        mockMvc.perform(post("/api/spot/add")
                        .header("Authorization", "Bearer " + token("1", "user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试\",\"capacity\":10}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.msg").value("无权限访问，仅管理员可操作"));
    }

    @Test
    @DisplayName("管理员 token 访问管理员接口 -> 200")
    void adminToken_adminOnly_200() throws Exception {
        mockMvc.perform(post("/api/spot/add")
                        .header("Authorization", "Bearer " + token("1", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试景点\",\"capacity\":10,\"status\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("公开接口无需 token -> 200")
    void publicEndpoint_noToken_200() throws Exception {
        mockMvc.perform(get("/api/spot/list"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("OPTIONS 预检请求直接放行（不要求登录）")
    void options_preflight_passes() throws Exception {
        mockMvc.perform(options("/api/spot/add"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isNotEqualTo(401));
    }
}