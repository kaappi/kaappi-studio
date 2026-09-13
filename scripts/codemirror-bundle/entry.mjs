// Entry point for codemirror-bundle.mjs. Everything editor.js imports from the
// bundle must be re-exported here; a symbol that is not exported is simply
// missing at runtime (the bundle used to lack Compartment, which is why theme
// changes once had to re-create the editor state — issue #30).
export { Compartment, EditorState } from "@codemirror/state";
export { EditorView } from "@codemirror/view";
export { HighlightStyle, StreamLanguage, syntaxHighlighting } from "@codemirror/language";
export { tags } from "@lezer/highlight";
export { basicSetup } from "codemirror";
export { scheme } from "@codemirror/legacy-modes/mode/scheme";
