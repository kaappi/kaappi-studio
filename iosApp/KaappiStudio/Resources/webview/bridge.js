import { createSchemeEditor } from "./editor.js";

let editor = null;
let isRunning = false;
let wasmModule = null;
let wasiShim = null;

const isDark = document.body.classList.contains("theme-dark");

async function init() {
  try {
    const wasmUrl = new URL("./kaappi.wasm", import.meta.url).href;
    const resp = await fetch(wasmUrl);
    const bytes = await resp.arrayBuffer();
    wasmModule = await WebAssembly.compile(bytes);
  } catch (e) {
    console.error("Failed to load WASM:", e);
  }

  try {
    wasiShim = await import("./wasi-shim-bundle.mjs");
  } catch (e) {
    console.error("Failed to load WASI shim:", e);
  }

  editor = await createSchemeEditor({
    parent: document.getElementById("editor-container"),
    doc: '(display "Hello from Kaappi!")\n(newline)\n',
    isDark,
    onRun: () => {},
  });
  notifyNative("ready", {});
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
    if (isRunning || !wasmModule || !wasiShim) return;
    isRunning = true;
    notifyNative("runStart", {});

    try {
      const code = editor.getContent();
      const { WASI, File, OpenFile, ConsoleStdout, PreopenDirectory } = wasiShim;

      const stdoutLines = [];
      const stderrLines = [];

      const fds = [
        new OpenFile(new File([])),
        ConsoleStdout.lineBuffered(line => { stdoutLines.push(line); }),
        ConsoleStdout.lineBuffered(line => { stderrLines.push(line); }),
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
            stderrLines.push(e.message ?? String(e));
          } else if (e.code !== 0) {
            stderrLines.push(e.message ?? String(e));
          }
        }
        const elapsed = performance.now() - t0;
        const stdout = stdoutLines.join("\n") + (stdoutLines.length ? "\n" : "");
        const stderr = stderrLines.join("\n") + (stderrLines.length ? "\n" : "");

        isRunning = false;
        notifyNative("runComplete", { stdout, stderr, elapsed });
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
  },

  setFontSize(px) {
    document.documentElement.style.setProperty("--kp-font-size", `${px}px`);
  },
};

init();
