#!/usr/bin/env bash
# Fail if the duplicated webview asset copies drift apart.
#
# AGENTS.md: app/src/main/assets/webview/ and iosApp/KaappiStudio/Resources/
# webview/ must contain identical index.html, editor.js, styles.css and
# codemirror-bundle.mjs. bridge.js is the documented exception — the Android
# variant is editor-only, the iOS variant pre-compiles WASM — so it is
# deliberately not compared.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID="$REPO_ROOT/app/src/main/assets/webview"
IOS="$REPO_ROOT/iosApp/KaappiStudio/Resources/webview"

status=0
for file in index.html editor.js styles.css codemirror-bundle.mjs; do
  if cmp -s "$ANDROID/$file" "$IOS/$file"; then
    echo "webview asset OK: $file"
  else
    echo "webview asset DIFFERS: $file" >&2
    echo "  $ANDROID/$file" >&2
    echo "  $IOS/$file" >&2
    status=1
  fi
done

if [ "$status" -ne 0 ]; then
  echo "" >&2
  echo "Apply the change to both copies (bridge.js may legitimately differ; see AGENTS.md)." >&2
fi
exit "$status"
