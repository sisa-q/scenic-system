# 游客端前端（web）

> 智慧景区 · 游客端 H5 / PWA —— Vue 3 + Vant 4 + Three.js

## 技术栈
Vue 3 · Vant 4 · Pinia · Vue Router 4 · Axios · Three.js 0.185 · Vue CLI 5 · PWA(Workbox)

## 功能页面
- `/home` 3D 地球全息导览（8K 地球 + 全球景点标注 + 天气面板/预警）
- `/spot/:id` 景点详情（默认版 / 故宫专属版）
- `/order-confirm` 确认订单（选票种/时段/数量）
- `/pay` 支付（支付宝沙箱跳转 / mock 降级）
- `/orders`、`/order/:id` 订单列表与详情（核销码/退款/评价）
- `/evaluation-submit` 发表评价 · `/notices`、`/notice/:id` 公告
- `/login`、`/user`、`/profile` 登录/个人中心/资料

## 运行
```bash
npm install
npm run serve   # 8080，/api 与 /ws 代理到后端 8083
npm run build   # 产出 dist（含 PWA service-worker.js + manifest）
```

## PWA
- 已接入 Workbox：静态资源预缓存 / 图片 CacheFirst / 读接口 NetworkFirst / 写接口不缓存
- 构建后 `dist/` 可直接由 Nginx / 任意静态服务器托管