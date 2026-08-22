#!/usr/bin/env bash
# Generate the Nanu ROR Visual Deck release signing key and install it as the
# four GitHub Actions secrets the release workflow expects.
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
# via `gh auth login` with admin access to joyarnold16/NanuAPKCloud.

set -euo pipefail

REPO="joyarnold16/NanuAPKCloud"
KEYSTORE="ror-release.keystore"
ALIAS="ror"
SECRET_NAMES=(
  ROR_RELEASE_KEYSTORE_B64
  ROR_RELEASE_KEYSTORE_PASSWORD
  ROR_RELEASE_KEY_ALIAS
  ROR_RELEASE_KEY_PASSWORD
)

fail() { echo; echo "ERROR: $*" >&2; exit 1; }

command -v keytool >/dev/null || fail "keytool not found - install a JDK (e.g. Temurin 17)."
command -v gh >/dev/null || fail "gh not found - install the GitHub CLI, then 'gh auth login'."
gh auth status >/dev/null 2>&1 || fail "gh is not logged in. Run: gh auth login"

# ---------------------------------------------------------------------------
# Confirm WHERE the secrets are about to go before generating anything. A run
# that silently targets the wrong account is the failure this script existed to
# prevent and did not: every 'gh secret set' can succeed against a repository
# that is not the one CI builds from.
# ---------------------------------------------------------------------------
ACCOUNT="$(gh api user --jq .login 2>/dev/null || echo '<unknown>')"
echo "GitHub account : $ACCOUNT"
echo "Target repo    : $REPO"

PERM="$(gh api "repos/$REPO" --jq '.permissions.admin' 2>/dev/null || echo 'unreachable')"
case "$PERM" in
  true) echo "Access         : admin (can set secrets)" ;;
  false) fail "$ACCOUNT can see $REPO but is not an admin of it.
Setting Actions secrets requires admin. Either log in as the repository owner
('gh auth login' again) or have the owner run this script." ;;
  *) fail "Cannot reach $REPO as $ACCOUNT.
Check the name, and that this account has access to it." ;;
esac
echo

if [ -f "$KEYSTORE" ]; then
  echo "$KEYSTORE already exists here."
  echo "Refusing to overwrite it: regenerating a signing key you have already"
  echo "shipped with would orphan every existing install. Delete it deliberately"
  echo "first if you are certain it was never used."
  exit 1
fi

read -r -p "Generate a new signing key and upload secrets to $REPO? [y/N] " reply
case "$reply" in y|Y|yes|YES) ;; *) echo "Aborted."; exit 1 ;; esac
echo

# A generated password avoids the weak-and-reused ones people pick under time
# pressure. Change it if you would rather choose your own.
#
# Read a fixed number of bytes rather than piping an endless /dev/urandom into
# `head -c 32`. That pipeline is what silently broke this script: head exits at
# 32 bytes, tr dies of SIGPIPE with status 141, `pipefail` promotes it to the
# pipeline's status, and `set -e` kills the run right here - after printing
# nothing, before generating a key, and before uploading a single secret. od
# reads exactly what it needs and closes cleanly, so there is no early reader
# to signal anyone.
PASSWORD="$(od -An -vtx1 -N24 /dev/urandom | tr -d ' \n')"
[ "${#PASSWORD}" -eq 48 ] || fail "Could not generate a password from /dev/urandom."

echo "Generating $KEYSTORE (alias: $ALIAS, valid 10000 days)..."
keytool -genkeypair -v \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PASSWORD" -keypass "$PASSWORD" \
  -dname "CN=Joy Arnold, OU=ROR Visual Deck, O=Nanu, L=Unknown, ST=Unknown, C=IN"

# Prove the keystore is readable with the password we just set, before it
# becomes the thing CI depends on.
keytool -list -keystore "$KEYSTORE" -storepass "$PASSWORD" -alias "$ALIAS" >/dev/null \
  || fail "The keystore was written but cannot be opened with its own password."

