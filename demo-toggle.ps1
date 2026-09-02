$ErrorActionPreference = 'SilentlyContinue'
param([switch]$NoWait)
$backendUp = Test-NetConnection -ComputerName 127.0.0.1 -Port 8083 -WarningAction SilentlyContinue -InformationLevel Quiet
if ($backendUp) {
  Write-Host '====================================================='
  Write-Host '  系统运行中 → 执行【一键全关】'
  Write-Host '====================================================='
  if ($NoWait) { & 'D:\bishe\stop-demo.ps1' -NoWait } else { & 'D:\bishe\stop-demo.ps1' }
} else {
  Write-Host '====================================================='
  Write-Host '  系统未运行 → 执行【一键全启】'
  Write-Host '====================================================='
  if ($NoWait) { & 'D:\bishe\start-demo.ps1' -NoWait } else { & 'D:\bishe\start-demo.ps1' }
}
