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
# Fetch the latest WASM binary
bash scripts/fetch-wasm.sh

# Build and run
./gradlew assembleDebug
```

### iOS

Open `iosApp/KaappiStudio.xcodeproj` in Xcode and build for a simulator or device.

### WASM Binary

The WASM binary can be fetched from Kaappi releases or built from source:

```bash
# From release
bash scripts/fetch-wasm.sh          # latest
bash scripts/fetch-wasm.sh 0.21.0   # specific version

# From source
cd ../kaappi && zig build wasm
cp zig-out/bin/kaappi.wasm ../kaappi-studio/app/src/main/assets/webview/
```

## Tech Stack

Matches [Ukulele Companion](https://github.com/baijum/ukulele-companion):

- Kotlin 2.4.0
- Gradle 9.5.1, AGP 9.2.1
- Compose BOM 2026.05.01
- iOS 16+ / Swift 6
- kotlinx-coroutines 1.11.0
- kotlinx-serialization 1.11.0

## License

MIT
