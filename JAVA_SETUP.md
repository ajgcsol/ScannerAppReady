# Java Setup for Android Development

## Issue
Java 24 is too new for Gradle 8.13. You need Java 17 or Java 21 for Android development.

## Quick Solution - Download Java 17

### Option 1: Microsoft Build of OpenJDK (Recommended)
1. Download from: https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.msi
2. Run the installer
3. Default installation path: `C:\Program Files\Microsoft\jdk-17.x.x.x-hotspot\`

### Option 2: Eclipse Temurin (AdoptOpenJDK)
1. Go to: https://adoptium.net/temurin/releases/?version=17
2. Download Windows x64 MSI installer
3. Run the installer
4. Default installation path: `C:\Program Files\Eclipse Adoptium\jdk-17.x.x.x-hotspot\`

### Option 3: Oracle JDK 17
1. Go to: https://www.oracle.com/java/technologies/downloads/#java17
2. Download Windows x64 Installer
3. Run the installer
4. Default installation path: `C:\Program Files\Java\jdk-17\`

## After Installation

1. Update `gradle.properties`:
   ```properties
   org.gradle.java.home=C:/Program Files/Microsoft/jdk-17.0.11.9-hotspot
   ```
   (Adjust path based on your installation)

2. Set JAVA_HOME permanently:
   - Open System Properties → Environment Variables
   - Add new System Variable:
     - Name: `JAVA_HOME`
     - Value: `C:\Program Files\Microsoft\jdk-17.0.11.9-hotspot`
   - Add to PATH: `%JAVA_HOME%\bin`

3. Test the build:
   ```bash
   cd C:/android/ScannerAppReady
   ./gradlew build
   ```

## Alternative: Use Existing Java 24 with Gradle 9

If you prefer to keep Java 24, you can try upgrading Gradle:

1. Edit `gradle/wrapper/gradle-wrapper.properties`:
   ```properties
   distributionUrl=https\://services.gradle.org/distributions/gradle-9.0-milestone-1-bin.zip
   ```

2. Note: Gradle 9 is still in development and may have issues.