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
│        └── SchemeRunner: Chicory JVM WASM runtime           │
│            executes kaappi.wasm natively (Dispatchers.IO)   │
│                                                             │
│  :shared (KMP): domain models, File/Settings repositories   │
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
│  :shared (KMP) framework: File/Settings repositories        │
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
| `:shared` | Kotlin Multiplatform (JVM + Android + iOS targets) | Domain models and `expect`/`actual` repositories |
| `:app` | Jetpack Compose, Material 3, WebView, Chicory | Full Android app |
| `iosApp/` | SwiftUI, WebKit | Full iOS app (consumes `:shared` as a static framework) |

### `:shared` — Kotlin Multiplatform module

`shared/src/commonMain/kotlin/com/kaappi/studio/`

- **`domain/`** — data classes: `Example` + `ExampleCategory`, `SchemeFile`
  (kotlinx-serialization), `RunResult` (stdout/stderr/elapsedMs), `ReplEntry`, `ThemeMode`.
- **`data/`** — `expect` classes implemented per platform:
  - `FileRepository` — user Scheme files (`*.scm`)
  - `SettingsRepository` — theme mode, font size, last opened file

Platform `actual` implementations:

| Concern | Android (`androidMain`) | iOS (`iosMain`) |
|---------|------------------------|-----------------|
| User files | `<filesDir>/schemes/*.scm` via `java.io.File` | `Documents/schemes/*.scm` via `NSFileManager` |
| Settings | `SharedPreferences` (`kaappi_studio_prefs`) | `NSUserDefaults` (standard) |

The module also declares a plain `jvm()` target (useful for fast JVM-side tests) and
Kover for coverage.

### `:app` — Android

```
app/src/main/java/com/kaappi/studio/
├── MainActivity.kt          # navigation drawer, top bar, save dialog, run/save wiring
├── bridge/KaappiBridge.kt   # @JavascriptInterface receiver for WebView messages
├── runtime/SchemeRunner.kt  # Chicory WASI execution of kaappi.wasm
├── ui/screens/              # Editor, Examples, FileBrowser, Settings screens
├── ui/theme/                # Material 3 theme ("Dark Roast" palette)
└── viewmodel/               # Editor / FileBrowser / Settings ViewModels (StateFlow)
```

Run flow (Android):

1. The Play button evaluates `window.kaappiAPI?.getCode()` in the WebView; the JS result
   (a JSON-encoded string) comes back through the `evaluateJavascript` callback.
2. `EditorViewModel.runCode()` invokes `SchemeRunner` on `Dispatchers.IO`.
3. `SchemeRunner` writes the code to `cacheDir/kaappi-run/program.scm`, configures WASI
   with args `["kaappi", "program.scm"]`, instantiates the cached `kaappi.wasm` module in
   a fresh `Store`, and captures stdout/stderr into memory buffers.
4. A `RunResult` flows back to the output pane, including elapsed milliseconds.

Two details worth knowing:

- Chicory throws when the WASI program calls `exit`; `SchemeRunner` treats
  `exit code: 0` messages as success and everything else as stderr output.
- The parsed WASM module is cached; each run re-instantiates but does not re-parse.

### `iosApp/` — iOS

```
iosApp/KaappiStudio/
├── KaappiStudioApp.swift    # @main entry point
├── Bridge/SchemeWebView.swift  # UIViewRepresentable + WKScriptMessageHandler coordinator
├── ViewModels/              # Editor / FileBrowser / Settings ObservableObjects
├── Views/                   # SwiftUI screens (Editor, Examples, FileBrowser, Settings)
├── Helpers/Examples.swift   # example programs (Swift mirror of the shared Kotlin list)
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

Asset locations — **kept in sync manually**:

| File | Android | iOS |
|------|---------|-----|
| `index.html`, `editor.js`, `styles.css`, `codemirror-bundle.mjs` | `app/src/main/assets/webview/` | `iosApp/KaappiStudio/Resources/webview/` |
| `bridge.js` | **Android variant**: editor only, `KaappiBridge.onMessage` | **iOS variant**: pre-compiles WASM at init, implements `runCode()`, posts run events |
| `kaappi.wasm` | assets root (`app/src/main/assets/kaappi.wasm`), read by `SchemeRunner` | `Resources/webview/kaappi.wasm`, fetched by `bridge.js` |
| `worker.js`, `runner.js`, `wasi-shim-bundle.mjs` | not present | present; **only `wasi-shim-bundle.mjs` is currently used** (`bridge.js` imports it directly — `worker.js`/`runner.js` are not wired into `index.html`) |

When you change a shared file (e.g. `editor.js`), copy it to **both** locations.

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
| Example programs | `shared/.../data/ExampleRepository.kt` **and** `iosApp/KaappiStudio/Helpers/Examples.swift` |
| Editor keymap/highlighting | `editor.js` (both webview dirs) |
| Editor/output colors | `styles.css` (both), `app/.../ui/theme/`, iOS assets |
| Scheme execution behavior | Android: `runtime/SchemeRunner.kt` · iOS: `bridge.js` (iOS variant) |
| File storage layout | `shared/src/*/data/FileRepository.*.kt` |
| Supported Scheme version | The `kaappi.wasm` binary itself ([kaappi/kaappi](https://github.com/kaappi/kaappi)) |
