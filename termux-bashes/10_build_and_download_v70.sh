#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO="joyarnold16/NanuAPKCloud"
WORKFLOW="build-apk.yml"
TAG="nanu-ai-trading-bot-v7-0-auto-entry-hardening"
APK="nanu-ai-trading-bot-v7-0-auto-entry-hardening.apk"
DEST="$HOME/storage/downloads/$APK"

termux-setup-storage >/dev/null 2>&1 || true
command -v gh >/dev/null || { echo "Install GitHub CLI first: pkg install gh"; exit 1; }
gh auth status -h github.com >/dev/null

if gh release view "$TAG" -R "$REPO" >/dev/null 2>&1; then
  echo "The v7.0 Automatic Entry Hardening release already exists. Downloading it now."
else
  echo "Starting the signed Nanu AI Trading Bot v7.0 build..."
  gh workflow run "$WORKFLOW" -R "$REPO" --ref main
  sleep 5
  RUN_ID="$(gh run list -R "$REPO" --workflow "$WORKFLOW" --branch main --limit 1 --json databaseId --jq '.[0].databaseId')"
  test -n "$RUN_ID" || { echo "Could not locate the GitHub Actions run."; exit 1; }
  gh run watch "$RUN_ID" -R "$REPO" --exit-status
fi

echo "Downloading the signed APK..."
gh release download "$TAG" -R "$REPO" -p "$APK" -O "$DEST" --clobber
echo "Done: $DEST"
ls -lh "$DEST"
