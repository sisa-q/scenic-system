param(
  [switch]$KeepOllama,   # 保留 Ollama（模型常驻，下次启动更快）
  [switch]$StopMySQL,    # 一并停止 MySQL80 系统服务（需要管理员权限）
  [switch]$NoWait        # 自动化：不等待按键
)
$ErrorActionPreference = 'SilentlyContinue'
Write-Host '====================================================='
Write-Host '  智慧景区系统 - 一键全关'
Write-Host '  停止: 游客8080 / 管理8081 / 后端8083 / cpolar'
Write-Host '        Redis 6379 / Ollama 11434 / (可选) MySQL 3306'
Write-Host '====================================================='

function Stop-Port($port) {
  $lines = netstat -ano | Select-String ":$port\s"
  if ($lines) {
    foreach ($l in $lines) {
      $parts = ($l.ToString().Trim() -split '\s+')
      $p = $parts[$parts.Length - 1]
      if ($p -match '^\d+$') { Stop-Process -Id ([int]$p) -Force -ErrorAction SilentlyContinue }
    }
  }
}

Write-Host '[1/4] 停止应用服务(8080/8081/8083)...'
Stop-Port 8080; Stop-Port 8081; Stop-Port 8083
Start-Sleep -Seconds 1

Write-Host '[2/4] 停止 cpolar 隧道...'
Get-Process -Name cpolar -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

Write-Host '[3/4] 停止 Redis (6379)...'
Stop-Port 6379
Get-Process -Name redis-server -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1

if ($StopMySQL) {
  Write-Host '[4/4] 停止 MySQL80 服务...'
  try { Stop-Service MySQL80 -ErrorAction Stop; Write-Host '      MySQL: [OK] 已停止' } catch { Write-Host '      MySQL: [!!] 停止失败（需要管理员权限），可手动停止 MySQL80 服务' }
} else {
  Write-Host '[4/4] MySQL80 为 Windows 服务，保持运行（如需一并关闭：stop-demo.ps1 -StopMySQL）'
}

if ($KeepOllama) {
  Write-Host '      Ollama: 保留运行（模型常驻；如需停止：stop-demo.ps1 不带 -KeepOllama）'
} else {
  Write-Host '      停止 Ollama (11434)...'
  Get-Process -Name 'ollama','ollama app' -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
  Write-Host '      Ollama: 已停止'
}

Write-Host ''
Write-Host '  已全部关闭。'
Write-Host '  一键重启：双击 demo-toggle.bat（或 start-demo.bat）'
if (-not $NoWait) { Write-Host '  按任意键关闭本窗口'; Read-Host }
