# Architecture

Kaappi Studio is a Kotlin Multiplatform app with a **native shell + WebView editor** design.
The native layer owns navigation, files, settings, and theming; a WebView hosts the
CodeMirror 6 code editor. Scheme execution takes a different path on each platform.

## Big picture

```
┌────────────────────────── Android ──────────────────────────┐
│  Jetpack Compose UI (MainActivity, screens, ViewModels)     │
│        │                                                    │
│        ├── WebView (editor): index.html + bridge.js +       │
│        │   editor.js (CodeMirror 6)                         │
│        │     ▲ JSON messages via @JavascriptInterface       │
│        │                                                    │
│        └── IsolatedSchemeRunner ──(Messenger)──┐            │
│                                                │            │
│  :shared (KMP): examples, File/Settings repos  │            │
├──────────────────── :runner process ───────────┼────────────┤
│  SchemeRunnerService → SchemeRunner: Chicory   ◄┘           │
│  executes kaappi.wasm natively; Stop kills the process      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────── iOS ─────────────────────────────┐
│  SwiftUI UI (ContentView, views, ObservableObject VMs)      │
│        │                                                    │
│        └── WKWebView (editor + runner): index.html +        │
│            bridge.js + editor.js (CodeMirror 6)             │
│              ▲ JSON messages via WKScriptMessageHandler     │
│              Scheme runs inside the WebView:                │
│              browser WebAssembly + wasi-shim-bundle.mjs     │
│                                                             │
│  :shared (KMP) static framework: examples, File/Settings    │
│  repositories — built by Gradle from an Xcode script phase  │
└─────────────────────────────────────────────────────────────┘
```

