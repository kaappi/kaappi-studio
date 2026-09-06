# Release Process

How Kaappi Studio versions, builds, and ships to Google Play and the App Store.

## Versioning

Semantic versioning with a `v` prefix (`v1.0.0`): **major** for breaking changes,
**minor** for features, **patch** for fixes. Both platforms always ship the same
version from a single tag.

## Everything a release touches

A version bump means updating **five files** (plus the tag):

| File | What to change |
|------|----------------|
| `app/build.gradle.kts` | `versionName` → `"X.Y.Z"`, `versionCode` → +1 |
| `iosApp/project.yml` | `MARKETING_VERSION` → `X.Y.Z`, `CURRENT_PROJECT_VERSION` → +1 |
| `iosApp/KaappiStudio.xcodeproj/project.pbxproj` | Same two values, all occurrences (Debug + Release) — easiest to update `project.yml` then run `cd iosApp && xcodegen generate` |
| `app/.../ui/screens/SettingsScreen.kt` | The `"Kaappi Studio vX.Y.Z"` string |
| `iosApp/KaappiStudio/Views/SettingsView.swift` | The `LabeledContent("Version", value: "X.Y.Z")` string |

## Prerequisites

- Clean working tree on `main`, up to date with `origin`
- `gh` CLI authenticated (`gh auth status`)
- Android signing: `keystore.properties` at the repo root + `app/upload-keystore.jks`
- Play upload: `app/play-service-account.json`
- iOS signing: Xcode configured with development team `9QBV46NATP` (set in `project.yml`)
- `kaappi.wasm` present in both
  `app/src/main/assets/` (+`webview/`) and `iosApp/KaappiStudio/Resources/webview/`
  (`bash scripts/fetch-wasm.sh` and copy — see [Getting Started](getting-started.md#wasm-binary-required))

All signing/secret files are gitignored; a machine without them can still build and
test but cannot sign for distribution or upload to Play.

## The flow

1. **Quality gates** — `./gradlew testDebugUnitTest`, then `bash scripts/fetch-wasm.sh`.
2. **Version bump commit** — update the five files above, commit on `main`
   (message convention: `Release: bump version to X.Y.Z (Android versionCode N, iOS build M)`).
   Do not push yet.
3. **Tag and push** — `git tag -a vX.Y.Z -m "vX.Y.Z" && git push origin main vX.Y.Z`.
4. **GitHub release** — `gh release create vX.Y.Z --title "vX.Y.Z" --notes "…"`
   with customer-facing notes.
5. **Android build** — `./gradlew assembleRelease bundleRelease`; verify
   `app/build/outputs/apk/release/` and `app/build/outputs/bundle/release/app-release.aab`.
6. **Play upload** — `./gradlew publishReleaseBundle` (requires the service account key;
   lands in internal testing first).
7. **iOS build** — regenerate the project (`cd iosApp && xcodegen generate`), then:

   ```bash
   xcodebuild archive \
     -project iosApp/KaappiStudio.xcodeproj \
     -scheme KaappiStudio \
     -configuration Release \
     -destination 'generic/platform=iOS' \
     -archivePath build/KaappiStudio.xcarchive
   ```

   Do **not** pass `CODE_SIGNING_ALLOWED=NO` — the archive must be signed.
8. **App Store upload** — `open build/KaappiStudio.xcarchive` and use Xcode Organizer
   (TestFlight / App Store submission).

## Automation

The repo ships AI-agent skills under [`.cursor/skills/`](../.cursor/skills/) that
script this flow step by step. They are the source of truth for the exact commands
(and are exposed to ZCode through the committed `.zcode/skills` symlink — the files
under `.cursor/skills/` are canonical):

| Skill | Scope |
|-------|-------|
| [`release`](../.cursor/skills/release/SKILL.md) | Full dual-platform release from one tag (the normal path) |
| [`github-release`](../.cursor/skills/github-release/SKILL.md) | Tag + GitHub release only |
| [`android-release`](../.cursor/skills/android-release/SKILL.md) | Android-only hotfix rebuild + Play upload |
| [`ios-release`](../.cursor/skills/ios-release/SKILL.md) | iOS-only hotfix rebuild + archive |

If you change version-bump locations or signing setup, update these skills in the same
change so the automation keeps working.
