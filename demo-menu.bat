@echo off
chcp 65001 >nul
title Smart-Scenic Demo Menu
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0demo-menu.ps1"
