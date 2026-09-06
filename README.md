# Kaappi Studio

A learning environment for the Scheme programming language for iOS and Android.

Write, run, and experiment with Scheme code directly on your Android or iOS device. Whether you're learning functional programming, testing an idea, or developing a complete application, Kaappi Studio provides a fast, interactive experience wherever you are.

## Features

- **Interactive Scheme REPL** — write and run code instantly
- **Syntax-highlighting code editor** — CodeMirror 6 with Kaappi's "Dark Roast" palette
- **Run programs instantly** — powered by WebAssembly for portability and performance
- **Browse and edit example programs** — 15 curated examples across 5 categories
- **Save and open local files** — persist your work on-device
- **Dark mode support** — Light, Dark, and System themes
- **Offline execution** — the WASM interpreter runs entirely on-device

## Architecture

Kaappi Studio is a **Kotlin Multiplatform** (KMP) app:

| Layer | Technology |
|-------|-----------|
| Shared logic | Kotlin Multiplatform (`:shared` module) |
| Android UI | Jetpack Compose + Material 3 |
| iOS UI | SwiftUI |
| Code editor | CodeMirror 6 (WebView) |
| Scheme engine | Kaappi WASM binary (wasm32-wasi) |
| Bridge | Android `@JavascriptInterface` / iOS `WKScriptMessageHandler` |

The native shell handles navigation, file management, settings, and theming. A WebView hosts the CodeMirror editor and WASM runner, communicating with the native layer via a JSON message bridge.

## Building

### Prerequisites

- JDK 17+
- Android Studio (for Android)
- Xcode 15+ (for iOS)
- `kaappi.wasm` binary (fetched automatically or built from source)

### Android

```bash
# Fetch the latest WASM binary (writes both platform locations)
bash scripts/fetch-wasm.sh

# Build and run
./gradlew assembleDebug
```

### iOS

Open `iosApp/KaappiStudio.xcodeproj` in Xcode and build for a simulator or device.

### WASM Binary

The Scheme interpreter ships as a WebAssembly binary (`kaappi.wasm`, gitignored).
`fetch-wasm.sh` downloads it from Kaappi releases, verifies it against the release's
published `SHA256SUMS` (the script fails hard if a release has no `SHA256SUMS` asset —
all releases since v0.21.0 publish one), and writes it to the two locations the
platforms read:

```bash
bash scripts/fetch-wasm.sh            # latest
bash scripts/fetch-wasm.sh 0.21.0     # specific version (a "v" prefix is accepted too)

# - app/src/main/assets/kaappi.wasm                       (Android runtime, Chicory)
# - iosApp/KaappiStudio/Resources/webview/kaappi.wasm     (iOS WebView)
```

Or build it from source:

```bash
cd ../kaappi && zig build wasm
cp zig-out/bin/kaappi.wasm ../kaappi-studio/app/src/main/assets/kaappi.wasm
cp zig-out/bin/kaappi.wasm ../kaappi-studio/iosApp/KaappiStudio/Resources/webview/kaappi.wasm
```

## Tech Stack

Matches [Ukulele Companion](https://github.com/baijum/ukulele-companion):

- Kotlin 2.4.0
- Gradle 9.5.1, AGP 9.2.1
- Compose BOM 2026.05.01
- iOS 16+ / Swift 6
- kotlinx-coroutines 1.11.0
- kotlinx-serialization 1.11.0

## Contributing

Developer documentation lives in [`docs/`](docs/README.md):

- [Getting Started](docs/getting-started.md) — setup and first build
- [Architecture](docs/architecture.md) — how the app works
- [Bridge Protocol](docs/bridge-protocol.md) — WebView ↔ native messaging
- [Development Guide](docs/development.md) — tests, lint, CI, common tasks
- [Release Process](docs/release.md) — versioning and shipping
- [Troubleshooting](docs/troubleshooting.md) — known gotchas

## License

MIT
