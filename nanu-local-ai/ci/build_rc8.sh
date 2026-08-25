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
    'nanu-local-ai/app/Rc8HomeActivity.kt',
    'nanu-local-ai/app/FileChatActivity.kt',
    'nanu-local-ai/app/ContinuousTalkActivity.kt',
    'nanu-local-ai/app/CreateStudioActivity.kt',
    'nanu-local-ai/app/PaperTradingActivity.kt',
    'nanu-local-ai/app/SafetyPrivacyActivity.kt',
    'nanu-local-ai/res/layout/activity_main.xml',
    'nanu-local-ai/res/layout/activity_talk.xml',
    'nanu-local-ai/res/layout/activity_create.xml',
    'nanu-local-ai/res/layout/activity_trading.xml',
    'nanu-local-ai/res/layout/activity_rc8_home.xml',
    'nanu-local-ai/res/layout/activity_file_chat.xml',
    'nanu-local-ai/res/layout/activity_talk_rc8.xml',
    'nanu-local-ai/res/layout/activity_create_studio.xml',
    'nanu-local-ai/res/layout/activity_paper_trading.xml',
    'nanu-local-ai/res/layout/activity_safety_privacy.xml',
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
        raise SystemExit(f'Missing or empty required RC8 source file: {path}')

for xml in [p for p in Path('nanu-local-ai/res/layout').glob('*.xml')] + [
    Path('nanu-local-ai/res/xml/nanu_file_paths.xml'),
    Path('nanu-local-ai/res/drawable/ic_nanu_launcher.xml'),
]:
    ET.parse(xml)

base_path = Path('nanu-local-ai/ci/build_rc5.sh')
base = base_path.read_text()

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'RC8 patch anchor {label!r} expected exactly once, found {count}')
    return text.replace(old, new, 1)

base = replace_once(base, 'versionCode = 17', 'versionCode = 22', 'versionCode')
base = replace_once(base, 'versionName = "1.0-rc5.2"', 'versionName = "1.0-rc8"', 'versionName')
base = base.replace('RC5.2', 'RC8').replace('rc5.2', 'rc8')

base = replace_once(
    base,
    'mkdir -p "$APP/res/drawable" "$NATIVE_DIR"',
    'mkdir -p "$APP/res/drawable" "$APP/res/xml" "$NATIVE_DIR"',
    'res/xml directory'
)

base = replace_once(
    base,
    'cp nanu-local-ai/res/layout/activity_create.xml "$APP/res/layout/activity_create.xml"',
    '''cp nanu-local-ai/res/layout/activity_create.xml "$APP/res/layout/activity_create.xml"
cp nanu-local-ai/res/layout/sheet_plus_menu.xml "$APP/res/layout/sheet_plus_menu.xml"
cp nanu-local-ai/res/layout/activity_rc8_home.xml "$APP/res/layout/activity_rc8_home.xml"
cp nanu-local-ai/res/layout/activity_file_chat.xml "$APP/res/layout/activity_file_chat.xml"
cp nanu-local-ai/res/layout/activity_talk_rc8.xml "$APP/res/layout/activity_talk_rc8.xml"
cp nanu-local-ai/res/layout/activity_create_studio.xml "$APP/res/layout/activity_create_studio.xml"
cp nanu-local-ai/res/layout/activity_paper_trading.xml "$APP/res/layout/activity_paper_trading.xml"
cp nanu-local-ai/res/layout/activity_safety_privacy.xml "$APP/res/layout/activity_safety_privacy.xml"''',
    'RC8 layout copies'
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
    '''  ImageModelManager.kt \\
  AttachmentManager.kt \\
  LocalImageGenerator.kt \\
  Rc8HomeActivity.kt \\
  FileChatActivity.kt \\
  ContinuousTalkActivity.kt \\
  CreateStudioActivity.kt \\
  PaperTradingActivity.kt \\
  SafetyPrivacyActivity.kt; do''',
    'RC8 Kotlin source list'
)

old_gradle = "app_gradle.write_text(text)"
new_gradle = '''if 'pdfbox-android' not in text:
    text = text.replace('dependencies {', 'dependencies {\\n    implementation("com.tom-roush:pdfbox-android:2.0.27.0")', 1)
app_gradle.write_text(text)

proguard = Path('llama-upstream/examples/llama.android/app/proguard-rules.pro')
proguard_text = proguard.read_text() if proguard.exists() else ''
r8_rule = '-dontwarn com.gemalto.jp2.**'
if r8_rule not in proguard_text:
    if proguard_text and not proguard_text.endswith('\\n'):
        proguard_text += '\\n'
    proguard_text += '\\n# Optional PDFBox JPEG-2000 decoder is not bundled.\\n' + r8_rule + '\\n'
    proguard.write_text(proguard_text)'''
base = replace_once(base, old_gradle, new_gradle, 'pdfbox dependency and R8 rule')

