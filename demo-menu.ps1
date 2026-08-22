$ErrorActionPreference = 'SilentlyContinue'
$root = 'D:\bishe'
Write-Host '============================================='
Write-Host '  智慧景区系统 - 演示控制台'
Write-Host '============================================='
while ($true) {
  Write-Host ''
  Write-Host '  请选择操作：'
  Write-Host '    1. 一键启动（含支付宝回调隧道）'
  Write-Host '    2. 一键关闭（保留 MySQL）'
  Write-Host '    3. 退出'
  $choice = Read-Host '  请输入 1 / 2 / 3'
  if ($choice -eq '1') {
    & (Join-Path $root 'start-demo-pay.ps1')
  } elseif ($choice -eq '2') {
    & (Join-Path $root 'stop-demo.ps1')
  } elseif ($choice -eq '3') {
    Write-Host '  已退出。'
    break
  } else {
    Write-Host '  输入无效，请重新输入。'
  }
}
