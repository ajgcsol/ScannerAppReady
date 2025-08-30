@echo off
echo ========================================
echo Scanner Pro - Build and Test Script
echo ========================================
echo.

set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set ANDROID_SDK=C:\Users\agregware\AppData\Local\Android\Sdk
set PATH=%JAVA_HOME%\bin;%ANDROID_SDK%\platform-tools;%ANDROID_SDK%\emulator;%PATH%

echo Step 1: Environment Check
echo -------------------------
echo Java Home: %JAVA_HOME%
echo Android SDK: %ANDROID_SDK%
echo.

echo Checking Java version...
java -version
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Java not found! Please install JDK 17 or later.
    pause
    exit /b 1
)

echo.
echo Step 2: Clean and Build
echo -----------------------
echo Cleaning previous build...
call gradlew.bat clean --no-daemon

echo Building debug APK...
call gradlew.bat assembleDebug --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Build failed!
    pause
    exit /b 1
)

echo.
echo Step 3: Check for Running Emulator
echo ----------------------------------
adb devices | findstr "emulator" > nul
if %ERRORLEVEL% NEQ 0 (
    echo No emulator running. Starting Medium_Tablet...
    start "Tablet Emulator" emulator.exe -avd Medium_Tablet -no-snapshot -no-audio
    echo Waiting for emulator to boot...
    timeout /t 45 /nobreak > nul
) else (
    echo Emulator already running.
)

echo.
echo Step 4: Install and Test App
echo ----------------------------
for /f "tokens=1" %%i in ('adb devices ^| findstr "emulator"') do (
    set EMULATOR_ID=%%i
    goto :install
)

:install
echo Installing app on %EMULATOR_ID%...
adb -s %EMULATOR_ID% install -r app\build\outputs\apk\debug\app-debug.apk
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Installation failed!
    pause
    exit /b 1
)

echo.
echo Step 5: Launch and Test
echo -----------------------
echo Launching Scanner Pro...
adb -s %EMULATOR_ID% shell am start -n com.yourorg.scanner/.MainActivity

echo.
echo Testing basic functionality...
echo - App should launch successfully
echo - Firebase connection should show as Connected
echo - Tap the Scan button to test mock scanning
echo - Try the Export menu for CSV/Excel export
echo - Test email sharing functionality

echo.
echo ========================================
echo Build and Test Complete!
echo ========================================
echo.
echo APK Location: app\build\outputs\apk\debug\app-debug.apk
echo App Package: com.yourorg.scanner
echo.
echo Next Steps:
echo 1. Test all features on the emulator
echo 2. Add Honeywell SDK when available
echo 3. Configure Firebase security rules
echo 4. Test on physical device
echo 5. Build release APK for deployment
echo.

echo Press any key to continue testing or close to exit...
pause