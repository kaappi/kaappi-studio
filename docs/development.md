# Development Guide

Day-to-day workflows for contributors: building, testing, linting, CI, and common tasks.

## Build commands

| Task | Command |
|------|---------|
| Android debug APK | `./gradlew assembleDebug` |
| Android install on device | `./gradlew installDebug` |
| Android unit tests | `./gradlew test` |
| Shared module tests (commonTest on Android host) | `./gradlew :shared:testAndroidHostTest` |
| Detekt static analysis (`:app`) | `./gradlew :app:detekt` |
| Coverage reports (Kover HTML) | `./gradlew :shared:koverHtmlReport :app:koverHtmlReport` |
| Android release AAB + APK | `./gradlew assembleRelease bundleRelease` (needs signing — see [Release](release.md)) |
| Regenerate Xcode project | `cd iosApp && xcodegen generate` |
| iOS build (CLI) | `xcodebuild build -project iosApp/KaappiStudio.xcodeproj -scheme KaappiStudio -destination 'platform=iOS Simulator,name=iPhone 17' CODE_SIGNING_ALLOWED=NO` — the simulator name depends on the installed Xcode; list available devices with `xcrun simctl list devices available` |

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
4. `./gradlew test :shared:testAndroidHostTest :app:detekt`
5. Coverage reports via `:shared:koverHtmlReport :app:koverHtmlReport`, uploaded as an
   artifact alongside the debug APK

### [iOS CI](../.github/workflows/ios.yml) (`macos-latest`)

1. `xcodebuild build` for an available iPhone simulator (`CODE_SIGNING_ALLOWED=NO`)
2. `xcodebuild test` (same destination)

Notes / gaps to be aware of:

- **iOS CI never fetches `kaappi.wasm`** (and it's gitignored), so CI-built iOS apps
  compile fine but cannot execute Scheme at runtime. Fetch it manually for local work.
- The iOS app itself has no test sources yet (the `KaappiStudioTests` target exists but
  is empty).

## Testing

Unit tests live in two places and run on the JVM (no emulator needed):

| Location | Contents | Task |
|----------|----------|------|
| `shared/src/commonTest/` | `ExampleRepository` integrity, `SchemeFile` serialization | `:shared:testAndroidHostTest` |
| `app/src/test/` | `KaappiBridge` message handling, `SchemeRunner` execution paths, `EditorViewModel` state | `:app:testDebugUnitTest` (or `:app:test`) |

Conventions:

- `SchemeRunner` takes an injectable `moduleLoader` lambda; tests pass an in-memory WASM
  module instead of reading the gitignored `kaappi.wasm` asset.
- `EditorViewModel` tests use `kotlinx-coroutines-test` (`Dispatchers.setMain`) because
  `viewModelScope` needs a Main dispatcher on the JVM.
- Android framework interactions in tests use Mockito (`contextWithCacheDir` helper).

The Compose UI (`ui/screens`, `ui/theme`) has no unit tests — it needs instrumentation
tests (`androidTest`) or Robolectric, which the project doesn't use yet.

## Coverage

Kover measures both modules; CI uploads the HTML reports as artifacts:

```bash
./gradlew :shared:koverHtmlReport :app:koverHtmlReport
# → shared/build/reports/kover/html/index.html
# → app/build/reports/kover/html/index.html
```

The logic layers (bridge, runner, viewmodels, shared domain/data) are covered; the
Compose UI is not, so module-level percentages are dominated by UI code — read the
per-package breakdown instead.

Detekt on `:app` runs with the default rule set; the pre-existing violations are frozen
in `app/detekt-baseline.xml` (generated with `:app:detektBaseline`). New code must be
baseline-clean — don't regenerate the baseline to make new violations pass.

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
```

The script writes the binary to all three gitignored paths the platforms read
(Android assets root, Android webview assets, iOS webview resources).

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
