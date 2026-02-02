@echo off
REM FastPay APK Zip Creator - Batch Wrapper
REM Double-click to create zip of APK files

echo =========================================
echo FastPay APK Zip Creator
echo =========================================
echo.

REM Check if PowerShell is available
where powershell >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Error: PowerShell is required but not found.
    pause
    exit 1
)

REM Run PowerShell script
powershell -ExecutionPolicy Bypass -File "%~dp0zip-apk.ps1" %*

if %ERRORLEVEL% EQU 0 (
    echo.
    echo =========================================
    echo Zip created successfully!
    echo =========================================
) else (
    echo.
    echo =========================================
    echo Error creating zip file.
    echo =========================================
)

echo.
pause
