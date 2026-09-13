@echo off
echo =======================================================
echo  Building PortGenZ Android Application...
echo =======================================================

cd /d "%~dp0android"
call gradlew.bat assembleDebug

if %ERRORLEVEL% equ 0 (
    copy /y "app\build\outputs\apk\debug\app-debug.apk" "..\PortGenZ.apk" >nul
    echo.
    echo =======================================================
    echo  [SUCCESS] Build completed successfully!
    echo  Generated APK: PortGenZ.apk (Project Root)
    echo =======================================================
) else (
    echo.
    echo [ERROR] Build failed. Please check the error logs above.
)
pause
