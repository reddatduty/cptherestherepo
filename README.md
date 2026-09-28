# AreaPulse Android

Modern Android 12–16 prototype for map-based community incident and paranormal reports.

## Current build
- `minSdk 31` (Android 12)
- `targetSdk 36` / `compileSdk 36` (Android 16)
- Android Gradle Plugin 8.11.1
- Gradle 8.13
- Java 17
- Adaptive + round + Android 13 monochrome launcher icons
- Android 12+ splash screen
- HTTPS-only networking
- Secure local WebView origin (`https://appassets.androidplatform.net`)
- OpenStreetMap tiles with attribution
- Photon place search
- Optional foreground location only when the user taps the location button
- No background location, accessibility service, overlays, SMS, contacts, device admin, or notification listener

## Build in Android Studio
Open this repository in a recent stable Android Studio, allow Gradle sync, then run the `app` configuration.

## Build with GitHub Actions
The included `.github/workflows/android.yml` installs Android API 36 and Gradle 8.13, builds the debug APK, and uploads it as the `AreaPulse-debug-apk` artifact.

## Important
Community reports in this prototype are unverified. The included visible reports are explicitly demo data. A public multi-user version would need authentication, moderation, abuse controls, a backend, and clear emergency/safety messaging.
