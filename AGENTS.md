# AGENTS.md

Instructions for AI coding agents working in this repository.

Kaappi Studio is a Kotlin Multiplatform (KMP) app for learning Scheme on iOS and
Android. A native shell (Compose on Android, SwiftUI on iOS) hosts a WebView running a
CodeMirror editor; the same `kaappi.wasm` Scheme interpreter binary runs on both
platforms. Full documentation lives in [`docs/`](docs/README.md) — read
[architecture.md](docs/architecture.md) before making structural changes.

## Commands

```bash
# Android (from repo root)
./gradlew assembleDebug            # build debug APK
./gradlew installDebug             # install on connected device/emulator
./gradlew test                     # app unit tests
./gradlew :shared:testAndroidHostTest   # shared commonTest suite (JVM, no emulator)
./gradlew :app:detekt              # static analysis (baseline in app/detekt-baseline.xml)
./gradlew :shared:koverHtmlReport :app:koverHtmlReport   # coverage reports

# iOS (Xcode project is generated — do not hand-edit project.pbxproj)
cd iosApp && xcodegen generate     # REQUIRED after adding/removing/moving files under iosApp/
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64   # the shared Kotlin framework alone (readable log)
xcodebuild build -project iosApp/KaappiStudio.xcodeproj -scheme KaappiStudio \
  -destination 'platform=iOS Simulator,name=iPhone 17' CODE_SIGNING_ALLOWED=NO   # runs Gradle too — needs a JDK
```

The destination simulator name depends on the installed Xcode (e.g. `iPhone 17`
ships with Xcode 26); list what you have with `xcrun simctl list devices available`.

Versions are centralized in `gradle/libs.versions.toml` (version catalog). JDK 17+.

## Critical gotchas

- **`kaappi.wasm` is gitignored and must be fetched:** `bash scripts/fetch-wasm.sh`
  writes the binary to the two paths the platforms read: `app/src/main/assets/kaappi.wasm`
  (Android assets root, read by `SchemeRunner`) and
  `iosApp/KaappiStudio/Resources/webview/kaappi.wasm` (fetched by the iOS `bridge.js`).
  The Android webview assets do not need it — Android executes via Chicory from the
  assets root. Without it, Android's Run button fails at runtime and iOS produces no
  output. iOS CI never fetches it — CI-built iOS artifacts cannot execute Scheme.
- **Two copies of the webview assets, kept in sync:**
  `app/src/main/assets/webview/` and `iosApp/KaappiStudio/Resources/webview/` both
  contain `index.html`, `bridge.js`, `editor.js`, `styles.css`,
  `codemirror-bundle.mjs`. Changes must be applied to both. CI enforces this on every
  push (`scripts/check-webview-assets.sh`, run from the Android workflow; see
  [docs/development.md](docs/development.md)) — every file in the Android copy must
  be byte-identical on iOS. **Exception:** `bridge.js` intentionally differs — the
  Android variant is editor-only; the iOS variant pre-compiles WASM and implements
  `runCode()`. See [docs/bridge-protocol.md](docs/bridge-protocol.md).
