# 管理端前端（admin）

> 智慧景区 · 管理后台 —— Vue 3 + Element Plus

## 技术栈
Vue 3 · Element Plus 2.14 · Pinia · Vue Router 4 · Axios · Three.js 0.185 · Vue CLI 5

## 功能页面
- `/admin/dashboard` 客流数字孪生大屏（WebSocket 每 5 秒实时 + 30 秒回放）
- `/admin/spot` 景点管理 · `/admin/ticket` 票务策略 · `/admin/timeslot` 分时时段
- `/admin/order` 订单管理（退款审核） · `/admin/verify` 核销管理
- `/admin/evaluation` 评价管理 · `/admin/notice` 公告发布 · `/admin/profile` 个人中心

## 运行
```bash
npm install
npm run serve   # 8081，/api 与 /ws 代理到后端 8083
npm run build
```

## 权限
管理端仅允许 `admin` 角色登录；接口权限由后端 JwtInterceptor 二次校验。