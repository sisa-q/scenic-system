$ErrorActionPreference = 'SilentlyContinue'
Write-Host '============================================='
Write-Host '  智慧景区系统 - 一键关闭'
Write-Host '  停止: 游客8080 / 管理8081 / 后端8083 / cpolar隧道'
Write-Host '============================================='

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

Write-Host '[1/2] 停止本地服务(8080/8081/8083)...'
Stop-Port 8080; Stop-Port 8081; Stop-Port 8083
Start-Sleep -Seconds 1

Write-Host '[2/2] 停止 cpolar 隧道...'
Get-Process -Name cpolar -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1

Write-Host ''
Write-Host '  已全部停止。随时可双击 start-demo-pay.bat 重新启动。'
Write-Host '  按任意键关闭本窗口'
Read-Host
