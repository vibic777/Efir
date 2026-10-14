@echo off
REM Обёртка для scripts\sign-windows.ps1 — подпись MSI/EXE.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0sign-windows.ps1" %*
