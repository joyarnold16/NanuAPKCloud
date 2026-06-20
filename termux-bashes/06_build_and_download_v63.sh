#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO="joyarnold16/NanuAPKCloud"
WORKFLOW="build-apk.yml"
TAG="nanu-ai-trading-bot-v6-3-spot-executor"
APK="nanu-ai-trading-bot-v6-3-spot-executor.apk"
DEST="$HOME/storage/downloads/$APK"

termux-setup-storage >/dev/null 2>&1 || true
command -v gh >/dev/null || { echo "Install GitHub CLI first: pkg install gh"; exit 1; }
gh auth status -h github.com >/dev/null

if gh release view "$TAG" -R "$REPO" >/dev/null 2>&1; then
  echo "The v6.3 release already exists."
else
  RUN_ID="$(gh run list -R "$REPO" --workflow "$WORKFLOW" --branch main --limit 1 --json databaseId,status --jq '.[] | select(.status == "queued" or .status == "in_progress" or .status == "waiting") | .databaseId' || true)"
  if test -z "$RUN_ID"; then
    echo "Starting the signed Nanu v6.3 APK build..."
    gh workflow run "$WORKFLOW" -R "$REPO" --ref main
    sleep 5
    RUN_ID="$(gh run list -R "$REPO" --workflow "$WORKFLOW" --branch main --limit 1 --json databaseId --jq '.[0].databaseId')"
  else
    echo "Using the running GitHub Actions build $RUN_ID..."
  fi
  test -n "$RUN_ID" || { echo "Could not locate the GitHub Actions run."; exit 1; }
  gh run watch "$RUN_ID" -R "$REPO" --exit-status
fi

echo "Downloading the signed APK..."
gh release download "$TAG" -R "$REPO" -p "$APK" -O "$DEST" --clobber
echo "Done: $DEST"
ls -lh "$DEST"
