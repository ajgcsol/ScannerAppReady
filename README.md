# ScannerApp (Realtime Shared Lists) – Ready Skeleton

This project is preconfigured so you **don’t need to import the AAR manually**.
We wrapped the Honeywell `DataCollection.aar` inside the `:honeywellAar` module.
Just replace the placeholder AAR with the real one and run.

## Folder layout
```
ScannerApp/
├─ app/                        # Your application module
├─ honeywellAar/               # AAR wrapper module (already included)
│  └─ libs/
│     └─ DataCollection.aar    # ← Replace this placeholder with the real AAR
├─ settings.gradle             # includes :app and :honeywellAar
└─ build.gradle                # adds Google Services classpath
```

## What you must do
1) **Replace the placeholder AAR**
   - Put the vendor-provided **DataCollection.aar** file at:
     `honeywellAar/libs/DataCollection.aar` (overwrite the placeholder).

2) **Add Firebase** (for realtime shared lists)
   - In Firebase console, add your Android app and download `google-services.json`.
   - Place it in `app/`.

3) **Gradle Sync & Run**
   - Open the project in Android Studio → wait for Gradle sync → Run on your device.

## Why this structure?
- Honeywell recommends adding the **DataCollection** library as a module and adding the decode permission in the manifest. This project automates that by wrapping the AAR in a small library module that the app depends on.  
  References: Honeywell SDK setup & required permission.  
- Decode permission required: `com.honeywell.decode.permission.DECODE`.

## Exports
- CSV/TSV via `ExportDelimited`
- Fixed-width text via `ExportFixedWidth`

## Realtime upload function
- Implemented in `data/sync/FirestoreSync.kt` using Firestore `set()` and realtime listeners (with offline persistence enabled by default on Android).
```
addScan(listId, scan) // upserts a scan doc
listenScans(listId)   // stream updates in realtime
```

## Notes
- The placeholder AAR is not functional; you must replace it with the real Honeywell SDK AAR from your vendor account.
- If you prefer PocketBase or Supabase later, we can swap the sync implementation.
