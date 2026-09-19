#!/usr/bin/env bash
set -euo pipefail

required_env=(
  NANU_REPORT_ENDPOINT
  NANU_SUPPORT_EMAIL
  NANU_PLAY_PUBLIC_KEY
  NANU_UPLOAD_KEYSTORE_BASE64
  NANU_UPLOAD_STORE_PASSWORD
  NANU_UPLOAD_KEY_ALIAS
  NANU_UPLOAD_KEY_PASSWORD
)
for name in "${required_env[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required Play release secret/configuration: $name" >&2
    exit 2
  fi
done

if [[ "$NANU_REPORT_ENDPOINT" != https://* ]]; then
  echo "NANU_REPORT_ENDPOINT must use HTTPS" >&2
  exit 2
fi
if [[ "$NANU_SUPPORT_EMAIL" != *@*.* ]]; then
  echo "NANU_SUPPORT_EMAIL does not look like a valid support email" >&2
  exit 2
fi

# Inject production-only public configuration into this CI checkout. No secret
# signing material is committed to the repository.
python3 <<'PY'
from pathlib import Path
import os
import xml.etree.ElementTree as ET

path = Path('nanu-local-ai/strings.xml')
tree = ET.parse(path)
root = tree.getroot()
values = {
    'nanu_report_endpoint': os.environ['NANU_REPORT_ENDPOINT'].strip(),
    'nanu_support_email': os.environ['NANU_SUPPORT_EMAIL'].strip(),
    'nanu_play_public_key': os.environ['NANU_PLAY_PUBLIC_KEY'].strip(),
}
for name, value in values.items():
    node = next((x for x in root.findall('string') if x.attrib.get('name') == name), None)
    if node is None:
        raise SystemExit(f'Missing string resource: {name}')
    node.text = value
ET.indent(tree, space='    ')
tree.write(path, encoding='unicode')

verify = ET.parse(path).getroot()
resolved = {x.attrib.get('name'): (x.text or '').strip() for x in verify.findall('string')}
if not resolved.get('nanu_report_endpoint', '').startswith('https://'):
    raise SystemExit('Injected report endpoint is not HTTPS')
if '@' not in resolved.get('nanu_support_email', ''):
    raise SystemExit('Injected support email is invalid')
PY

bash nanu-local-ai/ci/build_rc8.sh

ANDROID_PROJECT="llama-upstream/examples/llama.android"
KEYSTORE="$(pwd)/$ANDROID_PROJECT/nanu-upload.jks"
trap 'rm -f "$KEYSTORE"' EXIT
umask 077

printf '%s' "$NANU_UPLOAD_KEYSTORE_BASE64" | base64 --decode > "$KEYSTORE"
chmod 600 "$KEYSTORE"
test -s "$KEYSTORE"
export NANU_UPLOAD_KEYSTORE_PATH="$KEYSTORE"

keytool -list \
  -keystore "$KEYSTORE" \
  -storepass "$NANU_UPLOAD_STORE_PASSWORD" \
  -alias "$NANU_UPLOAD_KEY_ALIAS" >/dev/null

python3 <<'PY'
from pathlib import Path

path = Path('llama-upstream/examples/llama.android/app/build.gradle.kts')
text = path.read_text()
text = text.replace('versionCode = 27', 'versionCode = 100', 1)
text = text.replace('versionName = "1.0-final-test"', 'versionName = "1.0"', 1)

if 'signingConfigs {' not in text:
    anchor = '    buildTypes {'
    signing = '''    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("NANU_UPLOAD_KEYSTORE_PATH"))
            storePassword = System.getenv("NANU_UPLOAD_STORE_PASSWORD")
            keyAlias = System.getenv("NANU_UPLOAD_KEY_ALIAS")
            keyPassword = System.getenv("NANU_UPLOAD_KEY_PASSWORD")
        }
    }

'''
    if anchor not in text:
        raise SystemExit('Could not locate Gradle buildTypes block')
    text = text.replace(anchor, signing + anchor, 1)

release_anchor = '''        release {
            isMinifyEnabled = true'''
release_new = '''        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true'''
if release_anchor not in text:
    raise SystemExit('Could not locate Gradle release block')
text = text.replace(release_anchor, release_new, 1)

for marker in [
    'versionCode = 100',
    'versionName = "1.0"',
    'create("release")',
    'signingConfig = signingConfigs.getByName("release")',
]:
    if marker not in text:
        raise SystemExit(f'Play Gradle patch missing marker: {marker}')
path.write_text(text)
PY

(
  cd "$ANDROID_PROJECT"
  chmod +x gradlew
  ./gradlew --no-daemon :app:lintRelease :app:assembleRelease :app:bundleRelease --stacktrace
)

mkdir -p out
PLAY_APK="out/nanu-local-ai-v1.0-release.apk"
PLAY_AAB="out/nanu-local-ai-v1.0-play-release.aab"
cp "$ANDROID_PROJECT/app/build/outputs/apk/release/app-release.apk" "$PLAY_APK"
cp "$ANDROID_PROJECT/app/build/outputs/bundle/release/app-release.aab" "$PLAY_AAB"
test -s "$PLAY_APK"
test -s "$PLAY_AAB"
cp "$ANDROID_PROJECT/app/build/reports/lint-results-release.html" out/PLAY_LINT_REPORT.html
test -s out/PLAY_LINT_REPORT.html

# Verify JAR/AAB signature integrity. Android upload keys are normally
# self-signed certificates, so -strict would incorrectly fail solely because
# the certificate chain is not rooted in a public CA.
jarsigner -verify "$PLAY_AAB" >/dev/null

BUILD_TOOLS="${ANDROID_HOME:-$ANDROID_SDK_ROOT}/build-tools/36.0.0"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$PLAY_APK" > out/PLAY_APK_SIGNATURE.txt
"$BUILD_TOOLS/zipalign" -c -P 16 -v 4 "$PLAY_APK" > out/PLAY_APK_ALIGNMENT.txt

python3 nanu-local-ai/ci/verify_no_trading_artifact.py "$PLAY_APK" "$PLAY_AAB"
python3 nanu-local-ai/ci/verify_16k_native.py "$PLAY_APK"
python3 nanu-local-ai/ci/verify_16k_native.py "$PLAY_AAB"

keytool -exportcert -rfc \
  -alias "$NANU_UPLOAD_KEY_ALIAS" \
  -keystore "$KEYSTORE" \
  -storepass "$NANU_UPLOAD_STORE_PASSWORD" \
  -file out/nanu-upload-certificate.pem >/dev/null

{
  echo "Nanu Local AI 1.0 upload certificate"
  keytool -list -v \
    -alias "$NANU_UPLOAD_KEY_ALIAS" \
    -keystore "$KEYSTORE" \
    -storepass "$NANU_UPLOAD_STORE_PASSWORD" \
    | grep -E 'Alias name:|Valid from:|SHA1:|SHA256:' || true
} > out/UPLOAD_CERTIFICATE_INFO.txt

test -s out/nanu-upload-certificate.pem
test -s out/UPLOAD_CERTIFICATE_INFO.txt

python3 <<'PY'
from pathlib import Path
import hashlib

artifacts = [
    Path('out/nanu-local-ai-v1.0-release.apk'),
    Path('out/nanu-local-ai-v1.0-play-release.aab'),
]
lines = []
for path in artifacts:
    if path.stat().st_size <= 1_000_000:
        raise SystemExit(f'Play artifact is suspiciously small: {path}')
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    lines.append(f'{digest}  {path.name}')
    print(f'Play artifact ready: {path} ({path.stat().st_size} bytes)')
    print(f'SHA256: {digest}')
Path('out/PLAY_RELEASE_SHA256.txt').write_text('\n'.join(lines) + '\n')
PY

rm -f "$KEYSTORE"
