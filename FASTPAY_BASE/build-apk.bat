@echo off
REM Quick wrapper for build-apk.ps1 script

powershell.exe -ExecutionPolicy Bypass -File "%~dp0build-apk.ps1" %*
