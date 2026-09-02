param([switch]$NoWait)
$ErrorActionPreference = 'SilentlyContinue'
$root = 'D:\bishe'
$logDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

Write-Host '====================================================='
Write-Host '  智慧景区系统 - 一键启动（完整版）'
Write-Host '  Ollama 11434 / MySQL 3306 / Redis 6379 / 后端 8083'
Write-Host '  游客端 8080 / 管理端 8081'
Write-Host '====================================================='

function Test-Port([int]$port) {
  try { return (Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue -InformationLevel Quiet) } catch { return $false }
}
function Wait-Port([int]$port, [int]$maxSec) {
  for ($i = 0; $i -lt $maxSec; $i++) { if (Test-Port $port) { return $true }; Start-Sleep -Seconds 1 }
  return $false
}
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

Write-Host '[0/4] 停止旧实例...'
Stop-Port 8080; Stop-Port 8081; Stop-Port 8083
Start-Sleep -Seconds 2

# ---------- 1) Ollama ----------
Write-Host '[1/4] 检查 Ollama (11434)...'
if (Test-Port 11434) {
  Write-Host '      Ollama: [OK] 已在运行'
} else {
  $ollamaExe = Join-Path $env:LOCALAPPDATA 'Programs\Ollama\ollama.exe'
  if (Test-Path $ollamaExe) {
    Write-Host '      Ollama: 未运行，正在启动...'
    Start-Process -FilePath $ollamaExe -ArgumentList 'serve' -WorkingDirectory (Split-Path $ollamaExe) -WindowStyle Hidden
    if (Wait-Port 11434 60) { Write-Host '      Ollama: [OK] 已就绪' } else { Write-Host '      Ollama: [!!] 启动超时，查看是否有报错' }
  } else {
    Write-Host '      Ollama: [!!] 未找到 ollama.exe，请先安装 Ollama 并拉取 qwen2.5:3b / nomic-embed-text'
  }
}

# ---------- 2) MySQL + Redis ----------
Write-Host '[2/4] 检查 MySQL / Redis...'
if (Test-Port 3306) { Write-Host '      MySQL: [OK]' } else {
  Write-Host '      MySQL: 未运行，尝试启动服务 MySQL80...'
  try { Start-Service MySQL80 -ErrorAction Stop; if (Wait-Port 3306 30) { Write-Host '      MySQL: [OK] 已启动' } else { Write-Host '      MySQL: [!!] 启动超时' } } catch { Write-Host '      MySQL: [!!] 无法自动启动（需管理员），请手动启动 MySQL80 服务' }
}
if (Test-Port 6379) { Write-Host '      Redis: [OK]' } else {
  $redisExe = 'D:\bishe\tools\redis\redis-server.exe'
  if (-not (Test-Path $redisExe)) { $redisExe = (Get-Command redis-server -ErrorAction SilentlyContinue).Source }
  if ($redisExe -and (Test-Path $redisExe)) {
    $redisConf = 'D:\bishe\tools\redis\redis.conf'
    if (Test-Path $redisConf) { Start-Process -FilePath $redisExe -ArgumentList $redisConf -WorkingDirectory (Split-Path $redisExe) -WindowStyle Hidden }
    else { Start-Process -FilePath $redisExe -WindowStyle Hidden }
    if (Wait-Port 6379 15) { Write-Host '      Redis: [OK]' } else { Write-Host '      Redis: [!!] 启动失败，系统自动降级' }
  } else { Write-Host '      Redis: [!!] 未安装 Redis，系统自动降级（分布式锁/黑名单不可用）' }
}

# ---------- 3) 后端 ----------
Write-Host '[3/4] 启动后端 (8083, local profile)...'
$be = 'set JAVA_HOME=D:\bishe\tools\jdk-21.0.12+8&& D:\bishe\tools\apache-maven-3.9.9\bin\mvn.cmd -s D:\bishe\settings-local.xml -f D:\bishe\houduana\pom.xml spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.jvmArguments=-Djava.io.tmpdir=D:\bishe\logs\javatmpx > "D:\bishe\logs\backend.log" 2>&1'
Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $be -WorkingDirectory $root -WindowStyle Hidden
Write-Host '      等待后端就绪...'
$ready = $false
for ($i = 0; $i -lt 90; $i++) {
  Start-Sleep -Seconds 2
  try { $r = Invoke-WebRequest -Uri 'http://localhost:8083/api/spot/list' -UseBasicParsing -TimeoutSec 2; if ($r.StatusCode -eq 200) { $ready = $true; break } } catch {}
}
if ($ready) { Write-Host '      后端就绪 [OK]' } else { Write-Host '      后端启动超时，查看 logs\backend.log' }

# ---------- 4) 前端 ----------
Write-Host '[4/4] 启动游客端 (8080) + 管理端 (8081)...'
Start-Process -FilePath 'E:\nodejs\node.exe' -ArgumentList 'D:\bishe\tools\pwa-server\server.js' -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDir 'pwa8080.log') -RedirectStandardError (Join-Path $logDir 'pwa8080.err.log')
Start-Process -FilePath 'E:\nodejs\node.exe' -ArgumentList 'D:\bishe\tools\admin-server\server.js' -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDir 'admin8081.log') -RedirectStandardError (Join-Path $logDir 'admin8081.err.log')
Start-Sleep -Seconds 3

Write-Host ''
Write-Host '====================================================='
Write-Host '  全部启动完成，访问：'
Write-Host '  游客端: http://localhost:8080'
Write-Host '  管理端: http://localhost:8081'
Write-Host '  后端:   http://localhost:8083  |  Ollama: http://localhost:11434'
Write-Host '  日志:   D:\bishe\logs'
Write-Host '====================================================='
if (-not $NoWait) { Write-Host '  按任意键关闭本窗口（服务继续后台运行）'; Read-Host }
