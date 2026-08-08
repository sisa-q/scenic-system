# ============================================================
# 一键构建并启动整套生产环境（在仓库根目录运行）
#   1) 构建两个前端 dist（web / admin）
#   2) docker compose build（后端多阶段镜像 + Nginx 镜像）
#   3) docker compose up -d
# 前置：Docker Desktop 已启动；Node 可用（npm run build）
# ============================================================
$ErrorActionPreference = "Stop"

Set-Location (Split-Path $PSScriptRoot -Parent)

Write-Host "==> [1/3] 构建前端 web/dist" -ForegroundColor Cyan
Push-Location web
try { npm run build } finally { Pop-Location }

Write-Host "==> [2/3] 构建前端 admin/dist" -ForegroundColor Cyan
Push-Location admin
try { npm run build } finally { Pop-Location }

Write-Host "==> [3/3] docker compose build + up -d" -ForegroundColor Cyan
docker compose up -d --build

Write-Host ""
Write-Host "启动完成，访问：" -ForegroundColor Green
Write-Host "  游客端: http://localhost" -ForegroundColor Green
Write-Host "  管理端: http://localhost:8081" -ForegroundColor Green
Write-Host "  后端:   http://localhost:8083" -ForegroundColor Green
Write-Host "查看状态: docker compose ps" -ForegroundColor Green
Write-Host "查看日志: docker compose logs -f backend nginx" -ForegroundColor Green