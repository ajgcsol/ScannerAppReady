# Firebase Setup Instructions

## Prerequisites
Firebase CLI is already installed on your system.

## Steps to Set Up Firebase

### 1. Authenticate with Firebase

Run one of these commands in your terminal:

```bash
# Interactive login (opens browser)
firebase login

# OR for CI/non-interactive environments
firebase login:ci
```

### 2. Create a Firebase Project

Option A: Using Firebase Console (Recommended)
1. Go to https://console.firebase.google.com/
2. Click "Create a project"
3. Enter project name: "scanner-app" (or your preferred name)
4. Enable Google Analytics (optional)
5. Click "Create project"

Option B: Using Firebase CLI
```bash
cd C:/android/ScannerAppReady
firebase projects:create scanner-app --display-name "Scanner App"
```

### 3. Add Android App to Firebase Project

In Firebase Console:
1. Click "Add app" and select Android
2. Enter package name: `com.yourorg.scanner`
3. Register app
4. Download `google-services.json`
5. Place it in `C:/android/ScannerAppReady/app/`

### 4. Initialize Firestore

In Firebase Console:
1. Go to Build → Firestore Database
2. Click "Create database"
3. Choose production or test mode
4. Select your preferred location
5. Click "Enable"

### 5. Enable Firebase in Your App

Once you have the `google-services.json` file:

1. Uncomment the Firebase plugin in `app/build.gradle`:
   ```gradle
   id 'com.google.gms.google-services'
   ```

2. Uncomment Firebase dependencies in `app/build.gradle`:
   ```gradle
   implementation platform('com.google.firebase:firebase-bom:33.1.2')
   implementation 'com.google.firebase:firebase-firestore-ktx'
   ```

### 6. Configure Firestore Security Rules

In Firebase Console → Firestore → Rules:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Allow authenticated users to read/write their own scan records
    match /scan_records/{document} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## Local Development Without Firebase

If you want to run the app without Firebase initially:
1. Keep the Firebase dependencies commented out
2. The app will use local Room database only
3. Firebase sync features will be disabled

## Troubleshooting

- If build fails with "google-services.json not found", ensure the file is in the `app/` directory
- For authentication issues, try `firebase login --reauth`
- Check that your package name matches in both `google-services.json` and `app/build.gradle`