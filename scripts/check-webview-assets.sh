#!/usr/bin/env bash
# Fail if the duplicated webview asset copies drift apart.
#
# AGENTS.md: app/src/main/assets/webview/ and iosApp/KaappiStudio/Resources/
# webview/ hold the same shared assets (index.html, editor.js, styles.css,
# codemirror-bundle.mjs today). Rather than hardcode that list, every file in
# the Android copy must exist in the iOS copy and be byte-identical, so a new
# shared asset added on Android and forgotten on iOS fails here too. The check
# is one-directional: iOS-only extras (worker.js, runner.js,
# wasi-shim-bundle.mjs) are legal.
#
# Two files are skipped: bridge.js is the documented exception (the Android
# variant is editor-only, the iOS variant pre-compiles WASM), and kaappi.wasm
# is the gitignored runtime binary (see scripts/fetch-wasm.sh), never a
# shared source asset.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID="$REPO_ROOT/app/src/main/assets/webview"
IOS="$REPO_ROOT/iosApp/KaappiStudio/Resources/webview"

status=0
checked=0
for path in "$ANDROID"/*; do
  [ -f "$path" ] || continue
  file="$(basename "$path")"
  case "$file" in
    bridge.js|kaappi.wasm) continue ;;
  esac
  checked=$((checked + 1))
  if [ ! -f "$IOS/$file" ]; then
    echo "webview asset MISSING on iOS: $file" >&2
    echo "  $ANDROID/$file" >&2
    echo "  $IOS/$file (not found)" >&2
    status=1
  elif cmp -s "$path" "$IOS/$file"; then
    echo "webview asset OK: $file"
  else
    echo "webview asset DIFFERS: $file" >&2
    echo "  $ANDROID/$file" >&2
    echo "  $IOS/$file" >&2
    status=1
  fi
done

if [ "$checked" -eq 0 ]; then
  echo "no webview assets found under $ANDROID" >&2
  status=1
fi

if [ "$status" -ne 0 ]; then
  echo "" >&2
  echo "Apply the change to both copies (bridge.js may legitimately differ; see AGENTS.md)." >&2
fi
exit "$status"
