#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-latest}"
DEST="app/src/main/assets/webview/kaappi.wasm"
REPO="kaappi/kaappi"

if [ "$VERSION" = "latest" ]; then
  URL="https://github.com/$REPO/releases/latest/download/kaappi.wasm"
else
  URL="https://github.com/$REPO/releases/download/v$VERSION/kaappi.wasm"
fi

echo "Fetching kaappi.wasm from $URL ..."
curl -fSL -o "$DEST" "$URL"
echo "Saved to $DEST ($(wc -c < "$DEST" | tr -d ' ') bytes)"
