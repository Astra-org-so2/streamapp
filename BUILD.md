# StreamApp — Build and Verification Guide

## 1. Build Requirements
- **JDK:** OpenJDK 17 or higher
- **Android SDK:** API 35 (Platforms: `android-35`, Build-Tools: `35.0.0`)
- **Gradle:** Gradle 8.14 (managed via Gradle Wrapper)

## 2. Command Line Tasks

### Build Debug APK
```bash
./gradlew assembleDebug
```
Output APK: `app/build/outputs/apk/debug/app-debug.apk`

### Build Minified Release APK
```bash
./gradlew assembleRelease
```
Output APK: `app/build/outputs/apk/release/app-release.apk`

### Run Unit Tests
```bash
./gradlew test
```
Test Reports: `app/build/reports/tests/testDebugUnitTest/index.html`
