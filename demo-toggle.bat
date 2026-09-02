@echo off
chcp 65001 >nul
title Smart-Scenic One-Click Toggle
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0demo-toggle.ps1"

