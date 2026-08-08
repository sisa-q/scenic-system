package com.scenic.service;

import com.scenic.entity.User;

public interface UserService {
    User login(String username, String password);

    User register(User user);

    User findById(Long id);

    User findByUsername(String username);

    User update(User user);

    boolean deleteUser(Long id);  // 注销账号
}