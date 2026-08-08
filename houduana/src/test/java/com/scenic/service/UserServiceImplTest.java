package com.scenic.service;

import com.scenic.entity.User;
import com.scenic.repository.UserRepository;
import com.scenic.service.impl.UserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 用户服务测试：登录 / 注册 / 删除 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户服务：登录 / 注册 / 删除")
class UserServiceImplTest {

    private static final String HASH = "$2a$10$0123456789abcdefghijklmnopqrstuvwxyz0123456789";

    @Mock private UserRepository userRepository;
    @Mock private BCryptPasswordEncoder passwordEncoder;
    @InjectMocks private UserServiceImpl userService;

    private User user() {
        User user = new User();
        user.setUsername("admin");
        user.setPassword(HASH);
        user.setPhone("13800000000");
        return user;
    }

    @Test
    @DisplayName("登录成功：用用户名")
    void login_byUsername_ok() {
        User u = user();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(u));

        when(passwordEncoder.matches("123456", u.getPassword())).thenReturn(true);
        assertThat(userService.login("admin", "123456")).isSameAs(u);
    }

    @Test
    @DisplayName("登录成功：用手机号")
    void login_byPhone_ok() {
        User u = user();
        when(userRepository.findByUsername("13800000000")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("13800000000")).thenReturn(Optional.of(u));

        when(passwordEncoder.matches("123456", u.getPassword())).thenReturn(true);
        assertThat(userService.login("13800000000", "123456")).isSameAs(u);
    }

    @Test
    @DisplayName("登录失败：密码错误返回 null")
    void login_wrongPassword_returnsNull() {
        User u = user();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(u));

        when(passwordEncoder.matches("wrong", u.getPassword())).thenReturn(false);
        assertThat(userService.login("admin", "wrong")).isNull();
    }

    @Test
    @DisplayName("注册成功：默认角色为 user")
    void register_defaultsRoleToUser() {
        User u = new User();
        u.setUsername("newbie");
        u.setPassword("123456");
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.register(u);

        assertThat(saved.getRole()).isEqualTo("user");
        assertThat(saved.getPassword()).isEqualTo("encoded");
    }

    @Test
    @DisplayName("注册失败：用户名已存在")
    void register_duplicateUsername_throws() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(new User()));

        User u = new User();
        u.setUsername("admin");
        u.setPassword("123456");

        assertThatThrownBy(() -> userService.register(u))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户名已存在");
    }

    @Test
    @DisplayName("注销：存在则删除并返回 true")
    void deleteUser_exist() {
        when(userRepository.existsById(1L)).thenReturn(true);

        assertThat(userService.deleteUser(1L)).isTrue();
        verify(userRepository).deleteById(1L);
    }

    @Test
    @DisplayName("注销：不存在返回 false")
    void deleteUser_notExist() {
        when(userRepository.existsById(1L)).thenReturn(false);

        assertThat(userService.deleteUser(1L)).isFalse();
    }
}