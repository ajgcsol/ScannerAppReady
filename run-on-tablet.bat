@echo off
echo ========================================
echo Scanner App - Tablet Emulator Runner
echo ========================================
echo.

set ANDROID_SDK=C:\Users\agregware\AppData\Local\Android\Sdk
set EMULATOR_NAME=Medium_Tablet
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PATH=%JAVA_HOME%\bin;%ANDROID_SDK%\platform-tools;%ANDROID_SDK%\emulator;%PATH%

echo Step 1: Building latest APK...
call gradlew.bat assembleDebug --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo Build failed! Exiting.
    pause
    exit /b 1
)

echo.
echo Step 2: Checking if emulator is running...
adb devices | findstr "emulator" > nul
if %ERRORLEVEL% NEQ 0 (
    echo Starting tablet emulator...
    start "Tablet Emulator" emulator.exe -avd %EMULATOR_NAME% -no-snapshot -no-audio
    echo Waiting for emulator to boot...
    timeout /t 30 /nobreak > nul
)

echo.
echo Step 3: Installing app on emulator...
for /f "tokens=1" %%i in ('adb devices ^| findstr "emulator"') do (
    set EMULATOR_ID=%%i
    goto :install
)

:install
echo Installing on %EMULATOR_ID%...
adb -s %EMULATOR_ID% install -r app\build\outputs\apk\debug\app-debug.apk
if %ERRORLEVEL% EQU 0 (
    echo.
    echo Step 4: Launching Scanner App...
    adb -s %EMULATOR_ID% shell am start -n com.yourorg.scanner/.MainActivity
    echo.
    echo ✓ Scanner App launched successfully on tablet emulator!
    echo.
    echo The app should now be running on your tablet emulator.
    echo You can also find it in the app drawer as "ScannerApp"
) else (
    echo Installation failed! Check the error messages above.
)

echo.
pause