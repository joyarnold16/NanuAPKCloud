#!/usr/bin/env bash
# Generate the ROR Visual Deck release signing key and install it as the four
# GitHub Actions secrets the release workflow expects.
#
# Run this on YOUR machine. The private key must not pass through a chat
# session, a CI log, or the repository - anyone holding it can sign builds as
# this app. It is git-ignored here (*.keystore), but keep the real copy and the
# password somewhere durable and backed up: a password manager, not a laptop
# you might reimage.
#
#   cd ror-colreg && ./setup-release-signing.sh
#
# Requires: keytool (ships with any JDK) and the GitHub CLI (`gh`), logged in
# via `gh auth login` with access to joyarnold16/NanuAPKCloud.

set -euo pipefail

REPO="joyarnold16/NanuAPKCloud"
KEYSTORE="ror-release.keystore"
ALIAS="ror"

command -v keytool >/dev/null || { echo "keytool not found - install a JDK (e.g. Temurin 17)."; exit 1; }
command -v gh >/dev/null || { echo "gh not found - install the GitHub CLI, then 'gh auth login'."; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "gh is not logged in. Run: gh auth login"; exit 1; }

if [ -f "$KEYSTORE" ]; then
  echo "$KEYSTORE already exists here."
  echo "Refusing to overwrite it: regenerating a signing key you have already"
  echo "shipped with would orphan every existing install. Delete it deliberately"
  echo "first if you are certain it was never used."
  exit 1
fi

# A generated password avoids the weak-and-reused ones people pick under time
# pressure. Change it if you would rather choose your own.
PASSWORD="$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)"

echo "Generating $KEYSTORE (alias: $ALIAS, valid 10000 days)..."
keytool -genkeypair -v \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PASSWORD" -keypass "$PASSWORD" \
  -dname "CN=Joy Arnold, OU=ROR Visual Deck, O=Nanu, L=Unknown, ST=Unknown, C=IN"

echo
echo "Uploading secrets to $REPO..."
# base64 without line wraps; the workflow pipes this straight into `base64 -d`.
if base64 --help 2>&1 | grep -q -- '-w'; then
  B64="$(base64 -w0 "$KEYSTORE")"      # GNU coreutils
else
  B64="$(base64 < "$KEYSTORE" | tr -d '\n')"   # BSD/macOS
fi

# printf, not echo, so no trailing newline is stored. A stray newline inside a
# secret is a classic cause of "keystore was tampered with" and alias-mismatch
# failures that look nothing like a whitespace problem.
printf '%s' "$B64"      | gh secret set ROR_RELEASE_KEYSTORE_B64      --repo "$REPO"
printf '%s' "$PASSWORD" | gh secret set ROR_RELEASE_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS"    | gh secret set ROR_RELEASE_KEY_ALIAS         --repo "$REPO"
printf '%s' "$PASSWORD" | gh secret set ROR_RELEASE_KEY_PASSWORD      --repo "$REPO"

echo
echo "=============================================================="
echo " Secrets installed. Store these NOW - they cannot be read back"
echo " out of GitHub, and the keystore file cannot be regenerated."
echo "=============================================================="
echo "  keystore : $(pwd)/$KEYSTORE"
echo "  alias    : $ALIAS"
echo "  password : $PASSWORD"
echo "             (same value for both store and key password)"
echo
echo "Back up the keystore file and the password to a password manager."
echo "With Play App Signing a lost UPLOAD key can be reset by Google, but"
echo "recovering costs you days you will not want to spend mid-launch."
echo
echo "Next: push any commit to main. CI will publish a release-signed"
echo ".aab under a 'ror-visual-deck-release-*' tag. That is the Play upload."
