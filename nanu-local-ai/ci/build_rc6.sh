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
    'nanu-local-ai/res/layout/activity_main.xml',
    'nanu-local-ai/res/layout/sheet_plus_menu.xml',
    'nanu-local-ai/res/layout/item_message_assistant.xml',
    'nanu-local-ai/res/layout/item_message_user.xml',
    'nanu-local-ai/res/xml/nanu_file_paths.xml',
]
for path in required_files:
    p = Path(path)
    assert p.exists() and p.stat().st_size > 50, path

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
    assert marker in main, marker
assert 'openTalk(' not in Path('nanu-local-ai/res/layout/activity_main.xml').read_text()
assert 'openCreate(' not in Path('nanu-local-ai/res/layout/activity_main.xml').read_text()
assert 'openTrading(' not in Path('nanu-local-ai/res/layout/activity_main.xml').read_text()

for xml in [
    'nanu-local-ai/res/layout/activity_main.xml',
    'nanu-local-ai/res/layout/sheet_plus_menu.xml',
    'nanu-local-ai/res/layout/item_message_assistant.xml',
    'nanu-local-ai/res/layout/item_message_user.xml',
    'nanu-local-ai/res/xml/nanu_file_paths.xml',
]:
    ET.parse(xml)

base = Path('nanu-local-ai/ci/build_rc5.sh').read_text()
base = base.replace('versionCode = 17', 'versionCode = 19')
base = base.replace('versionName = "1.0-rc5.2"', 'versionName = "1.0-rc6"')
base = base.replace('RC5.2', 'RC6')
base = base.replace('rc5.2', 'rc6')
base = base.replace(
    'mkdir -p "$APP/res/drawable" "$NATIVE_DIR"',
    'mkdir -p "$APP/res/drawable" "$APP/res/xml" "$NATIVE_DIR"'
)
base = base.replace(
    'cp nanu-local-ai/res/layout/activity_create.xml "$APP/res/layout/activity_create.xml"',
    'cp nanu-local-ai/res/layout/activity_create.xml "$APP/res/layout/activity_create.xml"\ncp nanu-local-ai/res/layout/sheet_plus_menu.xml "$APP/res/layout/sheet_plus_menu.xml"'
)
base = base.replace(
    'cp nanu-local-ai/res/drawable/ic_nanu_launcher.xml "$APP/res/drawable/ic_nanu_launcher.xml"',
    'cp nanu-local-ai/res/drawable/ic_nanu_launcher.xml "$APP/res/drawable/ic_nanu_launcher.xml"\ncp nanu-local-ai/res/xml/nanu_file_paths.xml "$APP/res/xml/nanu_file_paths.xml"'
)
base = base.replace(
    '  ImageModelManager.kt; do',
    '  ImageModelManager.kt \\\n  AttachmentManager.kt \\\n  LocalImageGenerator.kt; do'
)

old_gradle = "app_gradle.write_text(text)"
new_gradle = '''if 'pdfbox-android' not in text:
    text = text.replace('dependencies {', 'dependencies {\\n    implementation("com.tom-roush:pdfbox-android:2.0.27.0")', 1)
app_gradle.write_text(text)'''
base = base.replace(old_gradle, new_gradle, 1)

old_manifest = 'manifest.write_text(text)'
new_manifest = (
    "provider = '''\\n"
    "        <provider\\n"
    "            android:name=\\\"androidx.core.content.FileProvider\\\"\\n"
    "            android:authorities=\\\"${applicationId}.files\\\"\\n"
    "            android:exported=\\\"false\\\"\\n"
    "            android:grantUriPermissions=\\\"true\\\">\\n"
    "            <meta-data\\n"
    "                android:name=\\\"android.support.FILE_PROVIDER_PATHS\\\"\\n"
    "                android:resource=\\\"@xml/nanu_file_paths\\\" />\\n"
    "        </provider>\\n"
    "'''\\n"
    "if 'androidx.core.content.FileProvider' not in text:\\n"
    "    text = text.replace('</application>', provider + '\\\\n    </application>', 1)\\n"
    "manifest.write_text(text)"
)
base = base.replace(old_manifest, new_manifest, 1)

# Keep the RC6 top-right model chooser instead of injecting the older RC5
# task-chooser implementation into MainActivity.
base = base.replace(
    "pattern = r'    private fun showRecommendedModelCatalog\\(ramGb: Double\\) \\{.*?\\n    \\}\\n\\n    private fun showModelSuggestionDetail'",
    "pattern = r'__RC6_DO_NOT_PATCH_MODEL_CATALOG__'"
)
base = base.replace(
    "if count != 1:\n    raise SystemExit('Could not patch recommended model catalog dialog')",
    "if count == 1:\n    pass"
)
base = base.replace("assert 'Choose a task' in main", "assert 'showPlusMenu' in main")
base = base.replace("assert 'showRecommendedModelsForTask' in main", "assert 'AssistantMode.IMAGE' in main")
base = base.replace("print('RC5.2 source validation passed.')", "print('RC6 unified source validation passed.')")
base = base.replace("print('RC5.2 packaged native runtime validation passed.')", "print('RC6 packaged native runtime validation passed.')")

Path('/tmp/build_nanu_rc6.sh').write_text(base)
PY

chmod +x /tmp/build_nanu_rc6.sh
bash /tmp/build_nanu_rc6.sh

python3 <<'PY'
from pathlib import Path
from zipfile import ZipFile

apk = Path('out/nanu-local-ai-v1.0-rc6.apk')
assert apk.exists() and apk.stat().st_size > 1_000_000
with ZipFile(apk) as z:
    names = set(z.namelist())
for required in [
    'lib/arm64-v8a/libsd.so',
    'lib/arm64-v8a/libc++_shared.so',
]:
    assert required in names, required
print('RC6 APK native/runtime validation passed.')
PY
