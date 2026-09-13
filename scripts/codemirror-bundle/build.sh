#!/usr/bin/env bash
# Rebuilds webview/codemirror-bundle.mjs from entry.mjs with the exact package
# versions pinned in package-lock.json, and writes it to both asset copies
# (scripts/check-webview-assets.sh requires them to be byte-identical).
#
#   bash scripts/codemirror-bundle/build.sh
#
# Needs Node.js (any current LTS) and network access for `npm ci` on the first
# run. To upgrade CodeMirror, bump the versions in package.json, run
# `npm install` here to refresh the lockfile, then rerun this script and commit
# the lockfile together with both bundle copies.
set -euo pipefail
cd "$(dirname "$0")"
repo_root=$(cd ../.. && pwd)
android_out="$repo_root/app/src/main/assets/webview/codemirror-bundle.mjs"
ios_out="$repo_root/iosApp/KaappiStudio/Resources/webview/codemirror-bundle.mjs"

npm ci --silent
npx esbuild entry.mjs \
  --bundle \
  --format=esm \
  --minify \
  --target=es2020 \
  --outfile="$android_out"
cp "$android_out" "$ios_out"
echo "wrote $(wc -c < "$android_out") bytes to:"
echo "  $android_out"
echo "  $ios_out"
