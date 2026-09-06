# Development Guide

Day-to-day workflows for contributors: building, testing, linting, CI, and common tasks.

## Build commands

| Task | Command |
|------|---------|
| Android debug APK | `./gradlew assembleDebug` |
| Android install on device | `./gradlew installDebug` |
| Android release AAB + APK | `./gradlew assembleRelease bundleRelease` (needs signing — see [Release](release.md)) |
| Android unit tests | `./gradlew test` (CI runs this) |
| Detekt static analysis (`:app`) | `./gradlew :app:detekt` |
| Shared module coverage (Kover) | `./gradlew :shared:koverHtmlReport` |
| Regenerate Xcode project | `cd iosApp && xcodegen generate` |
| iOS build (CLI) | `xcodebuild build -project iosApp/KaappiStudio.xcodeproj -scheme KaappiStudio -destination 'platform=iOS Simulator,name=iPhone 16' CODE_SIGNING_ALLOWED=NO` |

Gradle is configured with the configuration cache, parallel execution, and build caching
(`gradle.properties`) — most incremental builds are fast.

Version management: all dependency versions live in
[`gradle/libs.versions.toml`](../gradle/libs.versions.toml) (Gradle version catalog).
SDK levels: `compileSdk` 36, `minSdk` 26, `targetSdk` 35.

## Continuous integration

GitHub Actions runs two workflows on every push/PR to `main`:

### [Android CI](../.github/workflows/android.yml) (`ubuntu-latest`)

1. JDK 17 (Temurin) + Gradle setup
2. `bash scripts/fetch-wasm.sh`
3. `./gradlew assembleDebug`
4. `./gradlew test`
5. Uploads the debug APK as an artifact

### [iOS CI](../.github/workflows/ios.yml) (`macos-latest`)

1. `xcodebuild build` for the iPhone 16 simulator (`CODE_SIGNING_ALLOWED=NO`)
2. `xcodebuild test` (same destination)

Notes / gaps to be aware of:

- **iOS CI never fetches `kaappi.wasm`** (and it's gitignored), so CI-built iOS apps
  compile fine but cannot execute Scheme at runtime. Fetch it manually for local work.
- Neither workflow runs Detekt.
- There are currently **no unit test sources** on either platform; `./gradlew test` and
  the iOS test action pass vacuously. Test dependencies (JUnit 4, Compose UI test,
  kotlinx-coroutines-test via commonTest) are already wired up — new tests just work.
- Kover is applied to `:shared`; Detekt is applied to `:app` with the default rule set
  (no custom config file).

## Common contributor tasks

### Add or edit an example program

Examples exist in **two places** and must be kept in sync:

1. `shared/src/commonMain/kotlin/com/kaappi/studio/data/ExampleRepository.kt` (Android)
2. `iosApp/KaappiStudio/Helpers/Examples.swift` (iOS)

Use the same `id`, `title`, `description`, category, and code in both. Categories are
fixed: Getting Started, Functions, Data Structures, Control Flow, Advanced. After adding
an iOS file (if new), regenerate the Xcode project.

### Update the Scheme engine (`kaappi.wasm`)

```bash
bash scripts/fetch-wasm.sh <version>
cp app/src/main/assets/webview/kaappi.wasm app/src/main/assets/kaappi.wasm
cp app/src/main/assets/webview/kaappi.wasm iosApp/KaappiStudio/Resources/webview/kaappi.wasm
```

(The copies are needed because of the [placement quirk](troubleshooting.md#missing-or-misplaced-wasm-binary);
all three paths are gitignored.)

### Change the editor (keymap, highlighting, theme palettes)

Edit `editor.js` / `styles.css` in **both** `app/src/main/assets/webview/` and
`iosApp/KaappiStudio/Resources/webview/`. `codemirror-bundle.mjs` is a prebuilt ES
module — regenerate it only if you need a different CodeMirror version, and copy it to
both locations.

### Change native ↔ WebView messaging

Update, in lockstep:

- `bridge.js` (both variants — Android and iOS differ, see
  [Architecture](architecture.md#the-webview-layer))
- `app/src/main/java/com/kaappi/studio/bridge/KaappiBridge.kt`
- `iosApp/KaappiStudio/Bridge/SchemeWebView.swift`

The protocol is documented in [Bridge Protocol](bridge-protocol.md).

### Add files to the iOS app

Any file added/moved/removed under `iosApp/` requires:

```bash
cd iosApp && xcodegen generate
```

then commit the regenerated `KaappiStudio.xcodeproj/project.pbxproj`.

### Change data storage

Repositories are `expect`/`actual`: interfaces in
`shared/src/commonMain/kotlin/com/kaappi/studio/data/`, implementations in
`shared/src/androidMain/` and `shared/src/iosMain/`. Keep the three source sets in sync
when changing signatures. Note the `:shared` module also has a plain `jvm()` target for
fast JVM-side testing.

### Change the Android theme

- Native colors: `app/src/main/java/com/kaappi/studio/ui/theme/`
- Editor colors: `styles.css` (both webview dirs) + highlight palettes in `editor.js`
- Theme mode persistence: `shared/src/androidMain/.../SettingsRepository.android.kt`

## Code style

- Kotlin: official style (`kotlin.code.style=official`), enforced by Detekt on `:app`
  (default rule set).
- Swift 6, SwiftUI conventions, `@MainActor` view models.
- Commit messages: short imperative subject lines (see `git log` for examples;
  release bumps use a `Release:` prefix — see [Release](release.md)).

## Secrets & signing (local)

These files are gitignored and never committed:

| File | Purpose |
|------|---------|
| `keystore.properties` | Android release signing config (loaded by `app/build.gradle.kts`) |
| `app/upload-keystore.jks` | The keystore itself |
| `app/play-service-account.json` | Google Play upload credentials (`publishReleaseBundle`) |
| `kaappi.wasm` (three paths) | Scheme engine binary — see [Getting Started](getting-started.md#wasm-binary-required) |

Without `keystore.properties`, release builds fall back to debug signing — debug
development needs none of these files.