- **iOS links `:shared` as a Kotlin/Native static framework:** a pre-build script
  phase in `iosApp/project.yml` runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`,
  so every iOS build (local, CI, `xcodebuild archive`) needs a JDK and compiles
  `shared/src/iosMain`. Example programs, file storage and settings live only in
  Kotlin; the Swift view models wrap `FileRepository`/`SettingsRepository`. Any Kotlin
  exception a Swift caller must catch has to be listed in `@Throws` (with
  `CancellationException` on `suspend` functions) — an unlisted exception crossing the
  bridge terminates the iOS app. `ExampleRepositoryTest` caps per-example code size —
  keep example workloads small enough to run on iOS's main-thread WASM.
- **Test layout:** unit tests run on the JVM — `shared/src/commonTest/` (repository
  integrity, serialization; runs via `:shared:testAndroidHostTest`) and
  `app/src/test/` (bridge, runner, viewmodels; runs via `./gradlew test`). iOS tests
  (`iosApp/KaappiStudioTests/`: view models, the Kotlin ↔ Swift bridge, a file
  round trip through the shared repository) run on the simulator via `xcodebuild test`.
  Keep new detekt violations out of `app/detekt-baseline.xml` — extend it only for
  existing-code refactors, never to make new code pass. Compose UI (`ui/screens`,
  `ui/theme`) is untested and needs instrumentation tests.
- **Never commit secrets or binaries:** `keystore.properties`, `*.jks`,
  `app/play-service-account.json`, and all `kaappi.wasm` paths are gitignored. Keep it
  that way.

## Upstream interpreter bugs are reported at kaappi/kaappi

**Mandatory:** `kaappi.wasm` is the Scheme interpreter built from the
[kaappi/kaappi](https://github.com/kaappi/kaappi) repo; this studio only embeds it. If a
bug fix here reveals the defect is actually in the interpreter — wrong evaluation
results, a crash inside the WASM, missing/incorrect R7RS behavior — do **not** fix or
paper over it in this repo. File an issue at kaappi/kaappi with a minimal Scheme
reproduction and the expected vs. actual output, link it from the studio issue/PR, and
keep any studio-side change minimal (a workaround at most, with a comment pointing at
the upstream issue). Bugs in the bridge, UI, assets, or build are studio bugs and stay
here; bugs in how Scheme code evaluates are upstream.

## Layout map

| Path | Role |
|------|------|
| `shared/` | KMP module: domain models in `commonMain`, `expect`/`actual` `FileRepository`/`SettingsRepository` in `androidMain`/`iosMain` (user files live in `<filesDir>/schemes/` on Android, `Documents/schemes/` on iOS) |
| `app/` | Android app: Compose UI (`ui/screens/`), ViewModels, `bridge/KaappiBridge.kt`, `runtime/SchemeRunner.kt` (Chicory JVM WASM runtime) |
| `iosApp/` | SwiftUI app: `Bridge/SchemeWebView.swift`, ViewModels (wrapping the shared Kotlin repositories), Views, `Helpers/SharedModels.swift` (Swift conveniences for the Kotlin models), `Resources/webview/` assets |
| `docs/` | Contributor documentation |
| `.cursor/skills/` | Release automation playbooks (source of truth for release steps) and the `pr-groups` issue-batching skill. Exposed to ZCode via the `.zcode/skills` symlink — edit the files under `.cursor/skills/`, never the symlink |
| `.github/workflows/` | Android CI (fetch wasm → assembleDebug → test) and iOS CI (JDK → shared framework → xcodebuild build + test) |

## Execution model (why the platforms differ)

- **Android:** the Play button pulls code out of the editor via
  `window.kaappiAPI?.getCode()`, then `SchemeRunner` executes it natively with Chicory
  on a dedicated per-run thread (not the shared `Dispatchers.IO` pool). Long programs
  are fine; Stop only abandons a run, it cannot kill it (see `docs/architecture.md`, #24).
- **iOS:** the Play button calls `window.kaappiAPI?.runCode()` and the WASM runs
  **inside the WebView on its main thread** (WASI shim, no timeout). Long programs
  freeze the UI; keep iOS test programs short. `worker.js`/`runner.js` exist but are
  not wired in.
- JS → native messages are JSON `{event, ...}`; native → JS calls go through
  `window.kaappiAPI`. Android currently only handles `ready`; iOS handles
  `ready`/`runStart`/`runComplete`/`runError`. Keep both sides of the protocol in
  lockstep.

## Conventions

- Kotlin: official style, enforced by Detekt on `:app` (default rule set).
- Swift 6 / SwiftUI; `@MainActor` view models.
- Commit messages: short imperative subject (e.g. "Add developer documentation in
  docs/"). Version-bump commits use the `Release:` prefix.
- Do not bump versions or touch release files (`app/build.gradle.kts` version fields,
  `project.yml`, Settings version strings) outside a release — follow
  [.cursor/skills/release/SKILL.md](.cursor/skills/release/SKILL.md).

## Before you finish a change

1. Android changes: `./gradlew assembleDebug test :shared:testAndroidHostTest :app:detekt` pass.
2. iOS changes: the `xcodebuild build` command above passes, and `xcodegen generate`
   was run if files moved. Changes to the `shared` API surface (`expect` signatures,
   `@Throws`, domain models) count as iOS changes — the Swift call sites must still compile.
3. Shared asset or protocol changes: both platform copies updated and consistent.
4. Behavior changes: extend/adjust the relevant unit tests (see Test layout above).
5. Docs: if you changed behavior described in `docs/`, update the relevant page.
