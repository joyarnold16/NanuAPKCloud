#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('nanu-local-ai')
WORKFLOW = Path('.github/workflows/build-nanu-play-release.yml')

errors: list[str] = []

required_files = [
    ROOT / 'app/AiReportClient.kt',
    ROOT / 'app/SafetyGuard.kt',
    ROOT / 'app/SafetyPrivacyActivity.kt',
    ROOT / 'ci/build_play_release.sh',
    ROOT / 'ci/verify_16k_native.py',
    ROOT / 'deployment/nanu_ai_report_backend.gs',
    ROOT / 'deployment/README_AI_REPORT_BACKEND.md',
    ROOT / 'docs/PLAY_CONSOLE_SUBMISSION_DRAFT.md',
    WORKFLOW,
]
for path in required_files:
    if not path.exists() or path.stat().st_size < 80:
        errors.append(f'missing/empty Play release file: {path}')

workflow = WORKFLOW.read_text(errors='ignore') if WORKFLOW.exists() else ''
for marker in [
    'Build Nanu 1.0 Play Release',
    'workflow_dispatch:',
    'report_endpoint:',
    'support_email:',
    'NANU_UPLOAD_KEYSTORE_BASE64',
    'platforms;android-36',
    'preflight_play_release.py',
    'nanu-local-ai-v1.0-play-release.aab',
    'nanu-upload-certificate.pem',
]:
    if marker not in workflow:
        errors.append(f'Play workflow missing marker: {marker}')

build = (ROOT / 'ci/build_play_release.sh').read_text(errors='ignore')
for marker in [
    'NANU_REPORT_ENDPOINT',
    'NANU_SUPPORT_EMAIL',
    'NANU_UPLOAD_KEYSTORE_BASE64',
    'versionCode = 100',
    'versionName = "1.0"',
    ':app:bundleRelease',
    'jarsigner -verify -strict',
    'verify_16k_native.py',
    'nanu-upload-certificate.pem',
    'PLAY_RELEASE_SHA256.txt',
    'rm -f "$KEYSTORE"',
]:
    if marker not in build:
        errors.append(f'Play build script missing marker: {marker}')

report = (ROOT / 'app/AiReportClient.kt').read_text(errors='ignore')
for marker in [
    'startsWith("https://")',
    'requestMethod = "POST"',
    'report_id',
    'details',
    'nanu_report_endpoint',
]:
    if marker not in report:
        errors.append(f'AI reporting client missing marker: {marker}')

safety = (ROOT / 'app/SafetyGuard.kt').read_text(errors='ignore')
for marker in ['blockedReason', 'SYSTEM_RULES', 'self-harm', 'phishing', 'sexual or nude']:
    if marker not in safety:
        errors.append(f'AI safety guard missing marker: {marker}')

privacy = (ROOT / 'app/SafetyPrivacyActivity.kt').read_text(errors='ignore')
for marker in ['Submit to developer', 'submitReport()', 'AiReportClient']:
    if marker not in privacy:
        errors.append(f'in-app report screen missing marker: {marker}')

verifier = (ROOT / 'ci/verify_16k_native.py').read_text(errors='ignore')
for marker in ['MIN_ALIGN = 0x4000', 'readelf', '.aab']:
    if marker not in verifier:
        errors.append(f'16 KB verifier missing marker: {marker}')

strings = (ROOT / 'strings.xml').read_text(errors='ignore')
for marker in ['nanu_report_endpoint', 'nanu_support_email']:
    if marker not in strings:
        errors.append(f'production-config string missing: {marker}')

if errors:
    print('PLAY RELEASE PREFLIGHT FAILED')
    for error in errors:
        print(' -', error)
    raise SystemExit(1)

print('Nanu Play release static preflight passed.')
print(' - release signing path is isolated from the public repository')
print(' - version 1.0 release AAB path is configured')
print(' - AI reporting and local safety guardrails are present')
print(' - API 36 and 16 KB native compatibility checks are configured')
print(' - upload certificate + release digest will be exported')
