---
name: android-release
description: Build and upload an Android release based on an existing git tag. Runs tests, bumps version to match the tag, builds release AAB and APK, commits the version bump, and uploads the AAB to Google Play Store internal testing. Use when the user asks to make an Android release, build for Play Store, or upload to Google Play.
---

# Android Release

> **For normal dual-platform releases, use `/release` instead.** This skill is for Android-only hotfixes or when you need to rebuild Android independently.

## Prerequisites

- An existing version tag (e.g., `v1.0.0`)
- Signing keystore configured via `keystore.properties` at the project root
- Google Play service account key at `app/play-service-account.json`
- `kaappi.wasm` binary in `app/src/main/assets/` (run `bash scripts/fetch-wasm.sh`)

## Workflow

### Step 0: Determine the target tag

```bash
git describe --tags --abbrev=0
```

Confirm the tag with the user before proceeding.

### Step 1: Run tests

```bash
./gradlew testDebugUnitTest
```

**If any test fails, stop the release and fix the failures first.**

### Step 2: Bump the version

Read the current `versionCode` and `versionName` in `app/build.gradle.kts`.

- Set `versionName` to match the tag (e.g., `"1.1.0"`)
- Increment `versionCode` by 1

Also update the version string in `app/src/main/java/com/kaappi/studio/ui/screens/SettingsScreen.kt`.

### Step 3: Build release artifacts

```bash
./gradlew assembleRelease bundleRelease
```

### Step 4: Verify outputs

```bash
ls -lh app/build/outputs/apk/release/app-release*.apk \
       app/build/outputs/bundle/release/app-release.aab
```

### Step 5: Commit version bump

```bash
git add app/build.gradle.kts app/src/main/java/com/kaappi/studio/ui/screens/SettingsScreen.kt
git commit -m "Android: bump version to <versionName> (versionCode <versionCode>)"
git push
```

### Step 6: Upload to Google Play Store

```bash
ls app/play-service-account.json
```

If the file **exists**:

```bash
./gradlew publishReleaseBundle
```

Use `block_until_ms: 120000`.

### Step 7: Report to user

Provide the tag, version, AAB path/size, and Play Store upload status.
