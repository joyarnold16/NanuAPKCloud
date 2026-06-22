#!/data/data/com.termux/files/usr/bin/bash
# Downloads + verifies the latest Nanu APK from GitHub releases.
set -e

REPO="joyarnold16/NanuAPKCloud"
OUT="$HOME/storage/downloads"

echo "🔍 Finding latest release for $REPO ..."

# Get the latest release's APK download URL from GitHub API
API="https://api.github.com/repos/$REPO/releases/latest"
URL=$(curl -s "$API" | grep "browser_download_url" | grep ".apk" | head -1 | cut -d '"' -f 4)
TAG=$(curl -s "$API" | grep '"tag_name"' | head -1 | cut -d '"' -f 4)

if [ -z "$URL" ]; then
  echo "❌ No APK found in the latest release."
  echo "   Check: https://github.com/$REPO/releases"
  exit 1
fi

FILE="$OUT/${TAG}.apk"
echo "📦 Release: $TAG"
echo "⬇️  Downloading APK ..."
curl -L -o "$FILE" "$URL"

echo ""
echo "✅ Downloaded: $FILE"
echo ""

# ── Verify the APK ──────────────────────────────────────────
echo "🔎 Verifying APK integrity ..."

SIZE=$(stat -c%s "$FILE")
echo "   Size: $((SIZE / 1024 / 1024)) MB ($SIZE bytes)"

# Check it's a valid ZIP/APK (APKs are ZIP archives)
if unzip -l "$FILE" >/dev/null 2>&1; then
  echo "   ✓ Valid APK archive structure"
else
  echo "   ❌ File is corrupt — not a valid APK!"
  exit 1
fi

# Confirm it contains the compiled app + manifest
if unzip -l "$FILE" | grep -q "AndroidManifest.xml"; then
  echo "   ✓ Contains AndroidManifest.xml"
fi
if unzip -l "$FILE" | grep -q "classes.dex"; then
  echo "   ✓ Contains compiled code (classes.dex)"
fi

# SHA-256 fingerprint (for your records)
echo ""
echo "🔐 SHA-256 checksum:"
sha256sum "$FILE" | cut -d ' ' -f 1

echo ""
echo "✅ APK verified and ready to install."
echo "   Tap it in your Downloads folder, or run:"
echo "   termux-open \"$FILE\""
