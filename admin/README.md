# 智慧景区 - 管理端（独立前端）

独立于游客端的管理端工程，拥有自己独立的登录页（Element Plus 风格，仅限管理员登录）。

## 运行

```bash
npm run serve   # 开发模式（默认端口 8081，/api 代理到后端 http://localhost:8083）
npm run build   # 生产构建（输出 dist/）
```


## 说明

- 登录：仅允许管理员账号登录本端（admin/admin123）；游客账号会被拒绝，提示到游客端（http://localhost:8080）登录。
- 依赖：当前 node_modules 是链接到 `D:\bishe\admin\node_modules` 的目录链接（Junction），
  目的是离线复用依赖、免去重复安装；如需完全独立安装，删除该链接后执行 `npm install` 即可。