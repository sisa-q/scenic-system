# 后端（houduana）

> 智慧景区 · Spring Boot 后端 —— Java 21 + Spring Boot 3.5 + MySQL + Redis

## 技术栈
Java 21 · Spring Boot 3.5.16 · Spring Data JPA(Hibernate) · Spring WebSocket · Spring Data Redis(Lettuce) · jjwt 0.11.5 · BCrypt · Maven · JaCoCo

## 分层架构
```
com.scenic
├─ config/       配置类（跨域/拦截器注册/WebSocket/数据初始化/支付配置/全局异常）
├─ controller/   HTTP 接口层（收参→调 Service→返回 Result）
├─ service/      业务接口 + impl 实现（核心业务逻辑）
├─ repository/   JPA 数据访问层
├─ entity/       JPA 实体（对应 10 张表）
├─ interceptor/  JwtInterceptor（登录校验 + 角色权限）
├─ util/         JwtUtil / RedisCache / AlipaySigner（自研）
└─ vo/           统一返回 Result / 支付 PayResult / 天气 VO
```

## 核心实现亮点
| 亮点 | 实现 |
|---|---|
| 防超卖 | Redis 分布式锁 `ticket:slot:lock:{slotId}` + 锁内数据库双重校验，Redis 不可用自动降级 |
| 缓存三防 | 自研 `RedisCache`：防穿透（空值短 TTL）/防击穿（互斥锁重建）/防雪崩（TTL 随机抖动），封装 getOrLoad/getListOrLoad |
| 支付宝支付 | 自研 RSA2 签名/验签（AlipaySigner）、异步回调幂等（trade_no 去重）、金额比对、支付超时自动失效 |
| 订单状态机 | 0待付/1已付/2已核销/3已退/4失效/5退款中，六态流转 + 每分钟扫描超时 |
| 实时推送 | WebSocket `/ws/flow` 客流每 5 秒推送、`/ws/weather` 天气预警每 30 分钟检测 |
| 天气降级 | open-meteo → 和风 QWeather → 内置模拟，三级数据源 + Redis 30 分钟缓存 |
| 安全 | JWT 登录 + Redis 黑名单登出、登录失败限流（5 次/15 分钟）、BCrypt 密码、接口角色权限 |

## 运行
```bash
# 必须带 local profile（真实数据库密码/JWT密钥在 application-local.yml，不入库）
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## 测试
```bash
mvn test   # 118 项单元/集成测试（H2 内存库，不依赖 MySQL）+ JaCoCo 覆盖率报告
```

## 配置与 profile
| 文件 | 用途 |
|---|---|
| application.yml | 默认（端口 8083、数据源、Redis、JWT、支付，敏感值用 ${ENV} 占位） |
| application-local.yml | 本地真实凭据（已 .gitignore，不入库） |
| application-prod.yml | 生产（Docker 环境变量注入） |

## 部署
- 多阶段 Dockerfile：Maven+JDK21 构建 → 纯 JRE 运行
- docker-compose.yml：MySQL8 + Redis7 + 后端 + Nginx 四容器编排（healthcheck + 数据卷 + 镜像源）
- GitHub Actions CI：push 自动 `mvn test`