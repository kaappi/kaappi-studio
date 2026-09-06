# Bridge Protocol

The contract between the WebView (JavaScript) and the native shell (Kotlin/Swift).
Both platforms speak the same two-channel design:

- **Native → JS**: calls on the global `window.kaappiAPI` object via
  `evaluateJavascript` (Android) / `evaluateJavaScript` (iOS).
- **JS → Native**: JSON strings `{ "event": "...", ... }` posted via
  `window.KaappiBridge.onMessage()` (Android `@JavascriptInterface`) or
  `window.webkit.messageHandlers.kaappi.postMessage()` (iOS `WKScriptMessageHandler`).

## `window.kaappiAPI` (native → JS)

| Method | Platforms | Purpose |
|--------|-----------|---------|
| `setCode(code)` | both | Replace editor content with a raw string. iOS escapes the string into a JS literal when calling. |
| `setCodeBase64(b64)` | Android | Replace editor content from Base64 (avoids escaping issues on Android). |
| `getCode()` | both | Return editor content. Synchronous return consumed by `evaluateJavascript`'s callback (Android). |
| `runCode()` | iOS only | Execute the current editor content against `kaappi.wasm` inside the WebView. |
| `setTheme(theme)` | both | `"light"` or `"dark"`; switches the `theme-*` body class in `index.html`. |
| `setFontSize(px)` | both | Set the `--kp-font-size` CSS variable. |

Android never calls `runCode()` (it runs Scheme natively via `SchemeRunner`); iOS never
calls `setCodeBase64()` (it uses escaped `setCode`). When adding a method, update
**both** `bridge.js` variants and the relevant native caller.

## Events (JS → native)

All messages are JSON: `{ "event": "<name>", ...payload }`.

| Event | Platforms | Payload | Meaning |
|-------|-----------|---------|---------|
| `ready` | both | — | Editor finished initializing; enables Run/Save buttons and triggers pending-code injection. |
| `runStart` | iOS | — | Execution began (sets `isRunning`). |
| `runComplete` | iOS | `stdout`, `stderr`, `elapsed` | Execution finished successfully (times in ms). |
| `runError` | iOS | `error` | Execution failed. Also posted when `kaappi.wasm` or the WASI shim fails to load during init, and when Play is pressed while the runtime is unavailable. |

On iOS, `runComplete`'s `stdout`/`stderr` are the raw program output (decoded
incrementally, no line buffering), so output without a trailing newline — e.g.
`(display "42")` — is preserved exactly. `kaappi.wasm` is loaded with
`XMLHttpRequest` (`responseType: "arraybuffer"`) because WebKit rejects `fetch()`
for `file:` URLs.

The Android `KaappiBridge` currently only inspects the raw JSON for `"ready"` and ignores
everything else — Android produces run results natively and posts no run events.

## Ready handshake

1. `index.html` loads `bridge.js` as a module.
2. `bridge.js` creates the CodeMirror editor (via `editor.js`), pre-compiling
   `kaappi.wasm` on iOS.
3. It posts `ready` to native.
4. Native marks the editor ready (`EditorViewModel.onReady()` on both platforms),
   enabling the top-bar Run/Save buttons.
5. If code is pending (an example or file was just selected), Android injects it via
   `setCodeBase64` right after `ready` (`onReadyWithWebView`).

## Passing code safely

Never interpolate raw user code into JavaScript strings — quotes, backslashes, and
newlines will break the call or corrupt content:

- **Android** uses `setCodeBase64('<base64>')` (Base64 of UTF-8 bytes).
- **iOS** uses `setCode('<escaped>')`, escaping `\`, `'`, `\n`, and stripping `\r`
  (see `EditorViewModel.loadCode` in Swift).

## Example: full round trip (iOS)

```
User taps Play
  → EditorViewModel.runCode()
  → webView.evaluateJavaScript("window.kaappiAPI?.runCode()")
  → bridge.js: WASI instantiation over pre-compiled wasmModule
  → posts {"event":"runStart"}                          (isRunning = true)
  → posts {"event":"runComplete","stdout":"...","stderr":"...","elapsed":12.3}
  → SchemeWebView.Coordinator updates EditorViewModel
  → SwiftUI output view re-renders
```

## Known gaps

- Android's bridge string-matches `"ready"` in the JSON rather than parsing the
  `event` field; any message containing the substring triggers the ready path.
- `runner.js` / `worker.js` (a Web Worker execution path with a 10 s timeout) exist in
  the iOS assets but are not wired into `index.html`; execution currently runs inline in
  `bridge.js` on the WebView's main thread, so long-running programs can block the UI.
- The protocol is not versioned; the two `bridge.js` variants and the native handlers
  must be kept in lockstep.
