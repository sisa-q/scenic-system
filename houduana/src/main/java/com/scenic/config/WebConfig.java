package com.scenic.config;

import com.scenic.interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")                    // 拦截所有 /api 请求
                .excludePathPatterns(
                        // ====== 用户认证相关（公开） ======
                        "/api/user/login",                     // 登录
                        "/api/user/register",                  // 注册

                        // ====== 景点相关（游客可见） ======
                        "/api/spot/list",                      // 景点列表
                        "/api/spot/detail/**",                 // 景点详情（含通配符）

                        // ====== 票务相关（游客可见） ======
                        "/api/ticket/list",                    // 票种列表（游客首页/景点详情）
                        "/api/ticket/slots",                   // 获取时段列表
                        "/api/ticket/slots/spot",              // 按景点获取时段（游客端景点详情）
                        "/api/ticket/slot/**",                 // 单个时段详情（游客端确认订单）

                        // ====== 公告相关（游客可见） ======
                        "/api/notice/list",                    // 公告列表
                        "/api/notice/detail/**",

                        // ====== 天气服务（游客端地球导览，公开） ======
                        "/api/weather/**",               // 公告详情（含通配符）

                        // ====== 支付回调（验签在服务层，无需登录） ======
                        "/api/pay/notify/**",             // 支付宝异步回调
                        "/api/pay/return/**"              // 支付宝同步跳转（return_url 兜底确认）
                );
    }
}