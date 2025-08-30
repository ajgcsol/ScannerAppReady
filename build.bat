@echo off
echo Setting up Java environment...
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PATH=%JAVA_HOME%\bin;%PATH%

echo Java Home: %JAVA_HOME%
echo.
echo Building Android app...
call gradlew.bat build

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Build successful!
    echo APK location: app\build\outputs\apk\
) else (
    echo.
    echo Build failed! Check the error messages above.
)
pause