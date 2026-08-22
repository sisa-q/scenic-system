@echo off
chcp 65001 >nul
title Smart-Scenic Demo Stop
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-demo.ps1"
