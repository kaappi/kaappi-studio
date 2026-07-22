---
name: ios-release
description: Build an iOS release based on an existing git tag. Bumps version to match the tag, regenerates the Xcode project, and builds an iOS archive for App Store upload. Use when the user asks to make an iOS release, build for App Store, or create an iOS archive.
---

# iOS Release

> **For normal dual-platform releases, use `/release` instead.** This skill is for iOS-only hotfixes or when you need to rebuild iOS independently.

## Prerequisites

- An existing version tag (e.g., `v1.0.0`)
- Xcode installed with the `KaappiStudio` scheme
- Code signing configured (Development Team `9QBV46NATP` set in project.yml)
- `xcodegen` installed (`brew install xcodegen`)
- `kaappi.wasm` in `iosApp/KaappiStudio/Resources/webview/`

## Workflow

### Step 0: Determine the target tag

```bash
git describe --tags --abbrev=0
```

Confirm the tag with the user before proceeding.

### Step 1: Bump the version

Update `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` in `iosApp/project.yml`.

Set `MARKETING_VERSION` to match the tag (e.g., `1.1.0`).
Increment `CURRENT_PROJECT_VERSION` by 1.

Also update the version string in `iosApp/KaappiStudio/Views/SettingsView.swift`.

### Step 2: Regenerate Xcode project

```bash
cd iosApp && xcodegen generate && cd ..
```

### Step 3: Build the iOS archive

```bash
xcodebuild archive \
  -project iosApp/KaappiStudio.xcodeproj \
  -scheme KaappiStudio \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath build/KaappiStudio.xcarchive
```

Use `block_until_ms: 300000`.

**Important notes:**
- Do NOT pass `CODE_SIGNING_ALLOWED=NO` — the archive must be signed for App Store upload.
- The first build may trigger a macOS Keychain dialog for signing key access.

### Step 4: Commit version bump

```bash
git add iosApp/project.yml iosApp/KaappiStudio.xcodeproj/project.pbxproj \
       iosApp/KaappiStudio/Views/SettingsView.swift
git commit -m "iOS: bump version to <version> (build <buildNumber>)"
git push
```

### Step 5: Open in Xcode Organizer

```bash
open build/KaappiStudio.xcarchive
```

This opens Xcode Organizer where the user can click **"Distribute App"** > **"App Store Connect"** > **"Upload"**.

### Step 6: Report to user

Provide the tag, version, archive path, and instruction to upload via Xcode Organizer.
