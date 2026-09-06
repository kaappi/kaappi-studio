import { createSchemeEditor } from "./editor.js";

let editor = null;
let isRunning = false;
let wasmModule = null;
let wasiShim = null;
let initFailures = null;

const isDark = document.body.classList.contains("theme-dark");

// The WASM is loaded with XMLHttpRequest rather than fetch() because XHR's
// file: URL support has been more consistent across WebKit versions than
// fetch()'s; both currently require the allowFileAccessFromFileURLs
// preference set in SchemeWebView.swift (verified on iOS 26: with it, both
// succeed; without it, both fail). For file: URLs XHR reports status 0 on
// success, so treat 0 as OK when a non-empty body is present.
function loadWasmBytes(url) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("GET", url);
    xhr.responseType = "arraybuffer";
    xhr.onload = () => {
      const ok = xhr.response instanceof ArrayBuffer && xhr.response.byteLength > 0
        && (xhr.status === 0 || (xhr.status >= 200 && xhr.status < 300));
      if (ok) {
        resolve(xhr.response);
      } else if (xhr.response && xhr.response.byteLength === 0) {
        reject(new Error(`kaappi.wasm is empty (${url})`));
      } else {
        reject(new Error(`HTTP ${xhr.status} loading ${url}`));
      }
    };
    xhr.onerror = () => reject(new Error(`Network error loading ${url}`));
    xhr.send();
  });
}

async function init() {
  const failures = [];

  try {
    const wasmUrl = new URL("./kaappi.wasm", import.meta.url).href;
    const bytes = await loadWasmBytes(wasmUrl);
    wasmModule = await WebAssembly.compile(bytes);
  } catch (e) {
    console.error("Failed to load WASM:", e);
    failures.push(`Scheme runtime failed to load: ${e?.message ?? e}`);
  }

  try {
    wasiShim = await import("./wasi-shim-bundle.mjs");
  } catch (e) {
    console.error("Failed to load WASI shim:", e);
    failures.push(`WASI shim failed to load: ${e?.message ?? e}`);
  }

  editor = await createSchemeEditor({
    parent: document.getElementById("editor-container"),
    doc: '(display "Hello from Kaappi!")\n(newline)\n',
    isDark,
    onRun: () => {},
  });
  notifyNative("ready", {});

  // Surface load failures: the native Coordinator handles runError by showing
  // the error text; console.error alone is invisible inside a WKWebView. This
  // intentionally fires on every cold start when the runtime is missing (e.g.
  // the gitignored kaappi.wasm was never fetched) — it is the app's only
  // feedback that Scheme execution is unavailable.
  if (failures.length > 0) {
    initFailures = failures.join(" ");
    notifyNative("runError", { error: initFailures });
  }
}

function notifyNative(event, data) {
  const msg = JSON.stringify({ event, ...data });
  if (window.webkit?.messageHandlers?.kaappi) {
    window.webkit.messageHandlers.kaappi.postMessage(msg);
  }
}

window.kaappiAPI = {
  setCode(code) {
    if (editor) editor.setContent(code);
  },

  getCode() {
    return editor ? editor.getContent() : "";
  },

  runCode() {
    if (isRunning) return;
    if (!wasmModule || !wasiShim) {
      // Never fail silently: reuse the init-time failure details (which join
      // every load error) so the user sees exactly why execution is unavailable.
      notifyNative("runError", {
        error: initFailures || "Scheme runtime is unavailable: initialization failed.",
      });
      return;
    }
    isRunning = true;
    notifyNative("runStart", {});

    try {
      const code = editor.getContent();
      const { WASI, File, OpenFile, ConsoleStdout, PreopenDirectory } = wasiShim;

      // Raw write sinks (no line splitting): ConsoleStdout.lineBuffered keeps
      // the trailing partial line in an internal buffer it never flushes, so
      // output like (display "42") without a newline would be lost. Decode
      // incrementally and flush the decoders after wasi.start returns.
      const stdoutDecoder = new TextDecoder("utf-8", { fatal: false });
      const stderrDecoder = new TextDecoder("utf-8", { fatal: false });
      let stdoutText = "";
      let stderrText = "";

      const fds = [
        new OpenFile(new File([])),
        new ConsoleStdout(bytes => { stdoutText += stdoutDecoder.decode(bytes, { stream: true }); }),
        new ConsoleStdout(bytes => { stderrText += stderrDecoder.decode(bytes, { stream: true }); }),
        new PreopenDirectory(".", [
          ["program.scm", new File(new TextEncoder().encode(code))],
        ]),
      ];

      const wasi = new WASI(["kaappi", "program.scm"], [], fds);
      WebAssembly.instantiate(wasmModule, {
        wasi_snapshot_preview1: wasi.wasiImport,
      }).then(instance => {
        const t0 = performance.now();
        try {
          wasi.start(instance);
        } catch (e) {
          if (e instanceof WebAssembly.RuntimeError) {
            stderrText += (e.message ?? String(e)) + "\n";
          } else if (e.code !== 0) {
            stderrText += (e.message ?? String(e)) + "\n";
          }
        }
        const elapsed = performance.now() - t0;
        stdoutText += stdoutDecoder.decode();
        stderrText += stderrDecoder.decode();

        isRunning = false;
        notifyNative("runComplete", { stdout: stdoutText, stderr: stderrText, elapsed });
      }).catch(e => {
        isRunning = false;
        notifyNative("runError", { error: e.message });
      });
    } catch (e) {
      isRunning = false;
      notifyNative("runError", { error: e.message });
    }
  },

  setTheme(themeName) {
    document.body.className = `theme-${themeName}`;
    // Body class alone is not enough: CodeMirror's caret color, dark flag and
    // highlight palette are configured in editor.js and need reconfiguring
    // too (issue #7).
    if (editor) editor.setTheme(themeName === "dark");
  },

  setFontSize(px) {
    document.documentElement.style.setProperty("--kp-font-size", `${px}px`);
  },
};

init();
