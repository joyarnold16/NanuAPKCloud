#!/data/data/com.termux/files/usr/bin/bash
set -e
REPO="joyarnold16/NanuAPKCloud"
TAG="nanu-ai-trading-bot-v8-0-dex-safety-paper"
OUT="$HOME/storage/downloads"

echo "🔍 Fetching release: $TAG"
API="https://api.github.com/repos/$REPO/releases/tags/$TAG"
URL=$(curl -s "$API" | grep "browser_download_url" | grep ".apk" | head -1 | cut -d '"' -f 4)

if [ -z "$URL" ]; then
  echo "❌ No APK on that tag. Check https://github.com/$REPO/releases"
  exit 1
fi

FILE="$OUT/nanu-latest.apk"
echo "⬇️  Downloading ..."
curl -L -o "$FILE" "$URL"
echo "✅ Saved: $FILE"
echo ""
SIZE=$(stat -c%s "$FILE")
echo "   Size: $((SIZE / 1024 / 1024)) MB"
unzip -l "$FILE" >/dev/null 2>&1 && echo "   ✓ Valid APK" || { echo "   ❌ Corrupt"; exit 1; }
echo "🔐 SHA-256:"
sha256sum "$FILE" | cut -d ' ' -f 1
echo ""
echo "Install with: termux-open \"$FILE\""
