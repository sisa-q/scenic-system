$ErrorActionPreference = 'SilentlyContinue'
$root = 'D:\bishe'
$logDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

Write-Host '============================================'
Write-Host '  智慧景区系统 - 一键启动'
Write-Host '  游客端 8080 / 管理端 8081 / 后端 8083'
Write-Host '============================================'

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

Write-Host '[1/3] 停止旧实例...'
Stop-Port 8080; Stop-Port 8081; Stop-Port 8083
Start-Sleep -Seconds 2

# MySQL check
$mysql = netstat -ano | Select-String ':3306\s'
if ($mysql) { Write-Host '      MySQL: [OK]' } else { Write-Host '      MySQL: [!!] 未运行，请先启动 MySQL' }
$redis = netstat -ano | Select-String ':6379\s'
if (-not $redis) { Write-Host '      Redis: [!!] 未运行，系统自动降级' }

Write-Host '[2/3] 启动后端 (8083, local profile)...'
$be = 'set JAVA_HOME=D:\bishe\tools\jdk-21.0.12+8&& D:\bishe\tools\apache-maven-3.9.9\bin\mvn.cmd -f D:\bishe\houduana\pom.xml spring-boot:run -Dspring-boot.run.profiles=local > "D:\bishe\logs\backend.log" 2>&1'
Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $be -WorkingDirectory $root -WindowStyle Hidden

Write-Host '      等待后端就绪...'
$ready = $false
for ($i = 0; $i -lt 90; $i++) {
  Start-Sleep -Seconds 2
  try { $r = Invoke-WebRequest -Uri 'http://localhost:8083/api/spot/list' -UseBasicParsing -TimeoutSec 2; if ($r.StatusCode -eq 200) { $ready = $true; break } } catch {}
}
if ($ready) { Write-Host '      后端就绪 [OK]' } else { Write-Host '      后端启动超时，查看 logs\backend.log' }

Write-Host '[3/3] 启动游客端 (8080) + 管理端 (8081)...'
Start-Process -FilePath 'E:\nodejs\node.exe' -ArgumentList 'D:\bishe\tools\pwa-server\server.js' -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDir 'pwa8080.log') -RedirectStandardError (Join-Path $logDir 'pwa8080.err.log')
Start-Process -FilePath 'E:\nodejs\node.exe' -ArgumentList 'D:\bishe\tools\admin-server\server.js' -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDir 'admin8081.log') -RedirectStandardError (Join-Path $logDir 'admin8081.err.log')
Start-Sleep -Seconds 3

Write-Host ''
Write-Host '============================================'
Write-Host '  全部启动完成，访问：'
Write-Host '  游客端: http://localhost:8080'
Write-Host '  管理端: http://localhost:8081'
Write-Host '  日志:   D:\bishe\logs'
Write-Host '============================================'
Write-Host '  按任意键关闭本窗口（服务继续后台运行）'
Read-Host