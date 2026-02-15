@echo off
setlocal enabledelayedexpansion

:: FASTPAY Script Testing Suite - All-in-One Command
:: Usage: test_scripts.bat [OPTION]

title FASTPAY Script Testing Suite

echo ========================================
echo     FASTPAY SCRIPT TESTING SUITE       
echo ========================================
echo.

if "%1"=="" set "option=all"
if "%1" neq "" set "option=%1"

if "%option%"=="all" goto run_all_tests
if "%option%"=="quick" goto run_quick_tests
if "%option%"=="commands" goto test_script_commands
if "%option%"=="init" goto test_script_init
if "%option%"=="integration" goto test_integration
if "%option%"=="coverage" goto run_with_coverage
if "%option%"=="specific" goto run_specific_test
if "%option%"=="manual" goto show_manual_testing
if "%option%"=="help" goto show_help
if "%option%"=="-h" goto show_help
if "%option%"=="--help" goto show_help

echo [91m❌ Unknown option: %option%[0m
echo.
goto show_help

:show_help
echo [93mUSAGE:[0m
echo   test_scripts.bat [OPTION]
echo.
echo [93mOPTIONS:[0m
echo   all           Run ALL script tests (default)
echo   quick         Run quick tests only
echo   commands      Test script commands only
echo   init          Test script initialization only
echo   integration   Test integration only
echo   coverage      Run tests with coverage report
echo   specific      Run specific test method
echo   manual        Manual in-app testing guide
echo   help          Show this help message
echo.
echo [93mEXAMPLES:[0m
echo   test_scripts.bat                    # Run all tests
echo   test_scripts.bat quick              # Quick tests only
echo   test_scripts.bat coverage           # With coverage report
echo   test_scripts.bat specific           # Interactive test selection
echo.
goto end

:run_all_tests
echo [92m🚀 Running ALL Script Tests...[0m
echo.

gradlew.bat test --tests "com.example.fast.script.*"

if !errorlevel! equ 0 (
    echo [92m✅ ALL SCRIPT TESTS PASSED![0m
) else (
    echo [91m❌ SOME TESTS FAILED![0m
    exit /b 1
)
goto end

:run_quick_tests
echo [92m⚡ Running Quick Script Tests...[0m
echo.

echo [93m📋 Checking project compilation status...[0m
gradlew.bat compileDebugKotlin

if !errorlevel! neq 0 (
    echo [91m❌ Project has compilation errors - tests cannot run[0m
    echo [93m💡 Please fix compilation errors first, then run tests[0m
    echo.
    echo [94m📱 Manual Testing Guide (Available Now):[0m
    echo    Send these commands via remote interface:
    echo.
    echo    1. getActiveScripts
    echo    2. testMessageScript^|+1234567890^|Test message
    echo    3. testCommandScript^|sendSms^|phone=+1234567890^&message=Test
    echo    4. loadAxisScript
    echo    5. getScriptStats
    echo    6. syncScripts
    echo.
    exit /b 1
)

echo [92m✅ Compilation successful - Running tests...[0m
echo [93m⚠️  Note: Android Gradle doesn't support --tests filter directly[0m
echo [93m📊 Running all unit tests (includes script tests)...[0m
echo.

gradlew.bat test

if !errorlevel! equ 0 (
    echo [92m✅ QUICK TESTS PASSED![0m
) else (
    echo [91m❌ QUICK TESTS FAILED![0m
    exit /b 1
)
goto end

:test_script_commands
echo [92m📝 Testing Script Commands...[0m
echo.

gradlew.bat test --tests "com.example.fast.script.ScriptTestCommandsTest"

if !errorlevel! equ 0 (
    echo [92m✅ SCRIPT COMMAND TESTS PASSED![0m
) else (
    echo [91m❌ SCRIPT COMMAND TESTS FAILED![0m
    exit /b 1
)
goto end

:test_script_init
echo [92m🔧 Testing Script Initialization...[0m
echo.

gradlew.bat test --tests "com.example.fast.script.ScriptInitializerTest"

if !errorlevel! equ 0 (
    echo [92m✅ SCRIPT INITIALIZATION TESTS PASSED![0m
) else (
    echo [91m❌ SCRIPT INITIALIZATION TESTS FAILED![0m
    exit /b 1
)
goto end

:test_integration
echo [92m🔗 Testing Script Integration...[0m
echo.

gradlew.bat test --tests "com.example.fast.service.ScriptCommandsIntegrationTest"

if !errorlevel! equ 0 (
    echo [92m✅ INTEGRATION TESTS PASSED![0m
) else (
    echo [91m❌ INTEGRATION TESTS FAILED![0m
    exit /b 1
)
goto end

:run_with_coverage
echo [92m📊 Running Tests with Coverage Report...[0m
echo.

gradlew.bat test --tests "com.example.fast.script.*" jacocoTestReport

if !errorlevel! equ 0 (
    echo [92m✅ TESTS WITH COVERAGE COMPLETED![0m
    echo [93m📈 Coverage report available at: build\reports\jacoco\test\html\index.html[0m
) else (
    echo [91m❌ COVERAGE TESTS FAILED![0m
    exit /b 1
)
goto end

:run_specific_test
echo [92m🎯 Specific Test Selection[0m
echo.
echo [93mAvailable Test Methods:[0m
echo 1. getActiveScripts
echo 2. testMessageProcessing
echo 3. testCommandValidation
echo 4. loadAxisScript
echo 5. getScriptStatistics
echo 6. forceSyncScripts
echo 7. initialize ^(ScriptInitializer^)
echo 8. integration tests
echo.
set /p choice="Enter test number (1-8): "

if "!choice!"=="1" (
    echo [92mRunning getActiveScripts test...[0m
    gradlew.bat test --tests "*getActiveScripts*"
)
if "!choice!"=="2" (
    echo [92mRunning testMessageProcessing test...[0m
    gradlew.bat test --tests "*testMessageProcessing*"
)
if "!choice!"=="3" (
    echo [92mRunning testCommandValidation test...[0m
    gradlew.bat test --tests "*testCommandValidation*"
)
if "!choice!"=="4" (
    echo [92mRunning loadAxisScript test...[0m
    gradlew.bat test --tests "*loadAxisScript*"
)
if "!choice!"=="5" (
    echo [92mRunning getScriptStatistics test...[0m
    gradlew.bat test --tests "*getScriptStatistics*"
)
if "!choice!"=="6" (
    echo [92mRunning forceSyncScripts test...[0m
    gradlew.bat test --tests "*forceSyncScripts*"
)
if "!choice!"=="7" (
    echo [92mRunning ScriptInitializer tests...[0m
    gradlew.bat test --tests "*ScriptInitializer*"
)
if "!choice!"=="8" (
    echo [92mRunning integration tests...[0m
    gradlew.bat test --tests "*ScriptCommandsIntegration*"
)

if not defined choice (
    echo [91m❌ Invalid choice![0m
    exit /b 1
)

if !errorlevel! equ 0 (
    echo [92m✅ SPECIFIC TEST PASSED![0m
) else (
    echo [91m❌ SPECIFIC TEST FAILED![0m
    exit /b 1
)
goto end

:show_manual_testing
echo [92m📱 Manual In-App Testing Guide[0m
echo.
echo [93mSend these commands via remote interface:[0m
echo.
echo [94m1. Get Active Scripts:[0m
echo    Command: getActiveScripts
echo    Expected: Returns list of active scripts
echo.
echo [94m2. Test Message Processing:[0m
echo    Command: testMessageScript^|+1234567890^|Test message
echo    Expected: Message processed through script system
echo.
echo [94m3. Test Command Validation:[0m
echo    Command: testCommandScript^|sendSms^|phone=+1234567890^&message=Test^&sim=1
echo    Expected: Command validated through script system
echo.
echo [94m4. Load AXIS Script:[0m
echo    Command: loadAxisScript
echo    Expected: AXIS banking script loaded
echo.
echo [94m5. Get Script Statistics:[0m
echo    Command: getScriptStats
echo    Expected: Returns script execution statistics
echo.
echo [94m6. Force Script Sync:[0m
echo    Command: syncScripts
echo    Expected: Scripts synchronized from server
echo.
echo [93m💡 Tips:[0m
echo - Check logs for detailed execution information
echo - Monitor Firebase for tracking events
echo - Verify command history updates
goto end

:end
echo.
echo [92m🎉 Script Testing Complete![0m
echo ========================================
pause
