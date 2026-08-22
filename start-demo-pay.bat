@echo off
chcp 65001 >nul
title Smart-Scenic Demo (Pay Tunnel)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-demo-pay.ps1"
