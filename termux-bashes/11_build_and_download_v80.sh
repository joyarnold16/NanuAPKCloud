#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO="joyarnold16/NanuAPKCloud"
TAG="nanu-ai-trading-bot-v8-0-dex-safety-paper"
APK="nanu-ai-trading-bot-v8-0-dex-safety-paper.apk"

termux-setup-storage >/dev/null 2>&1 || true
gh workflow run build-apk.yml -R "$REPO" --ref main
echo "Build started. Wait for it to complete, then run this script again if needed."
gh run watch -R "$REPO" --exit-status

OUT="$HOME/storage/downloads/$APK"
gh release download "$TAG" -R "$REPO" -p "$APK" -O "$OUT" --clobber
echo "Downloaded: $OUT"
ls -lh "$OUT"
