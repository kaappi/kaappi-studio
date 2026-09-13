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
| Shared Kotlin framework for the iOS simulator | `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — what the Xcode script phase runs; useful on its own for a readable Gradle log |
| iOS build (CLI) | `xcodebuild build -project iosApp/KaappiStudio.xcodeproj -scheme KaappiStudio -destination 'platform=iOS Simulator,name=iPhone 17' CODE_SIGNING_ALLOWED=NO` — runs Gradle for `:shared` (needs a JDK); the simulator name depends on the installed Xcode; list available devices with `xcrun simctl list devices available` |

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

1. JDK 17 (Temurin) + Gradle setup, with `~/.konan` (the Kotlin/Native toolchain) cached
2. `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — compiles `shared/src/iosMain`
   with a readable log before Xcode's script phase runs the same task
3. `xcodebuild build` for an available iPhone simulator (`CODE_SIGNING_ALLOWED=NO`)
4. `xcodebuild test` (same destination)

Notes / gaps to be aware of:

- **iOS CI never fetches `kaappi.wasm`** (and it's gitignored), so CI-built iOS apps
  compile fine but cannot execute Scheme at runtime. Fetch it manually for local work.
- The iOS tests (`iosApp/KaappiStudioTests/`) run on the simulator: the
  `EditorViewModel` load queue, the Kotlin ↔ Swift bridge (`SharedFrameworkTests`),
  a save/list/read/delete round trip through the shared `FileRepository`
  (`FileBrowserViewModelTests`), and the bundled webview assets.

## Testing

Unit tests live in two places and run on the JVM (no emulator needed):

| Location | Contents | Task |
|----------|----------|------|
| `shared/src/commonTest/` | `ExampleRepository` integrity, `SchemeFile` serialization | `:shared:testAndroidHostTest` |
| `app/src/test/` | `KaappiBridge` message handling, `SchemeRunner` execution paths, `FileRepository` contract, `EditorViewModel` / `FileBrowserViewModel` state | `:app:testDebugUnitTest` (or `:app:test`) |

The iOS tests in `iosApp/KaappiStudioTests/` run on a simulator via the `KaappiStudio`
scheme's test action (`xcodebuild test`, see the build table); they cover the Swift view
models and the shared-framework bridge, not the SwiftUI views.

Conventions:

- `SchemeRunner` takes an injectable `ModuleCache` (built from a `moduleLoader` lambda);
  tests pass an in-memory WASM module instead of reading the gitignored `kaappi.wasm`
  asset.
- `EditorViewModel` tests use `kotlinx-coroutines-test` (`Dispatchers.setMain`) because
  `viewModelScope` needs a Main dispatcher on the JVM.
- `FileRepository` and `FileBrowserViewModel` operations are `suspend` functions (their
  I/O runs on `Dispatchers.IO`), so their tests run under `runBlocking` / `runTest`.
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

Examples live in **one place**,
`shared/src/commonMain/kotlin/com/kaappi/studio/data/ExampleRepository.kt`; both apps
read the list through `:shared` (Android directly, iOS through the framework). Categories
are fixed by `ExampleCategory`: Getting Started, Functions, Data Structures, Control
Flow, Advanced.

Keep the workload small: iOS runs Scheme inside the WebView's main thread, so huge
iteration counts or very long programs freeze the UI (see
[architecture.md](architecture.md)). `ExampleRepositoryTest` enforces a per-example
code-size budget.

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

A CI check (in the Android workflow, so it runs on Swift-only changes too) compares
the two copies byte-for-byte on every push and pull request — run it locally with:

```bash
bash scripts/check-webview-assets.sh
```

Every file in the Android copy must exist in the iOS copy and be identical, so a new
shared asset forgotten on iOS fails the check as well. `bridge.js` is excluded (the
two variants differ by design), and iOS-only files are allowed.

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
when changing signatures. The iOS app calls the Kotlin API from Swift
(`FileBrowserViewModel.swift`, `SettingsViewModel.swift`), so:

- any exception a Swift caller must be able to catch has to be listed in `@Throws` on
  the `expect` declaration and both actuals (`CancellationException` included on
  `suspend` functions) — an unlisted Kotlin exception crashes the iOS app;
- build the framework after a change (`./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`
  or just build in Xcode) and fix the Swift call sites the header change breaks.

`shared/src/iosMain` is compiled by every iOS build and by iOS CI.

### Change the Android theme

- Native colors: `app/src/main/java/com/kaappi/studio/ui/theme/`
- Editor colors: `styles.css` (both webview dirs) + highlight palettes in `editor.js`
- Theme mode persistence: `shared/src/androidMain/.../SettingsRepository.android.kt`
  (the iOS counterpart is `shared/src/iosMain/.../SettingsRepository.ios.kt`)

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
