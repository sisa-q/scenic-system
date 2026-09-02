$ErrorActionPreference = 'SilentlyContinue'
$root = 'D:\bishe'
$logDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

Write-Host '====================================================='
Write-Host '  智慧景区系统 - 一键启动(含支付宝回调隧道)'
Write-Host '  Ollama 11434 / 游客8080 / 管理8081 / 后端8083'
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

Write-Host '[1/6] 停止旧实例...'
Stop-Port 8080; Stop-Port 8081; Stop-Port 8083
Get-Process -Name cpolar -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

Write-Host '[2/6] 检查 Ollama (11434)...'
if (Test-Port 11434) { Write-Host '      Ollama: [OK]' } else {
  $ollamaExe = Join-Path $env:LOCALAPPDATA 'Programs\Ollama\ollama.exe'
  if (Test-Path $ollamaExe) {
    Start-Process -FilePath $ollamaExe -ArgumentList 'serve' -WorkingDirectory (Split-Path $ollamaExe) -WindowStyle Hidden
    if (Wait-Port 11434 60) { Write-Host '      Ollama: [OK]' } else { Write-Host '      Ollama: [!!] 启动超时' }
  } else { Write-Host '      Ollama: [!!] 未找到 ollama.exe' }
}

Write-Host '[3/6] 检查 MySQL / Redis...'
if (Test-Port 3306) { Write-Host '      MySQL: [OK]' } else {
  try { Start-Service MySQL80 -ErrorAction Stop; if (Wait-Port 3306 30) { Write-Host '      MySQL: [OK]' } else { Write-Host '      MySQL: [!!] 启动超时' } } catch { Write-Host '      MySQL: [!!] 无法自动启动，请手动启动 MySQL80' }
}
if (Test-Port 6379) { Write-Host '      Redis: [OK]' } else {
  $redisExe = 'D:\bishe\tools\redis\redis-server.exe'
  if (-not (Test-Path $redisExe)) { $redisExe = (Get-Command redis-server -ErrorAction SilentlyContinue).Source }
  if ($redisExe -and (Test-Path $redisExe)) {
    $redisConf = 'D:\bishe\tools\redis\redis.conf'
    if (Test-Path $redisConf) { Start-Process -FilePath $redisExe -ArgumentList $redisConf -WorkingDirectory (Split-Path $redisExe) -WindowStyle Hidden }
    else { Start-Process -FilePath $redisExe -WindowStyle Hidden }
    if (Wait-Port 6379 15) { Write-Host '      Redis: [OK]' } else { Write-Host '      Redis: [!!] 启动失败，系统自动降级' }
  } else { Write-Host '      Redis: [!!] 未安装 Redis，系统自动降级' }
}

Write-Host '[4/6] 启动游客端(8080)与管理端(8081)...'
Start-Process -FilePath 'E:\nodejs\node.exe' -ArgumentList 'D:\bishe\tools\pwa-server\server.js' -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDir 'pwa8080.log') -RedirectStandardError (Join-Path $logDir 'pwa8080.err.log')
Start-Process -FilePath 'E:\nodejs\node.exe' -ArgumentList 'D:\bishe\tools\admin-server\server.js' -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDir 'admin8081.log') -RedirectStandardError (Join-Path $logDir 'admin8081.err.log')
Start-Sleep -Seconds 2

Write-Host '[5/6] 启动 cpolar 隧道(指向8080)...'
$cpolar = 'D:\bishe\tools\cpolar\extract\cpolar\cpolar.exe'
if (-not (Test-Path $cpolar)) { Write-Host '  cpolar 未找到！'; $cpolar = 'cpolar' }
Start-Process -FilePath $cpolar -ArgumentList 'http','8080','-region','cn','-log','stdout' -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDir 'cpolar.log') -RedirectStandardError (Join-Path $logDir 'cpolar.err.log')
$tunnelUrl = ''
for ($i = 0; $i -lt 30; $i++) {
  Start-Sleep -Seconds 2
  try {
    $log = Get-Content (Join-Path $logDir 'cpolar.log') -Raw
    $m = [regex]::Match($log, 'https://[a-zA-Z0-9-]+\.r\d+\.cpolar\.(?:cn|top)')
    if ($m.Success) { $tunnelUrl = $m.Value; break }
  } catch {}
}
if (-not $tunnelUrl) { Write-Host '  [!!] 未检测到隧道域名，检查 logs\cpolar.log'; $tunnelUrl = 'https://FIXME.r7.cpolar.cn' }
Write-Host "  隧道: $tunnelUrl"

Write-Host '[6/6] 启动后端(8083) + 注入支付宝回调地址...'
$notify = $tunnelUrl + '/api/pay/notify/alipay'
$return = $tunnelUrl + '/pay'
Write-Host "  通知地址(notify): $notify"
Write-Host "  返回地址(return): $return"
$be = "set JAVA_HOME=D:\bishe\tools\jdk-21.0.12+8&& set PAY_NOTIFY_URL=$notify&& set PAY_RETURN_URL=$return&& D:\bishe\tools\apache-maven-3.9.9\bin\mvn.cmd -s D:\bishe\settings-local.xml -f D:\bishe\houduana\pom.xml spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.jvmArguments=-Djava.io.tmpdir=D:\bishe\logs\javatmpx > $logDir\backend.log 2>&1"
Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $be -WorkingDirectory $root -WindowStyle Hidden

$ready = $false
for ($i = 0; $i -lt 90; $i++) {
  Start-Sleep -Seconds 2
  try { $r = Invoke-WebRequest -Uri 'http://localhost:8083/api/spot/list' -UseBasicParsing -TimeoutSec 2; if ($r.StatusCode -eq 200) { $ready = $true; break } } catch {}
}
if ($ready) { Write-Host '  后端就绪 [OK]' } else { Write-Host '  [!!] 后端启动超时，查 logs\backend.log' }

Write-Host '[7/7] 验证隧道可达后端...'
try { $t = Invoke-WebRequest -Uri ($tunnelUrl + '/api/spot/list') -UseBasicParsing -TimeoutSec 12; Write-Host "  隧道API: $($t.StatusCode) [OK]" } catch { Write-Host '  [!!] 隧道API 不可达（cpolar 免费隧道可能需等待/需登录），稍后重试' }

Write-Host ''
Write-Host '====================================================='
Write-Host '  完成！访问：'
Write-Host "  游客端: $tunnelUrl  (手机公网也能访问)"
Write-Host '  管理端: http://localhost:8081'
Write-Host '  支付宝回调: notify/return 已自动指向当前隧道'
Write-Host '====================================================='
Write-Host '  按任意键关闭本窗口（服务与隧道继续后台运行）'
Read-Host
