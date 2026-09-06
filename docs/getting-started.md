# Getting Started

Set up a development environment and get Kaappi Studio running on Android and iOS.

## Prerequisites

| Tool | Version | Needed for |
|------|---------|-----------|
| JDK | 17+ | All Gradle builds (CI uses Temurin 17; Gradle downloads matching toolchains via foojay) |
| Android Studio | Current | Android development, emulator/device |
| Xcode | 15+ | iOS development, simulator/device |
| XcodeGen (`brew install xcodegen`) | Latest | Regenerating the Xcode project after adding/removing iOS files |
| [`gh` CLI](https://cli.github.com/) | Latest | Release automation (optional for day-to-day work) |

Versions are centralized in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml).
Current stack: Kotlin 2.4.0, Gradle 9.5.1, AGP 9.2.1, Compose BOM 2026.05.01,
iOS deployment target 16.0, Swift 6.

## Clone and set up

```bash
git clone git@github.com:kaappi/kaappi-studio.git
cd kaappi-studio
```

The Gradle wrapper handles everything else on first build:

```bash
./gradlew help   # first run downloads Gradle 9.5.1 and dependencies
```

## WASM binary (required)

The Scheme interpreter is a `wasm32-wasi` binary (`kaappi.wasm`) built from the
[kaappi/kaappi](https://github.com/kaappi/kaappi) project. It is **gitignored and not in
the repo**, so fetch it after cloning:

```bash
bash scripts/fetch-wasm.sh          # latest release
bash scripts/fetch-wasm.sh 0.21.0   # a specific version
```

**Known quirk:** the script writes to `app/src/main/assets/webview/kaappi.wasm`, but the
Android runtime (`SchemeRunner`) reads the binary from the assets **root**,
`app/src/main/assets/kaappi.wasm`. Until the script is updated, copy it there too —
otherwise Android builds fine but every Run fails with a runtime error:

```bash
cp app/src/main/assets/webview/kaappi.wasm app/src/main/assets/kaappi.wasm
```

For iOS, place a copy where the WebView can fetch it (this path is also gitignored;
`fetch-wasm.sh` does **not** write here):

```bash
cp app/src/main/assets/webview/kaappi.wasm iosApp/KaappiStudio/Resources/webview/kaappi.wasm
```

See [Troubleshooting](troubleshooting.md#missing-or-misplaced-wasm-binary) for the
failure symptoms when a copy is missing.

### Building the WASM binary from source

Instead of downloading a release, you can build it from the Kaappi source tree if you
have a checkout of `kaappi` next to this repo:

```bash
cd ../kaappi && zig build wasm
# then copy to all three locations listed above
```

## Android

```bash
bash scripts/fetch-wasm.sh
cp app/src/main/assets/webview/kaappi.wasm app/src/main/assets/kaappi.wasm

# Debug APK
./gradlew assembleDebug

# Install on a connected device/emulator
./gradlew installDebug
```

Or open the project root in Android Studio and press Run.

- Module: `:app`, application id `com.kaappi.studio`
- `minSdk` 26, `targetSdk` 35, `compileSdk` 36

## iOS

The Xcode project is generated from [`iosApp/project.yml`](../iosApp/project.yml).
A pre-generated `KaappiStudio.xcodeproj` is committed, so you can open it directly:

```bash
cp app/src/main/assets/webview/kaappi.wasm iosApp/KaappiStudio/Resources/webview/kaappi.wasm
open iosApp/KaappiStudio.xcodeproj
```

Select the `KaappiStudio` scheme and an iPhone simulator, then Run.

Regenerate the project whenever you add, remove, or move files under `iosApp/` —
otherwise the new files are not part of the build:

```bash
cd iosApp && xcodegen generate
```

## Verify your setup

- Android: `./gradlew assembleDebug` succeeds and the app launches; pressing **Run**
  in the Editor tab prints `Hello from Kaappi!`
- iOS: the app builds in Xcode and the Editor tab behaves the same
- CI parity: `./gradlew test` (Android) and the `KaappiStudio` scheme test action (iOS)
  are what GitHub Actions runs — see [Development](development.md#continuous-integration)

## Next steps

- [Architecture](architecture.md) — understand what you just built
- [Development](development.md) — tests, linting, and common tasks
- [Troubleshooting](troubleshooting.md) — when something doesn't work
