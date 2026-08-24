from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path('nanu-local-ai')

REQUIRED = [
    ROOT / 'app/MainActivity.kt',
    ROOT / 'app/MessageAdapter.kt',
    ROOT / 'app/AttachmentManager.kt',
    ROOT / 'app/LocalImageGenerator.kt',
    ROOT / 'app/ModelCatalog.kt',
    ROOT / 'app/ModelDownloadManager.kt',
    ROOT / 'app/NanuBaseActivity.kt',
    ROOT / 'app/TalkActivity.kt',
    ROOT / 'app/CreateActivity.kt',
    ROOT / 'app/TradingActivity.kt',
    ROOT / 'app/TradingEngine.kt',
    ROOT / 'app/MarketSnapshotClient.kt',
    ROOT / 'app/ImageModelManager.kt',
    ROOT / 'res/layout/activity_main.xml',
    ROOT / 'res/layout/activity_talk.xml',
    ROOT / 'res/layout/activity_create.xml',
    ROOT / 'res/layout/activity_trading.xml',
    ROOT / 'res/layout/sheet_plus_menu.xml',
    ROOT / 'res/layout/item_message_assistant.xml',
    ROOT / 'res/layout/item_message_user.xml',
    ROOT / 'res/xml/nanu_file_paths.xml',
    ROOT / 'res/drawable/ic_nanu_launcher.xml',
    ROOT / 'res/values/colors.xml',
    ROOT / 'strings.xml',
    ROOT / 'ci/build_rc5.sh',
    ROOT / 'ci/build_rc6.sh',
]

errors = []


def fail(message: str) -> None:
    errors.append(message)


for path in REQUIRED:
    if not path.exists():
        fail(f'missing: {path}')
    elif path.stat().st_size < 20:
        fail(f'empty/suspiciously small: {path}')

# Construct conflict markers dynamically so this checker does not flag its own
# source merely for containing the strings it is supposed to search for.
merge_markers = ('<' * 7, '=' * 7, '>' * 7)
for path in ROOT.rglob('*'):
    if not path.is_file() or path.suffix not in {'.kt', '.xml', '.sh', '.py'}:
        continue
    text = path.read_text(errors='replace')
    for marker in merge_markers:
        if marker in text:
            fail(f'merge-conflict marker {marker!r} in {path}')

xml_files = list((ROOT / 'res').rglob('*.xml'))
for path in xml_files:
    try:
        ET.parse(path)
    except Exception as exc:
        fail(f'XML parse failed for {path}: {exc}')

checks = {
    ROOT / 'app/MainActivity.kt': [
        'showPlusMenu()',
        'Tap to talk',
        'AssistantMode.TRADING',
        'AssistantMode.IMAGE',
        'openAttachmentDocument',
        'generateImageInChat',
        'createVisualPrompt',
        'FileProvider.getUriForFile',
    ],
    ROOT / 'app/TalkActivity.kt': [
        'TextToSpeech',
        'isLanguageAvailable',
    ],
    ROOT / 'app/ModelDownloadManager.kt': [
        'download',
    ],
    ROOT / 'app/LocalImageGenerator.kt': [
        'generate',
    ],
    ROOT / 'app/TradingActivity.kt': [
        'Trading',
    ],
}
for path, markers in checks.items():
    if not path.exists():
        continue
    text = path.read_text()
    for marker in markers:
        if marker not in text:
            fail(f'{path} missing expected marker: {marker}')

main_layout = (ROOT / 'res/layout/activity_main.xml').read_text() if (ROOT / 'res/layout/activity_main.xml').exists() else ''
for handler in ('openTalk(', 'openCreate(', 'openTrading('):
    if handler in main_layout:
        fail(f'legacy XML onClick handler remains in activity_main.xml: {handler}')

file_paths = ROOT / 'res/xml/nanu_file_paths.xml'
if file_paths.exists():
    fp = file_paths.read_text()
    if 'files-path' not in fp and 'cache-path' not in fp and 'external-files-path' not in fp:
        fail('nanu_file_paths.xml does not expose an app-owned path')

build5 = ROOT / 'ci/build_rc5.sh'
if build5.exists():
    text = build5.read_text()
    anchors = [
        'versionCode = 17',
        'versionName = "1.0-rc5.2"',
        'mkdir -p "$APP/res/drawable" "$NATIVE_DIR"',
        'ImageModelManager.kt; do',
        'app_gradle.write_text(text)',
        'manifest.write_text(text)',
        "assert 'Choose a task' in main",
        "assert 'showRecommendedModelsForTask' in main",
    ]
    for anchor in anchors:
        count = text.count(anchor)
        if count != 1:
            fail(f'build_rc5.sh anchor expected once but found {count}: {anchor}')

build6 = ROOT / 'ci/build_rc6.sh'
if build6.exists():
    text = build6.read_text()
    for marker in [
        'replace_once',
        'versionCode = 19',
        'versionName = "1.0-rc6"',
        'pdfbox-android:2.0.27.0',
        'androidx.core.content.FileProvider',
        'nanu-local-ai-v1.0-rc6.apk',
    ]:
        if marker not in text:
            fail(f'build_rc6.sh missing safety/build marker: {marker}')

# Keep only source checks that are safe without a full Kotlin lexer. A raw
# count of block-comment delimiters creates false positives when delimiters
# appear inside string literals; the Kotlin compiler is authoritative for
# syntax and will run immediately after this preflight.
for path in (ROOT / 'app').glob('*.kt'):
    text = path.read_text()
    if re.search(r'\bTODO\s*\(\s*"fatal', text, re.IGNORECASE):
        fail(f'fatal TODO marker in {path}')

if errors:
    print('RC6 PREFLIGHT FAILED')
    for item in errors:
        print(f' - {item}')
    sys.exit(1)

print(f'RC6 preflight passed: {len(REQUIRED)} required files, {len(xml_files)} XML files, build patch anchors validated.')
