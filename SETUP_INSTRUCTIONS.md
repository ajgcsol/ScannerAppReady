# Scanner Pro - Complete Setup Instructions

## 🚀 Overview

Scanner Pro is a comprehensive Android barcode scanning application with:
- **Beautiful Material Design 3 UI** with animations and modern styling
- **Real-time Firebase sync** for shared scan lists
- **Local Room database** with offline support
- **Advanced export functionality** (CSV, Excel, Email)
- **Honeywell scanner integration** (ready for SDK)
- **Professional tablet-optimized interface**

## 📋 Prerequisites

### Development Environment
- **Android Studio** (latest version)
- **JDK 17 or 21** (included with Android Studio)
- **Windows 10/11** (for deployment scripts)

### Device Requirements
- **Android 13 (API 33)** or higher
- **8-inch tablet** (optimized for tablets)
- **Honeywell N6703 scanner** (or compatible)

## 🛠️ Quick Setup (5 Minutes)

### 1. Build and Run
```bash
cd C:\android\ScannerAppReady
build-and-test.bat
```

### 2. Manual Build (if script fails)
```bash
# Set Java path
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr

# Build and install
gradlew assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Launch app
adb shell am start -n com.yourorg.scanner/.MainActivity
```

## 🔧 Detailed Configuration

### Firebase Setup (Optional but Recommended)

1. **Firebase Console Setup**
   - Go to https://console.firebase.google.com/
   - Create project: "scanner-app-production"
   - Add Android app with package: `com.yourorg.scanner`

2. **Enable Firestore**
   - Navigate to Firestore Database
   - Create database in production mode
   - Choose location: `us-central1`

3. **Security Rules**
   ```javascript
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /lists/{listId}/scans/{scanId} {
         allow read, write: if request.auth != null;
       }
       // Allow anonymous read/write for demo (tighten for production)
       match /{document=**} {
         allow read, write: if true;
       }
     }
   }
   ```

4. **Download Configuration**
   - Download `google-services.json`
   - Place in `app/` directory
   - Rebuild app

### Honeywell Scanner Integration

1. **Obtain SDK**
   - Contact Honeywell Technical Support
   - Download DataCollection.aar
   - Or get from your device vendor

2. **Install SDK**
   - Copy `DataCollection.aar` to `app/libs/`
   - Add to `app/build.gradle`:
     ```gradle
     implementation files('libs/DataCollection.aar')
     ```

3. **Enable Real Scanner**
   - Edit `HoneywellScanner.kt`
   - Uncomment real SDK imports and code
   - Remove mock implementation

4. **Test Hardware**
   ```bash
   # Test scan wedge mode
   # Open any text field and scan - should type barcode data
   
   # Test SDK mode with app
   # Use app's scan button - should capture via AIDC
   ```

## 📱 App Features

### Core Functionality
- ✅ **Mock Scanning** (ready for real scanner)
- ✅ **Firebase Cloud Sync** 
- ✅ **Local Database Storage**
- ✅ **Real-time UI Updates**
- ✅ **Error Handling & Logging**

### Export & Sharing
- ✅ **CSV Export** (immediate)
- ⚠️ **Excel Export** (CSV fallback until POI configured)
- ✅ **Email Integration** with attachments
- ✅ **Share to any app**

### UI/UX Features
- ✅ **Material Design 3** with dynamic colors
- ✅ **Responsive tablet layout**
- ✅ **Smooth animations** and transitions
- ✅ **Status indicators** (connection, scan count)
- ✅ **Last scan highlighting**
- ✅ **Error messages** with auto-dismiss

## 🧪 Testing Checklist

### Basic Functionality
- [ ] App launches without crashes
- [ ] Firebase shows "Connected" status
- [ ] Scan button triggers mock scan
- [ ] Scans appear in list immediately
- [ ] Scan counter updates correctly

### Export Features
- [ ] CSV export creates file
- [ ] Email share opens with attachment
- [ ] File sharing works with other apps
- [ ] Export includes all scan data

### Data Sync
- [ ] Scans persist after app restart
- [ ] Firebase sync works (check console)
- [ ] Offline mode works without Firebase
- [ ] Error handling works gracefully

### UI Polish
- [ ] All animations work smoothly
- [ ] Status cards update correctly
- [ ] Last scan card shows latest data
- [ ] Export dialog functions properly

## 🚨 Troubleshooting

### Build Issues
```bash
# Java not found
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr

# Gradle daemon issues
gradlew --stop
gradlew clean

# Dependency conflicts
gradlew build --refresh-dependencies
```

### Runtime Issues
```bash
# App crashes on launch
adb logcat | grep -i scanner

# Firebase connection issues
# Check google-services.json placement
# Verify Firebase project settings

# Scanner not working
# Confirm DataCollection.aar is included
# Check manifest permissions
```

### Export Issues
```bash
# CSV export fails
# Check external storage permissions
# Verify FileProvider configuration

# Email sharing doesn't work
# Test with different email apps
# Check file URI permissions
```

## 📂 Project Structure

```
ScannerAppReady/
├── app/
│   ├── src/main/java/com/yourorg/scanner/
│   │   ├── MainActivity.kt              # Main UI with Material Design 3
│   │   ├── ScannerViewModel.kt          # State management & business logic
│   │   ├── scanner/
│   │   │   └── HoneywellScanner.kt      # Scanner integration (ready for SDK)
│   │   ├── export/
│   │   │   └── ExportManager.kt         # CSV/Excel export & email sharing
│   │   ├── data/
│   │   │   ├── room/AppDb.kt           # Local database
│   │   │   └── sync/FirestoreSync.kt   # Cloud synchronization
│   │   ├── model/ScanRecord.kt         # Data model
│   │   └── ui/theme/Theme.kt           # Material Design 3 theme
│   ├── google-services.json            # Firebase configuration
│   └── build.gradle                    # Dependencies & configuration
├── build-and-test.bat                  # Automated build & test script
├── run-on-tablet.bat                   # Quick launch script
└── SETUP_INSTRUCTIONS.md              # This file
```

## 🎯 Production Deployment

### Release Build
```bash
# Generate signed APK
gradlew assembleRelease

# Install release build
adb install -r app\build\outputs\apk\release\app-release.apk
```

### Security Considerations
1. **Tighten Firebase rules** for production
2. **Add authentication** for multi-user support
3. **Enable ProGuard/R8** for code obfuscation
4. **Test thoroughly** on target devices

### MDM Deployment
- Export signed APK
- Deploy via your Mobile Device Management solution
- Configure app permissions as needed
- Test on pilot devices before full rollout

## 💬 Support

### Known Issues
- Excel export falls back to CSV (POI configuration needed)
- Mock scanner for development (real scanner needs SDK)
- Firebase rules wide open for demo (tighten for production)

### Getting Help
1. Check `adb logcat` for runtime errors
2. Review Firebase Console for sync issues
3. Test on emulator before physical device
4. Verify all dependencies are properly configured

---

**Scanner Pro** - Built with ❤️ using Kotlin, Jetpack Compose, and Material Design 3