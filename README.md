# NullXoidAndroid

Android frontend for NullXoid, built with Kotlin, Jetpack Compose, and Gradle.

## Current Scope

- Login against the existing NullXoid backend
- Chat list and chat detail views
- Streaming chat replies over the backend stream API
- Backend-backed model discovery and model selection
- Health screen for backend/runtime status
- Settings screen with configurable backend base URL
- Experimental embedded on-device backend scaffold for local streaming development
- In-app debug APK update check, download, and installer handoff backed by GitHub prereleases

This repository is intended to stay aligned with the current NullXoid backend contract rather than introducing Android-specific API drift.

## Requirements

- Android Studio with Android SDK 34
- JDK 17
- Android device or emulator running Android 8.0+ (API 26+)

## Backend Configuration

The app defaults to `http://10.0.2.2:8090`, which points an Android emulator back to a backend running on the host machine.

You can change the backend URL from the in-app Settings screen.

For physical devices, replace the default with a reachable LAN or tunnel URL for the backend.

## Build

From the repository root:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK will be produced under `app/build/outputs/apk/debug/`.

Pushes to `main` also run the GitHub Actions APK workflow and publish prerelease debug APK artifacts under both a versioned tag and `latest-debug`.

The app can check for a newer prerelease from Settings, download the APK, and launch Android's package installer. Android still requires the user to approve the install prompt and allow installs from this app when sideloading is not already enabled.

## Run

1. Start the NullXoid backend.
2. Launch an emulator or connect a device.
3. Install from Android Studio or run:

```powershell
.\gradlew.bat installDebug
```

4. Open the app, verify the backend URL in Settings, and sign in.

## Project Layout

- `app/src/main/java/com/nullxoid/android/data`: backend API, models, repository, and local settings store
- `app/src/main/java/com/nullxoid/android/ui`: Compose navigation, screens, and app view model
- `app/src/main/res`: Android resources and network/security config
