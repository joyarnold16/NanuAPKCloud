#!/usr/bin/env bash
set -euo pipefail

python3 <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

required_files = [
    'nanu-local-ai/app/MainActivity.kt',
    'nanu-local-ai/app/MessageAdapter.kt',
    'nanu-local-ai/app/AttachmentManager.kt',
    'nanu-local-ai/app/LocalImageGenerator.kt',
    'nanu-local-ai/app/ModelCatalog.kt',
    'nanu-local-ai/app/ModelDownloadManager.kt',
    'nanu-local-ai/app/NanuBaseActivity.kt',
    'nanu-local-ai/app/TalkActivity.kt',
    'nanu-local-ai/app/CreateActivity.kt',
    'nanu-local-ai/app/TradingActivity.kt',
    'nanu-local-ai/app/TradingEngine.kt',
    'nanu-local-ai/app/MarketSnapshotClient.kt',
    'nanu-local-ai/app/ImageModelManager.kt',
    'nanu-local-ai/res/layout/activity_main.xml',
    'nanu-local-ai/res/layout/activity_talk.xml',
    'nanu-local-ai/res/layout/activity_create.xml',
    'nanu-local-ai/res/layout/activity_trading.xml',
    'nanu-local-ai/res/layout/sheet_plus_menu.xml',
    'nanu-local-ai/res/layout/item_message_assistant.xml',
    'nanu-local-ai/res/layout/item_message_user.xml',
    'nanu-local-ai/res/xml/nanu_file_paths.xml',
    'nanu-local-ai/res/drawable/ic_nanu_launcher.xml',
    'nanu-local-ai/strings.xml',
]
for path in required_files:
    p = Path(path)
    if not p.exists() or p.stat().st_size <= 50:
        raise SystemExit(f'Missing or empty required source file: {path}')

main = Path('nanu-local-ai/app/MainActivity.kt').read_text()
for marker in [
    'showPlusMenu()',
    'Tap to talk',
    'AssistantMode.TRADING',
    'AssistantMode.IMAGE',
    'openAttachmentDocument',
    'generateImageInChat',
    'createVisualPrompt',
    'FileProvider.getUriForFile',
]:
    if marker not in main:
        raise SystemExit(f'MainActivity is missing RC6 marker: {marker}')

layout_main = Path('nanu-local-ai/res/layout/activity_main.xml').read_text()
for legacy_handler in ['openTalk(', 'openCreate(', 'openTrading(']:
    if legacy_handler in layout_main:
        raise SystemExit(f'Legacy click handler still present: {legacy_handler}')

for xml in [
    'nanu-local-ai/res/layout/activity_main.xml',
    'nanu-local-ai/res/layout/activity_talk.xml',
    'nanu-local-ai/res/layout/activity_create.xml',
    'nanu-local-ai/res/layout/activity_trading.xml',
    'nanu-local-ai/res/layout/sheet_plus_menu.xml',
    'nanu-local-ai/res/layout/item_message_assistant.xml',
    'nanu-local-ai/res/layout/item_message_user.xml',
    'nanu-local-ai/res/xml/nanu_file_paths.xml',
    'nanu-local-ai/res/drawable/ic_nanu_launcher.xml',
]:
    ET.parse(xml)

base_path = Path('nanu-local-ai/ci/build_rc5.sh')
base = base_path.read_text()

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'RC6 patch anchor {label!r} expected exactly once, found {count}')
    return text.replace(old, new, 1)

base = replace_once(base, 'versionCode = 17', 'versionCode = 19', 'versionCode')
base = replace_once(base, 'versionName = "1.0-rc5.2"', 'versionName = "1.0-rc6"', 'versionName')
base = base.replace('RC5.2', 'RC6').replace('rc5.2', 'rc6')
base = replace_once(
    base,
    'mkdir -p "$APP/res/drawable" "$NATIVE_DIR"',
    'mkdir -p "$APP/res/drawable" "$APP/res/xml" "$NATIVE_DIR"',
    'res/xml directory'
)
base = replace_once(
    base,
    'cp nanu-local-ai/res/layout/activity_create.xml "$APP/res/layout/activity_create.xml"',
    'cp nanu-local-ai/res/layout/activity_create.xml "$APP/res/layout/activity_create.xml"\ncp nanu-local-ai/res/layout/sheet_plus_menu.xml "$APP/res/layout/sheet_plus_menu.xml"',
    'plus menu copy'
)
base = replace_once(
    base,
    'cp nanu-local-ai/res/drawable/ic_nanu_launcher.xml "$APP/res/drawable/ic_nanu_launcher.xml"',
    'cp nanu-local-ai/res/drawable/ic_nanu_launcher.xml "$APP/res/drawable/ic_nanu_launcher.xml"\ncp nanu-local-ai/res/xml/nanu_file_paths.xml "$APP/res/xml/nanu_file_paths.xml"',
    'FileProvider paths copy'
)
base = replace_once(
    base,
    '  ImageModelManager.kt; do',
    '  ImageModelManager.kt \\\n  AttachmentManager.kt \\\n  LocalImageGenerator.kt; do',
    'RC6 Kotlin source list'
)

