export async function createSchemeEditor({ parent, doc, isDark, onRun }) {
  try {
    const {
      EditorView, EditorState, basicSetup, StreamLanguage,
      HighlightStyle, syntaxHighlighting, tags, scheme,
    } = await import("./codemirror-bundle.mjs");

    const darkHighlight = HighlightStyle.define([
      { tag: [tags.keyword, tags.standard(tags.variableName)], color: "#E8B563" },
      { tag: tags.comment, color: "#9C8A77", fontStyle: "italic" },
      { tag: tags.string, color: "#BFD08A" },
      { tag: tags.number, color: "#E0A867" },
      { tag: tags.atom, color: "#E8B563" },
      { tag: [tags.variableName, tags.definition(tags.variableName)], color: "#4FC4B2" },
      { tag: tags.bracket, color: "#BBA890" },
      { tag: [tags.meta, tags.operator], color: "#D08B3C" },
    ]);

    const lightHighlight = HighlightStyle.define([
      { tag: [tags.keyword, tags.standard(tags.variableName)], color: "#9A6A1E" },
      { tag: tags.comment, color: "#9C8A77", fontStyle: "italic" },
      { tag: tags.string, color: "#5A7A22" },
      { tag: tags.number, color: "#9A6A1E" },
      { tag: tags.atom, color: "#9A6A1E" },
      { tag: [tags.variableName, tags.definition(tags.variableName)], color: "#1E8A7A" },
      { tag: tags.bracket, color: "#8A7A6A" },
      { tag: [tags.meta, tags.operator], color: "#A06A20" },
    ]);

    const editorTheme = (dark) => EditorView.theme({
      "&": { backgroundColor: "transparent" },
      ".cm-content": { caretColor: dark ? "#F3E9DB" : "#1A1410" },
    }, { dark });

    const highlightExtension = (dark) =>
      syntaxHighlighting(dark ? darkHighlight : lightHighlight);

    // Native pushes setTheme on every UI update (including repeats with the
    // same value), so ignore no-op changes. codemirror-bundle.mjs does not
    // export Compartment, so a real theme change re-creates the editor state;
    // doc, selection and scroll position are preserved, undo history is not.
    let currentDark = isDark;
    const view = new EditorView({
      state: EditorState.create({
        doc,
        extensions: [
          basicSetup,
          editorTheme(currentDark),
          highlightExtension(currentDark),
          StreamLanguage.define(scheme),
        ],
      }),
      parent,
    });

    return {
      getContent: () => view.state.doc.toString(),
      setContent: (code) => view.dispatch({
        changes: { from: 0, to: view.state.doc.length, insert: code },
      }),
      setTheme: (dark) => {
        if (dark === currentDark) return;
        currentDark = dark;
        const { doc, selection } = view.state;
        const scrollTop = view.scrollDOM.scrollTop;
        view.setState(EditorState.create({
          doc,
          selection,
          extensions: [
            basicSetup,
            editorTheme(dark),
            highlightExtension(dark),
            StreamLanguage.define(scheme),
          ],
        }));
        view.scrollDOM.scrollTop = scrollTop;
      },
      destroy: () => view.destroy(),
    };
  } catch (e) {
    console.warn("CodeMirror failed to load; using plain textarea.", e);
    const ta = document.createElement("textarea");
    ta.value = doc;
    ta.spellcheck = false;
    Object.assign(ta.style, {
      width: "100%", height: "100%", resize: "none", background: "transparent",
      color: "inherit", border: "none", outline: "none", padding: "8px",
      fontFamily: "var(--kp-mono)", fontSize: "var(--kp-font-size)",
      lineHeight: "1.5", tabSize: 2,
    });
    parent.appendChild(ta);

    return {
      getContent: () => ta.value,
      setContent: (code) => { ta.value = code; },
      setTheme: () => {},
      destroy: () => ta.remove(),
    };
  }
}
