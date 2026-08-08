# 放行 80 / 8082 入站 TCP（允许局域网手机访问 Docker 部署的系统）
$ErrorActionPreference = "Stop"
try {
    New-NetFirewallRule -DisplayName "scenic-web-80" -Direction Inbound -Protocol TCP -LocalPort 80 -Action Allow -Profile Any | Out-Null
    New-NetFirewallRule -DisplayName "scenic-admin-8082" -Direction Inbound -Protocol TCP -LocalPort 8082 -Action Allow -Profile Any | Out-Null
    "OK" | Set-Content -LiteralPath 'D:\bishe\deploy\.fw-result.txt' -Encoding utf8
} catch {
    ("FAIL: " + $_.Exception.Message) | Set-Content -LiteralPath 'D:\bishe\deploy\.fw-result.txt' -Encoding utf8
}