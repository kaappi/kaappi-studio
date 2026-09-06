#!/usr/bin/env bash
set -euo pipefail

# Anchor to the repo root so the script can be run from any working directory.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

VERSION="${1:-latest}"
# Release tags are prefixed with "v"; accept both "0.21.0" and "v0.21.0".
VERSION="${VERSION#v}"
REPO="kaappi/kaappi"

if [ "$VERSION" = "latest" ]; then
  BASE_URL="https://github.com/$REPO/releases/latest/download"
else
  BASE_URL="https://github.com/$REPO/releases/download/v$VERSION"
fi

# All three locations the binary is read from:
# - app/src/main/assets/kaappi.wasm                       Android runtime (SchemeRunner, assets root)
# - app/src/main/assets/webview/kaappi.wasm               Android webview assets
# - iosApp/KaappiStudio/Resources/webview/kaappi.wasm     iOS WebView (bridge.js)
DESTS=(
  "$ROOT/app/src/main/assets/kaappi.wasm"
  "$ROOT/app/src/main/assets/webview/kaappi.wasm"
  "$ROOT/iosApp/KaappiStudio/Resources/webview/kaappi.wasm"
)

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "Fetching kaappi.wasm from $BASE_URL/kaappi.wasm ..."
curl -fSL -o "$TMP_DIR/kaappi.wasm" "$BASE_URL/kaappi.wasm"

# Verify against the SHA256SUMS asset published with the release.
echo "Fetching SHA256SUMS from $BASE_URL/SHA256SUMS ..."
curl -fsSL -o "$TMP_DIR/SHA256SUMS" "$BASE_URL/SHA256SUMS"
if command -v sha256sum >/dev/null 2>&1; then
  grep ' kaappi.wasm$' "$TMP_DIR/SHA256SUMS" | (cd "$TMP_DIR" && sha256sum -c -)
else
  grep ' kaappi.wasm$' "$TMP_DIR/SHA256SUMS" | (cd "$TMP_DIR" && shasum -a 256 -c -)
fi

for DEST in "${DESTS[@]}"; do
  mkdir -p "$(dirname "$DEST")"
  cp "$TMP_DIR/kaappi.wasm" "$DEST"
  echo "Saved to $DEST ($(wc -c < "$DEST" | tr -d ' ') bytes)"
done
