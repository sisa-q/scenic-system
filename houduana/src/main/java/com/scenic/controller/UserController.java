package com.scenic.controller;

import com.scenic.entity.User;
import com.scenic.service.UserService;
import com.scenic.util.JwtUtil;
import com.scenic.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    // ==================== 登录 ====================
    @PostMapping("/login")
    public Result login(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");

        if (username == null || username.trim().isEmpty()) {
            return Result.error("请输入用户名或手机号");
        }
        if (password == null || password.trim().isEmpty()) {
            return Result.error("请输入密码");
        }

        // 登录失败限流：5 次失败锁定 15 分钟（Redis 不可用时自动跳过）
        if (redisTemplate != null) {
            String failKey = "login:fail:" + username.trim().toLowerCase();
            String failVal = redisTemplate.opsForValue().get(failKey);
            if (failVal != null) {
                try {
                    if (Integer.parseInt(failVal) >= 5) {
                        return Result.error("失败次数过多，请15分钟后再试");
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        User user = userService.login(username.trim(), password.trim());
        if (user == null) {
            if (redisTemplate != null) {
                String failKey = "login:fail:" + username.trim().toLowerCase();
                Long n = redisTemplate.opsForValue().increment(failKey);
                if (n != null && n == 1) {
                    redisTemplate.expire(failKey, Duration.ofMinutes(15));
                }
            }
            return Result.error("用户名或密码错误");
        }

        // 端角色强校验：游客端只允许游客账号，管理端只允许管理员账号
        String end = params.get("end");
        if (end != null && !end.trim().isEmpty()) {
            boolean isAdminRole = "admin".equals(user.getRole());
            boolean adminEnd = "admin".equals(end.trim());
            if (adminEnd && !isAdminRole) {
                return Result.error("仅管理员账号可登录管理端");
            }
            if (!adminEnd && isAdminRole) {
                return Result.error("管理员账号请在管理端登录");
            }
        }

        // 登录成功清除失败计数
        if (redisTemplate != null) {
            redisTemplate.delete("login:fail:" + username.trim().toLowerCase());
        }

        String token = jwtUtil.generateToken(user.getId().toString(), user.getUsername(), user.getRole());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("phone", user.getPhone());
        data.put("role", user.getRole());

        return Result.success(data);
    }

    // ==================== 登出（JWT 加入 Redis 黑名单，立即失效） ====================
    @PostMapping("/logout")
    public Result logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ") && redisTemplate != null) {
                String token = authHeader.substring(7);
                String jti = jwtUtil.getJtiFromToken(token);
                if (jti != null) {
                    long remainMs = jwtUtil.getRemainingMillis(token);
                    if (remainMs > 0) {
                        redisTemplate.opsForValue().set("jwt:blacklist:" + jti, "1", Duration.ofMillis(remainMs));
                    }
                }
            }
            return Result.success("已退出登录");
        } catch (Exception e) {
            return Result.error("退出失败：" + e.getMessage());
        }
    }

    // ==================== 注册 ====================
    @PostMapping("/register")
    public Result register(@RequestBody User user) {
        try {
            // 基础校验
            if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
                return Result.error("用户名不能为空");
            }
            if (user.getPassword() == null || user.getPassword().length() < 6) {
                return Result.error("密码至少6位");
            }

            // 检查用户名是否已存在
            if (userService.findByUsername(user.getUsername()) != null) {
                return Result.error("用户名已存在");
            }

            // 设置默认角色
            if (user.getRole() == null || user.getRole().isEmpty()) {
                user.setRole("user");
            }

            User registered = userService.register(user);
            registered.setPassword(null); // 清除密码返回

            Map<String, Object> data = new HashMap<>();
            data.put("id", registered.getId());
            data.put("username", registered.getUsername());
            data.put("nickname", registered.getNickname());
            data.put("role", registered.getRole());

            return Result.success(data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("注册失败：" + e.getMessage());
        }
    }

    // ==================== 获取当前用户信息 ====================
    @GetMapping("/info")
    public Result info(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            Long userId = Long.parseLong(jwtUtil.getUserIdFromToken(token));
            User user = userService.findById(userId);
            if (user == null) {
                return Result.error("用户不存在");
            }
            user.setPassword(null);
            return Result.success(user);
        } catch (Exception e) {
            return Result.error("获取用户信息失败");
        }
    }

    // ==================== 更新用户信息 ====================
    @PutMapping("/update")
    public Result update(@RequestBody Map<String, String> params,
                         @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            Long userId = Long.parseLong(jwtUtil.getUserIdFromToken(token));

            User user = userService.findById(userId);
            if (user == null) {
                return Result.error("用户不存在");
            }

            String nickname = params.get("nickname");
            String phone = params.get("phone");

            if (nickname != null && !nickname.trim().isEmpty()) {
                user.setNickname(nickname.trim());
            }
            if (phone != null && !phone.trim().isEmpty()) {
                user.setPhone(phone.trim());
            }

            User updated = userService.update(user);
            updated.setPassword(null);
            return Result.success(updated);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("更新失败：" + e.getMessage());
        }
    }

    // ==================== 注销账号（物理删除） ====================
    @DeleteMapping("/delete")
    public Result deleteAccount(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            Long userId = Long.parseLong(jwtUtil.getUserIdFromToken(token));

            boolean deleted = userService.deleteUser(userId);
            if (deleted) {
                return Result.success("账号已注销");
            } else {
                return Result.error("注销失败，用户不存在");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("注销失败：" + e.getMessage());
        }
    }
}