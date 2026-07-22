---
name: release
description: Unified release for both Android and iOS from a single version tag. Runs quality gates, bumps both platform versions in one commit, tags, builds both platforms, uploads Android to Play Store, and opens iOS archive for App Store upload. Use when the user asks to release, ship, cut a release, or make a new version.
---

# Release (Android + iOS)

Build and release both platforms from a single version tag. Bumps Android and iOS versions atomically in one commit, then builds and distributes both.

## Prerequisites

- `gh` CLI authenticated (`gh auth status`)
- Working tree is clean, on `main`, up to date with origin
- Signing keystore via `keystore.properties` at project root (Android)
- Google Play service account key at `app/play-service-account.json` (Android upload)
- Xcode installed with code signing configured (iOS)
- `kaappi.wasm` binary in `app/src/main/assets/` and `iosApp/KaappiStudio/Resources/webview/` (run `bash scripts/fetch-wasm.sh` if missing)

## Workflow

### Step 1: Verify clean state

```bash
git status
git branch --show-current
gh auth status
```

Confirm the branch is `main` and there are no uncommitted changes. If the working tree is dirty, stop and ask the user to commit or stash first.

### Step 2: Determine the next version

Read the latest existing tag:

```bash
git describe --tags --abbrev=0
```

The project uses **semantic versioning** (`major.minor.patch`) with a `v` prefix (e.g., `v1.0.0`).

If the user did not specify a version or release type, ask which type of release to create:

| Release type | What changes | Example |
|--------------|-------------|---------|
| **Major** | Bump major, reset minor and patch to 0 | `1.0.0` -> `2.0.0` |
| **Feature** | Bump minor, reset patch to 0 | `1.0.0` -> `1.1.0` |
| **Bugfix** | Bump patch | `1.0.0` -> `1.0.1` |

Ask the user to confirm the new version before proceeding.

### Step 3: Run quality gates

All gates must pass before any version bumping or tagging.

#### 3a. Run Android unit tests

```bash
./gradlew testDebugUnitTest
```

#### 3b. Fetch WASM binary

```bash
bash scripts/fetch-wasm.sh
```

### Step 4: Bump both platform versions in one commit

#### 4a. Android version bump

Read the current `versionCode` and `versionName` in `app/build.gradle.kts`:

```kotlin
defaultConfig {
    versionCode = <current>
    versionName = "<major>.<minor>.<patch>"
}
```

- Set `versionName` to the new version (e.g., `"1.1.0"`)
- Increment `versionCode` by 1

#### 4b. iOS version bump

Read the current `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` in both `iosApp/project.yml` and `iosApp/KaappiStudio.xcodeproj/project.pbxproj`.

- Set `MARKETING_VERSION` to the new version (e.g., `1.1.0`)
- Increment `CURRENT_PROJECT_VERSION` by 1

Update all occurrences in the pbxproj (Debug and Release for each target).

#### 4c. Update Settings screen version strings

Update the version strings in:
- `app/src/main/java/com/kaappi/studio/ui/screens/SettingsScreen.kt` — `"Kaappi Studio v<version>"`
- `iosApp/KaappiStudio/Views/SettingsView.swift` — `LabeledContent("Version", value: "<version>")`

#### 4d. Commit

Stage all files and commit:

```bash
git add app/build.gradle.kts iosApp/project.yml iosApp/KaappiStudio.xcodeproj/project.pbxproj \
       app/src/main/java/com/kaappi/studio/ui/screens/SettingsScreen.kt \
       iosApp/KaappiStudio/Views/SettingsView.swift
git commit -m "Release: bump version to <version> (Android versionCode <N>, iOS build <M>)"
```

Do **not** push yet — the tag is created next.

### Step 5: Tag and push

Create an annotated tag on the version-bump commit and push everything:

```bash
git tag -a v<version> -m "v<version>"
git push origin main v<version>
```

### Step 6: Create GitHub release

```bash
gh release create v<version> \
  --title "v<version>" \
  --notes "$(cat <<'EOF'
## What's New

<customer-facing summary>

EOF
)"
```

### Step 7: Build Android

```bash
./gradlew assembleRelease bundleRelease
```

Verify outputs:

```bash
ls -lh app/build/outputs/apk/release/app-release*.apk \
       app/build/outputs/bundle/release/app-release.aab
```

### Step 8: Upload Android to Play Store

Check for the service account key:

```bash
ls app/play-service-account.json
```

If the file **exists**, upload:

```bash
./gradlew publishReleaseBundle
```

If the file is **missing**, warn the user and skip.

### Step 9: Build iOS

#### 9a. Regenerate Xcode project

```bash
cd iosApp && xcodegen generate && cd ..
```

#### 9b. Build the iOS archive

```bash
xcodebuild archive \
  -project iosApp/KaappiStudio.xcodeproj \
  -scheme KaappiStudio \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath build/KaappiStudio.xcarchive
```

Do NOT pass `CODE_SIGNING_ALLOWED=NO` — the archive must be signed.

### Step 10: Open iOS archive

```bash
open build/KaappiStudio.xcarchive
```

This opens Xcode Organizer for App Store upload.

### Step 11: Report to user

Provide:
- The new tag name (e.g., `v1.1.0`)
- The GitHub release URL
- Android: `versionName` / `versionCode`, AAB path and size, Play Store upload status
- iOS: `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION`, archive path
- Next steps: test in internal/TestFlight, then promote
