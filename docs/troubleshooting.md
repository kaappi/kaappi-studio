# Troubleshooting

Known gotchas in this repository. If you hit something not listed here, consider adding it.

## Missing or misplaced WASM binary

**Symptoms:**

- Android: app builds and runs, but pressing **Run** shows an error in the output pane
  (the raw asset-not-found message from `SchemeRunner`, e.g. `kaappi.wasm`).
- iOS: editor works, but Run silently produces no output; the WebView console shows
  `Failed to load WASM` (the fetch in `bridge.js` failed — also happens in **iOS CI**
  builds, which never fetch the binary).

**Cause:** `kaappi.wasm` is gitignored, and `scripts/fetch-wasm.sh` writes only to
`app/src/main/assets/webview/kaappi.wasm`, while:

- the Android runtime reads `app/src/main/assets/kaappi.wasm` (assets root), and
- the iOS WebView fetches `iosApp/KaappiStudio/Resources/webview/kaappi.wasm`.

**Fix:**

```bash
bash scripts/fetch-wasm.sh
cp app/src/main/assets/webview/kaappi.wasm app/src/main/assets/kaappi.wasm
cp app/src/main/assets/webview/kaappi.wasm iosApp/KaappiStudio/Resources/webview/kaappi.wasm
```

(Upstream candidates: point the script at the Android assets root, or make `SchemeRunner`
read the `webview/` copy, and have iOS CI fetch the binary.)

## iOS build doesn't include new Swift files

The Xcode project is **generated** from `project.yml` by XcodeGen. New/removed/moved
files under `iosApp/` are invisible to the committed `KaappiStudio.xcodeproj` until you
regenerate:

```bash
cd iosApp && xcodegen generate
```

Then commit the updated `project.pbxproj`.

## Android: `Run`/`Save` buttons are disabled

The buttons stay disabled until the WebView posts the `ready` event
([Bridge Protocol](bridge-protocol.md#ready-handshake)). If they never enable:

- Reload/recreate the emulator (WebView startup failure).
- Check that `index.html` and `bridge.js` exist in `app/src/main/assets/webview/` —
  the editor cannot boot from a partial assets directory.
- On a fresh clone you may have fetched the WASM binary but not copied it to the assets
  root; the editor still becomes ready, but Run will fail — see the first section.

## Long-running Scheme programs freeze the iOS UI

iOS executes Scheme **inside the WebView on the main thread** (see
[Bridge Protocol — known gaps](bridge-protocol.md#known-gaps)); there is no timeout wired
in. Android runs on a background dispatcher via Chicory and shows a progress
indicator instead. Keep iOS test programs short, or wire up the existing-but-unused
`worker.js`/`runner.js` Web Worker path.

## Gradle: first build is slow or fails on JDK mismatch

The project targets JDK 17+ (CI uses Temurin 17); Gradle downloads the correct
toolchain via the foojay resolver plugin. If the build still picks a wrong JDK,
set `org.gradle.java.installations.paths` or your `JAVA_HOME` explicitly. Android
compile tasks target JVM 11 bytecode (`jvmTarget = 11`).

## Play upload fails (`publishReleaseBundle`)

`app/play-service-account.json` is gitignored — it exists only on machines that were
set up for releases. Without it, skip the upload step; the AAB in
`app/build/outputs/bundle/release/` can be uploaded manually through the Play Console.

## Scheme examples look different on Android vs iOS

Example programs are duplicated in
[`ExampleRepository.kt`](../shared/src/commonMain/kotlin/com/kaappi/studio/data/ExampleRepository.kt)
and [`Examples.swift`](../iosApp/KaappiStudio/Helpers/Examples.swift). If one was
updated without the other, they drift — see
[Development](development.md#add-or-edit-an-example-program).