old_gradle = "app_gradle.write_text(text)"
new_gradle = '''if 'pdfbox-android' not in text:
    text = text.replace('dependencies {', 'dependencies {\\n    implementation("com.tom-roush:pdfbox-android:2.0.27.0")', 1)
app_gradle.write_text(text)'''
base = replace_once(base, old_gradle, new_gradle, 'pdfbox dependency')

old_manifest = 'manifest.write_text(text)'
new_manifest = """provider = '''
        <provider
            android:name=\"androidx.core.content.FileProvider\"
            android:authorities=\"${applicationId}.files\"
            android:exported=\"false\"
            android:grantUriPermissions=\"true\">
            <meta-data
                android:name=\"android.support.FILE_PROVIDER_PATHS\"
                android:resource=\"@xml/nanu_file_paths\" />
        </provider>
'''
if 'androidx.core.content.FileProvider' not in text:
    text = text.replace('</application>', provider + '\\n    </application>', 1)
manifest.write_text(text)"""
base = replace_once(base, old_manifest, new_manifest, 'FileProvider manifest injection')

# RC6 owns the top-right model chooser. Disable the older RC5 task-chooser
# patch so the checked-in MainActivity remains the source of truth.
base = replace_once(
    base,
    "pattern = r'    private fun showRecommendedModelCatalog\\(ramGb: Double\\) \\{.*?\\n    \\}\\n\\n    private fun showModelSuggestionDetail'",
    "pattern = r'__RC6_DO_NOT_PATCH_MODEL_CATALOG__'",
    'legacy model-catalog patch pattern'
)
base = replace_once(
    base,
    "if count != 1:\n    raise SystemExit('Could not patch recommended model catalog dialog')",
    "if count not in (0, 1):\n    raise SystemExit('Unexpected RC6 model-catalog patch count')",
    'legacy model-catalog patch guard'
)
base = replace_once(base, "assert 'Choose a task' in main", "assert 'showPlusMenu' in main", 'main validation 1')
base = replace_once(base, "assert 'showRecommendedModelsForTask' in main", "assert 'AssistantMode.IMAGE' in main", 'main validation 2')
base = base.replace("print('RC5.2 source validation passed.')", "print('RC6 unified source validation passed.')")
base = base.replace("print('RC5.2 packaged native runtime validation passed.')", "print('RC6 packaged native runtime validation passed.')")

for required in [
    'versionCode = 19',
    'versionName = "1.0-rc6"',
    'pdfbox-android:2.0.27.0',
    'AttachmentManager.kt',
    'LocalImageGenerator.kt',
    'nanu_file_paths.xml',
    'androidx.core.content.FileProvider',
    'out/nanu-local-ai-v1.0-rc6.apk',
]:
    if required not in base:
        raise SystemExit(f'Generated RC6 build script is missing: {required}')

out = Path('/tmp/build_nanu_rc6.sh')
out.write_text(base)
print('RC6 build script generation/patch validation passed.')
PY

chmod +x /tmp/build_nanu_rc6.sh
bash -n /tmp/build_nanu_rc6.sh
bash /tmp/build_nanu_rc6.sh

python3 <<'PY'
from pathlib import Path
from zipfile import ZipFile

apk = Path('out/nanu-local-ai-v1.0-rc6.apk')
aab = Path('out/nanu-local-ai-v1.0-rc6-debug.aab')
for artifact in [apk, aab]:
    if not artifact.exists() or artifact.stat().st_size <= 1_000_000:
        raise SystemExit(f'Missing or suspiciously small artifact: {artifact}')

with ZipFile(apk) as z:
    names = set(z.namelist())
for required in [
    'lib/arm64-v8a/libsd.so',
    'lib/arm64-v8a/libc++_shared.so',
]:
    if required not in names:
        raise SystemExit(f'APK missing native runtime: {required}')
print('RC6 APK/native artifact validation passed.')
PY
