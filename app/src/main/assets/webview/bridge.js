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
    if (editor) editor.setContent(atob(b64));
  },

  getCode() {
    return editor ? editor.getContent() : "";
  },

  setTheme(themeName) {
    document.body.className = `theme-${themeName}`;
  },

  setFontSize(px) {
    document.documentElement.style.setProperty("--kp-font-size", `${px}px`);
  },
};

init();
