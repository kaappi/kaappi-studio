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
./gradlew test                     # unit tests (CI runs this)
./gradlew :app:detekt              # static analysis

# iOS (Xcode project is generated — do not hand-edit project.pbxproj)
cd iosApp && xcodegen generate     # REQUIRED after adding/removing/moving files under iosApp/
xcodebuild build -project iosApp/KaappiStudio.xcodeproj -scheme KaappiStudio \
  -destination 'platform=iOS Simulator,name=iPhone 16' CODE_SIGNING_ALLOWED=NO
```

Versions are centralized in `gradle/libs.versions.toml` (version catalog). JDK 17+.

## Critical gotchas

- **`kaappi.wasm` is gitignored and must be fetched:** `bash scripts/fetch-wasm.sh`
  writes only `app/src/main/assets/webview/kaappi.wasm`, but the Android runtime
  (`SchemeRunner`) reads `app/src/main/assets/kaappi.wasm` (assets root), and the iOS
  WebView fetches `iosApp/KaappiStudio/Resources/webview/kaappi.wasm`. Copy the binary
  to all three paths or Android's Run button fails at runtime and iOS silently produces
  no output. iOS CI never fetches it — CI-built iOS artifacts cannot execute Scheme.
- **Two copies of the webview assets, kept in sync manually:**
  `app/src/main/assets/webview/` and `iosApp/KaappiStudio/Resources/webview/` both
  contain `index.html`, `bridge.js`, `editor.js`, `styles.css`,
  `codemirror-bundle.mjs`. Changes must be applied to both. **Exception:** `bridge.js`
  intentionally differs — the Android variant is editor-only; the iOS variant
  pre-compiles WASM and implements `runCode()`. See
  [docs/bridge-protocol.md](docs/bridge-protocol.md).
- **Example programs are duplicated:** Android reads
  `shared/src/commonMain/.../data/ExampleRepository.kt`; iOS reads
  `iosApp/KaappiStudio/Helpers/Examples.swift`. Any example change must land in both,
  with matching id/title/description/category/code.
- **Tests are vacuous:** no unit test sources exist yet on either platform;
  `./gradlew test` and the iOS test action pass without exercising code. Do not treat a
  green test run as validation — verify changes by building and running.
- **Never commit secrets or binaries:** `keystore.properties`, `*.jks`,
  `app/play-service-account.json`, and all `kaappi.wasm` paths are gitignored. Keep it
  that way.

## Layout map

| Path | Role |
|------|------|
| `shared/` | KMP module: domain models in `commonMain`, `expect`/`actual` `FileRepository`/`SettingsRepository` in `androidMain`/`iosMain` (user files live in `<filesDir>/schemes/` on Android, `Documents/schemes/` on iOS) |
| `app/` | Android app: Compose UI (`ui/screens/`), ViewModels, `bridge/KaappiBridge.kt`, `runtime/SchemeRunner.kt` (Chicory JVM WASM runtime) |
| `iosApp/` | SwiftUI app: `Bridge/SchemeWebView.swift`, ViewModels, Views, `Resources/webview/` assets |
| `docs/` | Contributor documentation |
| `.cursor/skills/` | Release automation playbooks (source of truth for release steps). Exposed to ZCode via the `.zcode/skills` symlink — edit the files under `.cursor/skills/`, never the symlink |
| `.github/workflows/` | Android CI (fetch wasm → assembleDebug → test) and iOS CI (xcodebuild build + test) |

## Execution model (why the platforms differ)

- **Android:** the Play button pulls code out of the editor via
  `window.kaappiAPI?.getCode()`, then `SchemeRunner` executes it natively with Chicory
  on `Dispatchers.IO`. WebView runs on a background thread — long programs are fine.
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

1. Android changes: `./gradlew assembleDebug` (and `:app:detekt`) pass.
2. iOS changes: the `xcodebuild build` command above passes, and `xcodegen generate`
   was run if files moved.
3. Shared asset or protocol changes: both platform copies updated and consistent.
4. Docs: if you changed behavior described in `docs/`, update the relevant page.