old_manifest = 'manifest.write_text(text)'
new_manifest = """# RC8 privacy hardening: local private data is not included in Android cloud backup,
# and all network traffic must use TLS.
text = text.replace('android:allowBackup=\"true\"', 'android:allowBackup=\"false\"')
if 'android:usesCleartextTraffic' not in text:
    text = text.replace('android:supportsRtl=\"true\"', 'android:supportsRtl=\"true\"\\n        android:usesCleartextTraffic=\"false\"', 1)

provider = '''
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

# MainActivity remains the proven RC6 chat/model engine, but RC8 Home becomes
# the single launcher. Secondary activities are intentionally not exported.
main_pattern = r'(<activity\\s+android:name=\"\\.MainActivity\"[^>]*>)(.*?)(</activity>)'
match = re.search(main_pattern, text, flags=re.S)
if not match:
    raise SystemExit('Could not locate MainActivity in generated manifest')
main_open = match.group(1).replace('android:exported=\"true\"', 'android:exported=\"false\"')
main_body = re.sub(r'\\s*<intent-filter>.*?</intent-filter>\\s*', '\\n', match.group(2), flags=re.S)
main_replacement = main_open + main_body + match.group(3)
text = text[:match.start()] + main_replacement + text[match.end():]

rc8_activities = '''
        <activity android:name=\".SafetyPrivacyActivity\" android:exported=\"false\" />
        <activity android:name=\".PaperTradingActivity\" android:exported=\"false\" />
        <activity android:name=\".CreateStudioActivity\" android:exported=\"false\" />
        <activity android:name=\".ContinuousTalkActivity\" android:exported=\"false\" />
        <activity android:name=\".FileChatActivity\" android:exported=\"false\" />
        <activity
            android:name=\".Rc8HomeActivity\"
            android:exported=\"true\">
            <intent-filter>
                <action android:name=\"android.intent.action.MAIN\" />
                <category android:name=\"android.intent.category.LAUNCHER\" />
            </intent-filter>
        </activity>
'''
if 'android:name=\".Rc8HomeActivity\"' not in text:
    text = text.replace('</application>', rc8_activities + '\\n    </application>', 1)
manifest.write_text(text)"""
base = replace_once(base, old_manifest, new_manifest, 'RC8 manifest injection')

# Keep the checked-in unified MainActivity as the source of truth instead of
# applying the old RC5 model-catalog regex patch.
base = replace_once(
    base,
    "pattern = r'    private fun showRecommendedModelCatalog\\(ramGb: Double\\) \\{.*?\\n    \\}\\n\\n    private fun showModelSuggestionDetail'",
    "pattern = r'__RC8_DO_NOT_PATCH_MODEL_CATALOG__'",
    'legacy model-catalog patch pattern'
)
base = replace_once(
    base,
    "if count != 1:\n    raise SystemExit('Could not patch recommended model catalog dialog')",
    "if count not in (0, 1):\n    raise SystemExit('Unexpected RC8 model-catalog patch count')",
    'legacy model-catalog patch guard'
)
base = replace_once(base, "assert 'Choose a task' in main", "assert 'showPlusMenu' in main", 'main validation 1')
base = replace_once(base, "assert 'showRecommendedModelsForTask' in main", "assert 'AssistantMode.IMAGE' in main", 'main validation 2')
base = base.replace("print('RC5.2 source validation passed.')", "print('RC8 unified source validation passed.')")
base = base.replace("print('RC5.2 packaged native runtime validation passed.')", "print('RC8 packaged native runtime validation passed.')")

for required in [
    'versionCode = 22', 'versionName = "1.0-rc8"', 'pdfbox-android:2.0.27.0',
    '-dontwarn com.gemalto.jp2.**', 'AttachmentManager.kt', 'LocalImageGenerator.kt',
    'Rc8HomeActivity.kt', 'FileChatActivity.kt', 'ContinuousTalkActivity.kt',
    'CreateStudioActivity.kt', 'PaperTradingActivity.kt', 'SafetyPrivacyActivity.kt',
    'activity_rc8_home.xml', 'activity_file_chat.xml', 'activity_talk_rc8.xml',
    'activity_create_studio.xml', 'activity_paper_trading.xml', 'activity_safety_privacy.xml',
    'android:allowBackup=\\"false\\"', 'androidx.core.content.FileProvider',
    'out/nanu-local-ai-v1.0-rc8.apk'
]:
    if required not in base:
        raise SystemExit(f'Generated RC8 build script is missing: {required}')

out = Path('/tmp/build_nanu_rc8.sh')
out.write_text(base)
print('RC8 build script generation/patch validation passed.')
PY

chmod +x /tmp/build_nanu_rc8.sh
bash -n /tmp/build_nanu_rc8.sh
bash /tmp/build_nanu_rc8.sh

python3 <<'PY'
from pathlib import Path
from zipfile import ZipFile

apk = Path('out/nanu-local-ai-v1.0-rc8.apk')
aab = Path('out/nanu-local-ai-v1.0-rc8-debug.aab')
for artifact in [apk, aab]:
    if not artifact.exists() or artifact.stat().st_size <= 1_000_000:
        raise SystemExit(f'Missing or suspiciously small RC8 artifact: {artifact}')

with ZipFile(apk) as z:
    names = set(z.namelist())
for required in ['lib/arm64-v8a/libsd.so', 'lib/arm64-v8a/libc++_shared.so']:
    if required not in names:
        raise SystemExit(f'RC8 APK missing native runtime: {required}')
print('RC8 APK/native artifact validation passed.')
PY
