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
- Persistent embedded-mode chat storage backed by local SQLite
- In-app debug APK update check, download, and installer handoff backed by GitHub prereleases

This repository is intended to stay aligned with the current NullXoid backend contract rather than introducing Android-specific API drift.

## Requirements

- Android Studio with Android SDK 34
- JDK 17
- Android device or emulator running Android 8.0+ (API 26+)

## Backend Configuration

The app defaults to `http://localhost:8090`. Configure `Backend URL` in settings for the backend reachable from your emulator or device.

You can change the backend URL from the in-app Settings screen.

For physical devices, replace the default with a reachable LAN or tunnel URL for the backend.

Embedded backend mode runs a local Ktor server inside the Android app on `http://127.0.0.1:8090`. The embedded engine can be set to:

- `Echo`: deterministic local streaming for smoke testing with no model dependency.
- `Ollama`: relays embedded backend chat streams to an Ollama server using the configured Ollama URL and model.

For local Ollama testing, configure `Ollama URL` for the endpoint reachable from your emulator or device. Physical devices should use an approved LAN, tunnel, or remote route that matches your security policy.

## Build

From the repository root:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK will be produced under `app/build/outputs/apk/debug/`.

Pushes to `main` also run the GitHub Actions APK workflow and publish prerelease debug APK artifacts under both a versioned tag and `latest-debug`.

The app checks for newer prereleases at startup, every six hours while running, and whenever the user taps Check in Settings. When an update is available, it shows an in-app prompt with Update and Later actions. Update downloads the APK and launches Android's package installer; Android still requires the user to approve the install prompt and allow installs from this app when sideloading is not already enabled.

## Run

1. Start the NullXoid backend.
2. Launch an emulator or connect a device.
3. Install from Android Studio or run:

```powershell
.\gradlew.bat installDebug
```

4. Open the app, verify the backend URL in Settings, and sign in.

## E2E Testing

The first instrumentation E2E test runs entirely on the app's embedded Echo backend. It does not need the desktop/backend server:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:PATH="$env:JAVA_HOME\bin;$env:LOCALAPPDATA\Android\Sdk\platform-tools;$env:PATH"

adb devices -l
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.nullxoid.android.NullXoidEmbeddedE2eTest
```

If your phone is not listed by `adb devices -l`, enable Developer Options, turn on USB debugging, reconnect USB, and accept the RSA prompt on the phone.

For real-world backend testing on a physical phone:

1. Put the phone and backend host on the same reachable network.
2. Install the debug APK from `app/build/outputs/apk/debug/app-debug.apk`.
3. Open Settings in the app.
4. Set `Backend URL` to the phone-reachable backend URL, for example `http://192.168.1.x:8090`.
5. Save, return to login, sign in, open Health, refresh models, then send a chat message.

Debug builds permit cleartext HTTP so LAN backend testing works. Release builds keep the stricter network security config.

## Project Layout

- `app/src/main/java/com/nullxoid/android/data`: backend API, models, repository, and local settings store
- `app/src/main/java/com/nullxoid/android/ui`: Compose navigation, screens, and app view model
- `app/src/main/res`: Android resources and network/security config