The key asymmetry: **Android runs Scheme natively** through
[Chicory](https://github.com/dylibso/chicory) (a pure-JVM WebAssembly interpreter), while
**iOS runs Scheme inside the WebView** using the browser's WebAssembly engine plus the
bundled `wasi-shim-bundle.mjs` (a prebuilt build of
[`@bjorn3/browser_wasi_shim`](https://github.com/bjorn3/browser_wasi_shim)). Both paths
execute the same `kaappi.wasm` binary and feed it the program as `program.scm` over WASI.

## Modules

| Module | Technology | Responsibility |
|--------|-----------|----------------|
| `:shared` | Kotlin Multiplatform (Android + iOS targets) | Domain models, example programs and `expect`/`actual` repositories |
| `:app` | Jetpack Compose, Material 3, WebView, Chicory | Full Android app |
| `iosApp/` | SwiftUI, WebKit | Full iOS app (links `:shared` as a static framework built by a Gradle run-script phase) |

### `:shared` — Kotlin Multiplatform module

`shared/src/commonMain/kotlin/com/kaappi/studio/`

- **`domain/`** — data classes: `Example` + `ExampleCategory`, `SchemeFile`
  (kotlinx-serialization), `RunResult` (stdout/stderr/elapsedMs), `ReplEntry`, `ThemeMode`.
- **`data/`** — `ExampleRepository` (the single list of example programs, used by both
  apps) and `expect` classes implemented per platform:
  - `FileRepository` — user Scheme files (`*.scm`)
  - `SettingsRepository` — theme mode, font size, last opened file

  Both actuals obey the behavior contract documented in the `expect` KDoc:
  every `FileRepository` operation is a `suspend` function that does its disk
  I/O on `Dispatchers.IO` so the main thread never blocks (issue #12);
  `listFiles` returns names, paths and modification times without reading any
  file's contents (`SchemeFile` carries none — open with `readFile`);
  centrally sanitized file names ([A-Za-z0-9_\- ], via `SchemeFileNames`);
  atomic writes; `readFile` throwing on missing/unreadable files (never
  returning "") and decoding non-UTF-8 content lossily (U+FFFD); `renameFile`
  refusing to overwrite an existing destination; theme fallback to SYSTEM on
  unknown stored values; and font-size clamping to 10–24 (default 14).

Platform `actual` implementations:

| Concern | Android (`androidMain`) | iOS (`iosMain`) |
|---------|------------------------|-----------------|
| User files | `<filesDir>/schemes/*.scm` via `java.io.File` | `Documents/schemes/*.scm` via `NSFileManager` |
| Settings | `SharedPreferences` (`kaappi_studio_prefs`) | `NSUserDefaults` (standard) |

#### How iOS consumes `:shared`

`shared/build.gradle.kts` declares the three iOS targets (`iosArm64`,
`iosSimulatorArm64`, `iosX64`) and a **static** framework named `shared`. The
`KaappiStudio` Xcode target has a "Build shared Kotlin framework" pre-build script
phase (see `iosApp/project.yml`) that runs
`./gradlew :shared:embedAndSignAppleFrameworkForXcode`; the Kotlin Gradle plugin reads
Xcode's `CONFIGURATION`/`SDK_NAME`/`ARCHS` environment, links the matching framework
and copies it to `shared/build/xcode-frameworks/<config>/<sdk>/`, where
`FRAMEWORK_SEARCH_PATHS` points and `OTHER_LDFLAGS` links it. Static frameworks are
linked into the app binary, not embedded, so nothing is copied into the bundle and no
signing is involved. Every iOS build therefore needs a JDK, and iOS CI compiles
`shared/src/iosMain` on every push.

Swift sees the Kotlin API through the generated Objective-C header:
`ExampleRepository.shared.examples`, `FileRepository()`/`SettingsRepository()`, and the
Kotlin enums (`ThemeMode.entries`, `ExampleCategory.entries`). Two bridge rules matter:

- `suspend` functions become `async throws` in Swift, but **only exceptions listed in
  `@Throws` become thrown errors** — any other Kotlin exception crossing into Swift
  terminates the process. The `FileRepository` `expect` declarations (and
  `SchemeFileNames.sanitize`) carry the annotations; keep them when changing the API.
- `Example.description` is exported as `description_` (NSObject owns `description`),
  and `SchemeFile.lastModified` arrives as `Int64` epoch milliseconds.
  `iosApp/KaappiStudio/Helpers/SharedModels.swift` adds the `Identifiable`
  conformances and a `Date` accessor the SwiftUI views need.

Kover measures the module's coverage on the Android host.

### `:app` — Android

```
app/src/main/java/com/kaappi/studio/
├── MainActivity.kt          # navigation drawer, top bar, save dialog, run/save wiring
├── bridge/KaappiBridge.kt   # @JavascriptInterface receiver for WebView messages
├── runtime/
│   ├── SchemeExecutor.kt        # one run: run(code) + stop()
│   ├── IsolatedSchemeRunner.kt  # app-process client: binds the :runner service, kills it on Stop
│   ├── IsolatedRunSession.kt    # transport-free stop/pid/death ordering (unit tested)
│   ├── RunnerProtocol.kt        # Messenger message ids; results travel as files
│   ├── SchemeRunnerService.kt   # bound service in android:process=":runner"
│   └── SchemeRunner.kt          # Chicory WASI execution of kaappi.wasm (in-process engine)
├── ui/screens/              # Editor, Examples, FileBrowser, Settings screens
├── ui/theme/                # Material 3 theme ("Dark Roast" palette)
└── viewmodel/               # Editor / FileBrowser / Settings ViewModels (StateFlow)
```

Run flow (Android):

1. The Play button evaluates `window.kaappiAPI?.getCode()` in the WebView; the JS result
   (a JSON-encoded string) comes back through the `evaluateJavascript` callback.
2. `EditorViewModel.runCode()` builds a fresh `IsolatedSchemeRunner`, which binds
   `SchemeRunnerService` — declared with `android:process=":runner"`, so Android
   starts (or reuses) a second app process — and sends it the program over a
   `Messenger`. The service acknowledges with its pid and then runs the program
   through `SchemeRunner` on a dedicated single-thread executor (one daemon thread
   per run, never the shared `Dispatchers.IO` pool). While a run is in progress the
   Play button is replaced by a Stop button.
3. `SchemeRunner` writes the code to a unique per-run directory
   `cacheDir/kaappi-run/run-<uuid>/program.scm`, configures WASI with args
   `["kaappi", "program.scm"]`, instantiates the shared `kaappi.wasm` module in a
   fresh `Store`, and captures stdout/stderr into memory buffers.
4. The service writes stdout/stderr to `cacheDir/kaappi-run/result-<uuid>/` and
   replies with the paths (a Binder transaction is capped at ~1 MB; program output is
   not). The client reads and deletes them, and a `RunResult` flows back to the output
   pane, including elapsed milliseconds.

Details worth knowing:

- The ViewModels are created through `ViewModelProvider` factories, so they (and any
  run in progress) survive configuration changes.
- The editor WebView is created once per Activity lifetime and reused across drawer
  section switches; it is destroyed in `onDestroy()`. Editor content is pulled into
  `EditorViewModel` in `onPause` and re-injected into a recreated WebView through
  `setCodeBase64` (the `setEditorCode` path).
- Chicory throws when the WASI program calls `exit`; `SchemeRunner` treats
  `exit code: 0` messages as success and everything else as stderr output.
- The parsed WASM module is cached once per `:runner` process (shared by the
  per-run `SchemeRunner` instances); each run re-instantiates but does not re-parse.
  The process survives between runs as a cached process, so consecutive runs stay
  warm; a run after a Stop pays a cold start and a re-parse.
- Stop kills the program (issue #24). Chicory 1.7.5 has no interrupt or fuel
  hooks, so the WASM cannot be interrupted from inside a process; instead nothing
  but Scheme programs runs in `:runner`, and `EditorViewModel.stopRun()` cancels
  the run job, whose `finally` calls `IsolatedSchemeRunner.stop()`: it unbinds the
  service (so the system does not restart it) and `Process.killProcess`es the pid
  the service reported. The ordering rules live in `IsolatedRunSession` and are
  unit tested: a Stop that lands before the pid ack arrives kills the process as
  soon as the ack does; a result or process death arriving after Stop is ignored;
  Stop after a normal result kills nothing. Clearing the ViewModel cancels
  `viewModelScope` and stops an in-flight run the same way.
- A program that exhausts memory or crashes the runtime takes the `:runner`
  process down, not the app; the client sees `onServiceDisconnected` and shows
  "The Scheme runner process exited unexpectedly". A killed run leaves its
  `program.scm` (and any uncollected result files) under `cacheDir/kaappi-run/`;
  `SchemeRunnerService.onCreate` sweeps that directory, which is safe because no
  run can be in flight when a runner process comes up.

### `iosApp/` — iOS

```
iosApp/KaappiStudio/
├── KaappiStudioApp.swift    # @main entry point
├── Bridge/SchemeWebView.swift  # UIViewRepresentable + WKScriptMessageHandler coordinator
├── ViewModels/              # Editor / FileBrowser / Settings ObservableObjects
│                            # (FileBrowser and Settings wrap the shared Kotlin repositories)
├── Views/                   # SwiftUI screens (Editor, Examples, FileBrowser, Settings)
├── Helpers/SharedModels.swift  # Identifiable/Date conveniences for the Kotlin models
└── Resources/webview/       # WebView assets bundled into the app
```

Run flow (iOS):

1. The Play button calls `EditorViewModel.runCode()`, which evaluates
   `window.kaappiAPI?.runCode()` in the WebView.
2. `bridge.js` instantiates its pre-compiled `kaappi.wasm` module with a WASI shim,
   writes the program to an in-memory `program.scm`, and runs it.
3. On completion it posts `runComplete` (or `runError`) to native via
   `webkit.messageHandlers.kaappi`.
4. `SchemeWebView.Coordinator` updates `EditorViewModel` published properties and the
   output view re-renders.

The project is generated by [XcodeGen](https://github.com/yonaskolb/XcodeGen) from
`iosApp/project.yml` (bundle id `com.kaappi.studio.ios`, team `9QBV46NATP`, deployment
target iOS 16.0). The generated `.xcodeproj` is committed; regenerate with
`cd iosApp && xcodegen generate` after file changes.

## The WebView layer

Both apps load the same `index.html`, which loads `bridge.js` as a module.
`bridge.js` creates the CodeMirror editor (`editor.js`, importing a prebuilt
`codemirror-bundle.mjs`) and exposes the `window.kaappiAPI` object the native side calls
into. The full message contract is in [Bridge Protocol](bridge-protocol.md).

Asset locations — **kept in sync**, enforced by `scripts/check-webview-assets.sh` in CI:

| File | Android | iOS |
|------|---------|-----|
| `index.html`, `editor.js`, `styles.css`, `codemirror-bundle.mjs` | `app/src/main/assets/webview/` | `iosApp/KaappiStudio/Resources/webview/` |
| `bridge.js` | **Android variant**: editor only, `KaappiBridge.onMessage` | **iOS variant**: pre-compiles WASM at init, implements `runCode()`, posts run events |
| `kaappi.wasm` | assets root (`app/src/main/assets/kaappi.wasm`), read by `SchemeRunner` | `Resources/webview/kaappi.wasm`, fetched by `bridge.js` |
| `worker.js`, `runner.js`, `wasi-shim-bundle.mjs` | not present | present; **only `wasi-shim-bundle.mjs` is currently used** (`bridge.js` imports it directly — `worker.js`/`runner.js` are not wired into `index.html`) |

When you change a shared file (e.g. `editor.js`), copy it to **both** locations;
`bash scripts/check-webview-assets.sh` fails if the copies differ.

## Navigation & UI structure

Both platforms implement the same four sections with a drawer:

| Section | Android | iOS |
|---------|---------|-----|
| Editor | `EditorScreen` (WebView + output pane) | `EditorView` (`SchemeWebView` + output) |
| Examples | `ExamplesScreen` | `ExamplesView` |
| Files | `FileBrowserScreen` | `FileBrowserView` |
| Settings | `SettingsScreen` (theme, font size) | `SettingsView` |

Selecting an example or file pushes its code into the editor via the
`pendingCode` mechanism (Android) or `loadCode` (iOS), then navigates to the Editor.

## Theming

- Native side: `ThemeMode` (Light/Dark/System) stored via `SettingsRepository`;
  Android uses `KaappiStudioTheme` (Material 3), iOS uses its own SwiftUI theme.
- WebView side: native pushes `setTheme('dark'|'light')` and `setFontSize(px)`
  on every update; `styles.css` defines `theme-dark` / `theme-light` classes and the
  editor highlight palettes live in `editor.js`.

## What lives where — quick reference

| Want to change… | Edit |
|-----------------|------|
| Example programs | `shared/.../data/ExampleRepository.kt` (both platforms) |
| Editor keymap/highlighting | `editor.js` (both webview dirs) |
| Editor/output colors | `styles.css` (both), `app/.../ui/theme/`, iOS assets |
| Scheme execution behavior | Android: `runtime/SchemeRunner.kt` (engine), `runtime/IsolatedSchemeRunner.kt` + `SchemeRunnerService.kt` (process isolation, Stop) · iOS: `bridge.js` (iOS variant) |
| File storage layout | `shared/src/*/data/FileRepository.*.kt` (both platforms; iOS via the framework) |
| Settings storage | `shared/src/*/data/SettingsRepository.*.kt` |
| Swift ↔ Kotlin bridge conveniences | `iosApp/KaappiStudio/Helpers/SharedModels.swift` |
| Supported Scheme version | The `kaappi.wasm` binary itself ([kaappi/kaappi](https://github.com/kaappi/kaappi)) |
