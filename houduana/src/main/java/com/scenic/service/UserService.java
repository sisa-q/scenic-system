package com.scenic.service;

import com.scenic.entity.User;

import java.math.BigDecimal;

public interface UserService {
    User login(String username, String password);

    User register(User user);

    User findById(Long id);

    User findByUsername(String username);

    User update(User user);

    boolean checkPassword(Long userId, String rawPassword);  // 校验原密码

    /** 扣减余额（不足返回 false） */
    boolean deductBalance(Long userId, BigDecimal amount);

    /** 增加余额（退款/充值） */
    void addBalance(Long userId, BigDecimal amount);

    boolean deleteUser(Long id);  // 注销账号
}