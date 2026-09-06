import { createSchemeEditor } from "./editor.js";

let editor = null;

const isDark = document.body.classList.contains("theme-dark");

async function init() {
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
  if (window.KaappiBridge) {
    window.KaappiBridge.onMessage(msg);
  } else if (window.webkit?.messageHandlers?.kaappi) {
    window.webkit.messageHandlers.kaappi.postMessage(msg);
  }
}

window.kaappiAPI = {
  setCode(code) {
    if (editor) editor.setContent(code);
  },

  setCodeBase64(b64) {
    if (!editor) return;
    // atob() yields one Latin-1 character per byte, so UTF-8 multibyte
    // sequences (λ, é, curly quotes) would become mojibake (issue #6).
    // Decode the bytes explicitly as UTF-8 instead.
    editor.setContent(
      new TextDecoder().decode(Uint8Array.from(atob(b64), c => c.charCodeAt(0))));
  },

  getCode() {
    return editor ? editor.getContent() : "";
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
