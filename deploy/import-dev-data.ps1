# ============================================================
# 一键导入：本机开发库 digital_ticket -> 容器 MySQL（真实数据演示）
# 用法：在仓库根目录执行  .\deploy\import-dev-data.ps1
# 前置：容器已启动（docker compose up -d），Docker Desktop 运行中
# ============================================================
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

# ---- 可修改参数 ----
$HOST_MYSQL_HOST = "host.docker.internal"   # 容器内访问宿主机
$HOST_MYSQL_PORT = "3306"                   # 本机开发库端口
$HOST_MYSQL_USER = "root"
$HOST_MYSQL_PASS = "REDACTED"            # 开发库 root 密码
$CONTAINER_ROOT_PASS = "REDACTED"         # 容器 MYSQL_ROOT_PASSWORD（.env 可改）
$DB_NAME = "digital_ticket"
# --------------------

Write-Host "==> [1/3] 导出开发库（容器 mysqldump -> 容器内 /tmp/dump_dev.sql）" -ForegroundColor Cyan
docker exec scenic-mysql sh -c "mysqldump -h $HOST_MYSQL_HOST -P $HOST_MYSQL_PORT -u$HOST_MYSQL_USER -p$HOST_MYSQL_PASS --single-transaction --quick --default-character-set=utf8mb4 --set-gtid-purged=OFF $DB_NAME > /tmp/dump_dev.sql && echo DUMP_OK"
if ($LASTEXITCODE -ne 0) { throw "导出失败" }

Write-Host "==> [2/3] 停后端并导入容器库" -ForegroundColor Cyan
docker compose stop backend
docker exec scenic-mysql sh -c "mysql -uroot -p$CONTAINER_ROOT_PASS $DB_NAME < /tmp/dump_dev.sql && echo IMPORT_OK"
if ($LASTEXITCODE -ne 0) { throw "导入失败" }

Write-Host "==> [3/3] 重启后端" -ForegroundColor Cyan
docker compose start backend

Write-Host "" -ForegroundColor Green
Write-Host "完成！验证：" -ForegroundColor Green
Write-Host "  游客端 http://localhost          （admin / admin123 可登管理端）" -ForegroundColor Green
Write-Host "  管理端 http://localhost:8082" -ForegroundColor Green
Write-Host "  API   http://localhost/api/spot/list" -ForegroundColor Green