echo
echo "Uploading secrets to $REPO..."
# base64 without line wraps; the workflow pipes this straight into `base64 -d`.
# Test the capability directly. `base64 --help | grep -q` is the same
# early-reader SIGPIPE trap as above, just with a shorter fuse.
if printf '' | base64 -w0 >/dev/null 2>&1; then
  B64="$(base64 -w0 "$KEYSTORE")"      # GNU coreutils
else
  B64="$(base64 < "$KEYSTORE" | tr -d '\n')"   # BSD/macOS
fi

# Round-trip the encoding here rather than discovering it in CI, where the only
# symptom is an unhelpful "keystore was tampered with" from Gradle.
printf '%s' "$B64" | base64 -d 2>/dev/null | cmp -s - "$KEYSTORE" \
  || fail "base64 round-trip does not reproduce the keystore. Not uploading."

# printf, not echo, so no trailing newline is stored. A stray newline inside a
# secret is a classic cause of "keystore was tampered with" and alias-mismatch
# failures that look nothing like a whitespace problem.
set_secret() {
  local name="$1" value="$2"
  printf '%s' "$value" | gh secret set "$name" --repo "$REPO" \
    || fail "Failed to set $name. Nothing further was uploaded."
  echo "  set $name"
}

set_secret ROR_RELEASE_KEYSTORE_B64      "$B64"
set_secret ROR_RELEASE_KEYSTORE_PASSWORD "$PASSWORD"
set_secret ROR_RELEASE_KEY_ALIAS         "$ALIAS"
set_secret ROR_RELEASE_KEY_PASSWORD      "$PASSWORD"

# ---------------------------------------------------------------------------
# Read back. `gh secret set` exiting 0 is not proof the workflow can see the
# secret: repository, environment and Dependabot scopes are three different
# stores, and only the first is readable by these jobs. This lists what is
# actually at repository scope.
# ---------------------------------------------------------------------------
echo
echo "Verifying at repository scope..."
# --json needs gh >= 2.24; older builds print a name/updated-at table. Take
# whichever works and reduce both shapes to bare names in one place.
INSTALLED="$(gh secret list --repo "$REPO" --json name --jq '.[].name' 2>/dev/null \
  || gh secret list --repo "$REPO" 2>/dev/null || true)"
INSTALLED="$(printf '%s\n' "$INSTALLED" | awk 'NF {print $1}')"

MISSING=0
for name in "${SECRET_NAMES[@]}"; do
  # Substring match on a newline-delimited list, rather than `printf | grep -q`:
  # grep -q closes the pipe on its first match, and under pipefail that turns a
  # successful find into a failed test.
  if case $'\n'"$INSTALLED"$'\n' in *$'\n'"$name"$'\n'*) true ;; *) false ;; esac; then
    echo "  confirmed $name"
  else
    echo "  NOT FOUND $name"
    MISSING=$((MISSING + 1))
  fi
done
[ "$MISSING" -eq 0 ] || fail "$MISSING secret(s) are not at repository scope on $REPO.
If you added any of these by hand in the web UI, check they are under
Settings > Secrets and variables > Actions > Repository secrets - not
Environment secrets, and not Dependabot secrets."

echo
echo "=============================================================="
echo " Secrets installed and verified. Store these NOW - they cannot"
echo " be read back out of GitHub, and the keystore cannot be"
echo " regenerated."
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

# Kick the build now rather than telling the reader to invent a commit.
if gh workflow run build-ror-colreg.yml --repo "$REPO" >/dev/null 2>&1; then
  echo "Triggered a build. The signed .aab appears in a minute or two:"
  echo "  gh run watch --repo $REPO"
  echo "  https://github.com/$REPO/actions"
else
  echo "Next: run the 'Build ROR COLREG APK' workflow from the Actions tab."
fi
echo "It publishes under a 'ror-visual-deck-release-*' tag. That is the Play upload."
