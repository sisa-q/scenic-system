package com.scenic.controller;

import com.scenic.entity.User;
import com.scenic.service.UserService;
import com.scenic.util.JwtUtil;
import com.scenic.vo.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 用户控制器测试（登录 / 注册） */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户控制器")
class UserControllerTest {

    @Mock private UserService userService;
    @Mock private JwtUtil jwtUtil;
    @InjectMocks private UserController userController;

    @Test
    void login_emptyUsername() {
        Map<String, String> params = new HashMap<>();
        params.put("password", "123456");

        Result r = userController.login(params);

        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.getMsg()).contains("请输入用户名或手机号");
    }

    @Test
    void login_wrongPassword() {
        Map<String, String> params = new HashMap<>();
        params.put("username", "admin");
        params.put("password", "wrong");
        when(userService.login("admin", "wrong")).thenReturn(null);

        Result r = userController.login(params);

        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.getMsg()).contains("用户名或密码错误");
    }

    @Test
    void login_success() {
        Map<String, String> params = new HashMap<>();
        params.put("username", "admin");
        params.put("password", "123456");

        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setRole("admin");
        when(userService.login("admin", "123456")).thenReturn(user);
        when(jwtUtil.generateToken(any(), any(), any())).thenReturn("fake-token");

        Result r = userController.login(params);

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(((Map<?, ?>) r.getData()).get("token")).isEqualTo("fake-token");
    }

    @Test
    void logout_ok() {
        // Redis 未注入（单元测试）时仍返回成功
        Result r = userController.logout(null);

        assertThat(r.getCode()).isEqualTo(200);
    }

    @Test
    void register_shortPassword() {
        User user = new User();
        user.setUsername("newbie");
        user.setPassword("123");

        Result r = userController.register(user);

        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.getMsg()).contains("密码至少6位");
    }

    @Test
    void register_success() {
        User user = new User();
        user.setUsername("newbie");
        user.setPassword("123456");
        when(userService.findByUsername("newbie")).thenReturn(null);
        when(userService.register(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Result r = userController.register(user);

        assertThat(r.getCode()).isEqualTo(200);
    }
}