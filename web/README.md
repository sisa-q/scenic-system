# 智慧景区 - 游客端（独立前端）

独立于管理端的游客端工程，拥有自己的登录页。

## 运行

```bash
npm run serve   # 开发模式（默认端口 8080，/api 代理到后端 http://localhost:8083）
npm run build   # 生产构建（输出 dist/）
```

## 说明

- 登录：仅允许游客账号登录本端；管理员账号会被拒绝，提示到管理端（http://localhost:8081）登录。
- 依赖：当前 node_modules 是链接到 `D:\bishe\admin\node_modules` 的目录链接（Junction），
  目的是离线复用依赖、免去重复安装；如需完全独立安装，删除该链接后执行 `npm install` 即可。