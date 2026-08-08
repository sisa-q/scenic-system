package com.scenic.service.impl;

import com.scenic.entity.User;
import com.scenic.repository.UserRepository;
import com.scenic.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public User login(String username, String password) {
        // 支持用户名或手机号登录
        Optional<User> optional = userRepository.findByUsername(username);
        if (optional.isEmpty()) {
            optional = userRepository.findByPhone(username);
        }
        if (optional.isPresent()) {
            User user = optional.get();
            if (passwordEncoder.matches(password, user.getPassword())) {
                return user;
            }
        } else {
            // 用户不存在也执行一次 BCrypt 比较，抹平响应时间差，防止账号探测
            passwordEncoder.matches(password, passwordEncoder.encode("dummy-timing"));
        }
        return null;
    }

    @Override
    public User register(User user) {
        // 检查用户名是否已存在
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new RuntimeException("用户名已存在");
        }
        // 默认角色为 user
        if (user.getRole() == null || user.getRole().isEmpty()) {
            user.setRole("user");
        }
        // hash the plaintext password before persisting
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    @Override
    public User findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    @Override
    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    @Override
    public User update(User user) {
        if (user.getId() == null) {
            throw new RuntimeException("用户ID不能为空");
        }
        Optional<User> existing = userRepository.findById(user.getId());
        if (existing.isEmpty()) {
            throw new RuntimeException("用户不存在");
        }
        // encode plaintext password if provided; keep existing BCrypt hash unchanged
        String pwd = user.getPassword();
        if (pwd != null && !pwd.startsWith("$2")) {
            user.setPassword(passwordEncoder.encode(pwd));
        }
        return userRepository.save(user);
    }

    @Override
    public boolean deleteUser(Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return true;
        }
        return false;
    }
}