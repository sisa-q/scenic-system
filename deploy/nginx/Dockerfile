# ============================================================
# Nginx 镜像：托管两个前端 dist + 反向代理后端
# 构建上下文为仓库根目录（docker-compose.yml 中指定 context: .）
# 前置条件：先执行 npm run build 生成 web/dist 与 admin/dist
# ============================================================
ARG NGINX_IMAGE=nginx:1.27-alpine
FROM ${NGINX_IMAGE}
COPY deploy/nginx/nginx.conf /etc/nginx/nginx.conf
COPY web/dist /usr/share/nginx/html/web
COPY admin/dist /usr/share/nginx/html/admin
EXPOSE 80 8081