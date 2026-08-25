#!/usr/bin/env python3
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path('nanu-local-ai')
WORKFLOW = Path('.github/workflows/build-nanu-local-ai-branch.yml')
CHECKER = ROOT / 'ci/preflight_rc8.py'

required = [
    'app/MainActivity.kt', 'app/MessageAdapter.kt', 'app/AttachmentManager.kt',
    'app/LocalImageGenerator.kt', 'app/ModelCatalog.kt', 'app/ModelDownloadManager.kt',
    'app/NanuBaseActivity.kt', 'app/TalkActivity.kt', 'app/CreateActivity.kt',
    'app/TradingActivity.kt', 'app/TradingEngine.kt', 'app/MarketSnapshotClient.kt',
    'app/ImageModelManager.kt', 'app/Rc8HomeActivity.kt', 'app/FileChatActivity.kt',
    'app/ContinuousTalkActivity.kt', 'app/CreateStudioActivity.kt',
    'app/PaperTradingActivity.kt', 'app/SafetyPrivacyActivity.kt',
    'res/layout/activity_main.xml', 'res/layout/activity_talk.xml',
    'res/layout/activity_create.xml', 'res/layout/activity_trading.xml',
    'res/layout/activity_rc8_home.xml', 'res/layout/activity_file_chat.xml',
    'res/layout/activity_talk_rc8.xml', 'res/layout/activity_create_studio.xml',
    'res/layout/activity_paper_trading.xml', 'res/layout/activity_safety_privacy.xml',
    'res/layout/sheet_plus_menu.xml', 'res/layout/item_message_assistant.xml',
    'res/layout/item_message_user.xml', 'res/xml/nanu_file_paths.xml',
    'res/drawable/ic_nanu_launcher.xml', 'strings.xml', 'ci/build_rc8.sh',
    'docs/PLAY_STORE_READINESS_RC8.md', 'docs/DATA_SAFETY_RC8.md'
]

errors = []
for rel in required:
    path = ROOT / rel
    if not path.exists() or path.stat().st_size <= 40:
        errors.append(f'missing or empty: {path}')

merge_markers = ('<' * 7, '=' * 7, '>' * 7)
for rel in required:
    path = ROOT / rel
    if not path.exists() or path.suffix.lower() in {'.png', '.jpg', '.jpeg'}:
        continue
    text = path.read_text(errors='ignore')
    for marker in merge_markers:
        if marker in text:
            errors.append(f'merge-conflict marker in {path}')

for xml in [p for p in (ROOT / 'res/layout').glob('*.xml')] + [ROOT / 'res/xml/nanu_file_paths.xml', ROOT / 'res/drawable/ic_nanu_launcher.xml']:
    try:
        ET.parse(xml)
    except Exception as exc:
        errors.append(f'XML parse failed: {xml}: {exc}')

build = (ROOT / 'ci/build_rc8.sh').read_text() if (ROOT / 'ci/build_rc8.sh').exists() else ''
for marker in [
    'versionCode = 22', 'versionName = "1.0-rc8"',
    'Rc8HomeActivity.kt', 'FileChatActivity.kt', 'ContinuousTalkActivity.kt',
    'CreateStudioActivity.kt', 'PaperTradingActivity.kt', 'SafetyPrivacyActivity.kt',
    'android:allowBackup=\\"false\\"', '-dontwarn com.gemalto.jp2.**',
    'out/nanu-local-ai-v1.0-rc8.apk'
]:
    if marker not in build:
        errors.append(f'build_rc8.sh missing marker: {marker}')

workflow = WORKFLOW.read_text() if WORKFLOW.exists() else ''
for marker in [
    'platforms;android-36', 'build_rc8.sh', 'preflight_rc8.py',
    'nanu-local-ai-v1.0-rc8'
]:
    if marker not in workflow:
        errors.append(f'RC8 branch workflow missing marker: {marker}')

home = (ROOT / 'app/Rc8HomeActivity.kt').read_text() if (ROOT / 'app/Rc8HomeActivity.kt').exists() else ''
for marker in ['MainActivity::class.java', 'ContinuousTalkActivity::class.java', 'FileChatActivity::class.java', 'CreateStudioActivity::class.java', 'TradingActivity::class.java', 'PaperTradingActivity::class.java', 'SafetyPrivacyActivity::class.java']:
    if marker not in home:
        errors.append(f'RC8 home missing destination: {marker}')

safety = (ROOT / 'app/SafetyPrivacyActivity.kt').read_text() if (ROOT / 'app/SafetyPrivacyActivity.kt').exists() else ''
for marker in ['PRIVACY_POLICY.md', 'TERMS_OF_USE.md', 'saveReport()', 'Export safety report']:
    if marker not in safety:
        errors.append(f'safety/privacy activity missing marker: {marker}')

paper = (ROOT / 'app/PaperTradingActivity.kt').read_text() if (ROOT / 'app/PaperTradingActivity.kt').exists() else ''
for marker in ['No real trade was placed', 'Virtual balance', 'KEY_POSITIONS', 'KEY_HISTORY']:
    if marker not in paper:
        errors.append(f'paper trading missing safety marker: {marker}')

# Play-oriented permission guardrails. Exclude this checker itself so its own
# blocklist is not mistaken for an app permission declaration.
scan_files = [
    p for p in ROOT.rglob('*')
    if p.is_file()
    and p.suffix.lower() in {'.kt', '.xml', '.sh', '.py'}
    and p != CHECKER
]
source_text = '\n'.join(p.read_text(errors='ignore') for p in scan_files)

forbidden_permissions = [
    'MANAGE_EXTERNAL_STORAGE', 'READ_EXTERNAL_STORAGE', 'WRITE_EXTERNAL_STORAGE',
    'READ_CONTACTS', 'WRITE_CONTACTS', 'ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION',
    'QUERY_ALL_PACKAGES', 'READ_SMS', 'RECEIVE_SMS', 'READ_CALL_LOG', 'SYSTEM_ALERT_WINDOW'
]
for permission in forbidden_permissions:
    if permission in source_text:
        errors.append(f'forbidden/unnecessary sensitive permission marker found: {permission}')

# RC8 should not contain an execution/wallet integration.
for risky_marker in ['PRIVATE_KEY', 'seed phrase', 'walletConnect', 'sendTransaction(', 'placeRealTrade(', 'binary options']:
    if risky_marker.lower() in source_text.lower():
        errors.append(f'RC8 contains disallowed/unwanted real-money execution marker: {risky_marker}')

if re.search(r'\bTODO\s*\(\s*"fatal', source_text, re.IGNORECASE):
    errors.append('fatal TODO found')

if errors:
    print('RC8 PREFLIGHT FAILED')
    for error in errors:
        print(' -', error)
    raise SystemExit(1)

print('RC8 static preflight passed.')
print(' - feature files present and XML parses')
print(' - Play-sensitive permission guardrails passed')
print(' - privacy/safety/paper-trading markers passed')
print(' - Android API 36 workflow + RC8 artifact markers passed')
