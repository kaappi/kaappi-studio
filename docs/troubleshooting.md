# Troubleshooting

Known gotchas in this repository. If you hit something not listed here, consider adding it.

## Missing or misplaced WASM binary

**Symptoms:**

- Android: app builds and runs, but pressing **Run** shows an error in the output pane
  (the raw asset-not-found message from `SchemeRunner`, e.g. `kaappi.wasm`).
- iOS: editor works, but Run silently produces no output; the WebView console shows
  `Failed to load WASM` (the fetch in `bridge.js` failed — also happens in **iOS CI**
  builds, which never fetch the binary).

**Cause:** `kaappi.wasm` is gitignored, so it is not in the repo. It must be fetched
with `scripts/fetch-wasm.sh`, which writes both copies:

- the Android runtime reads `app/src/main/assets/kaappi.wasm` (assets root), and
- the iOS WebView fetches `iosApp/KaappiStudio/Resources/webview/kaappi.wasm`.

**Fix:**

```bash
bash scripts/fetch-wasm.sh
```

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

## iOS build fails in "Build shared Kotlin framework"

The iOS app links `:shared` as a static framework that Xcode builds through Gradle in a
pre-build script phase (`iosApp/project.yml`). When that phase fails:

- **`Unable to locate a Java Runtime` / `JAVA_HOME is not set`** — Xcode's script
  environment does not see your shell profile. Install a JDK 17+ where
  `/usr/libexec/java_home` can find it (e.g. Temurin from Homebrew, then
  `sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk`),
  or launch Xcode with `JAVA_HOME` exported.
- **A Kotlin compile error in `shared/src/iosMain`** — run the same task from a terminal
  for a readable log: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`.
- **`framework 'shared' not found` at link time** — the Gradle phase did not run or
  wrote to a different configuration/SDK directory than Xcode is linking against.
  Check `shared/build/xcode-frameworks/<Debug|Release>/<sdk>/shared.framework` exists;
  a clean build (⇧⌘K) then rebuild regenerates it.
- **`No such module 'shared'`** — same cause as above (the header lives inside the
  framework directory), or `xcodegen generate` was not re-run after a `project.yml`
  change, so `FRAMEWORK_SEARCH_PATHS` is missing from the target.
