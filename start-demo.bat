@echo off
chcp 65001 >nul
title Smart-Scenic Demo Launcher
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-demo.ps1"
