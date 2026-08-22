#!/usr/bin/env bash
set -euo pipefail

LLAMA_COMMIT="9a286ac98d2cab74231bd3f1fc3f2b8bdf05422e"
SD_COMMIT="97d2990807fe6d558e395f8764198d7c7e7b411c"
NDK_VERSION="29.0.13113456"

rm -rf llama-upstream stable-diffusion-upstream sd-build out

git clone --filter=blob:none https://github.com/ggml-org/llama.cpp.git llama-upstream
(
  cd llama-upstream
  git checkout "$LLAMA_COMMIT"
)

git clone https://github.com/leejet/stable-diffusion.cpp.git stable-diffusion-upstream
(
  cd stable-diffusion-upstream
  git checkout "$SD_COMMIT"
  git submodule update --init --recursive
)

cmake -S stable-diffusion-upstream -B sd-build -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_SDK_ROOT/ndk/$NDK_VERSION/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-28 \
  -DANDROID_STL=c++_shared \
  -DGGML_OPENMP=OFF \
  -DSD_WEBP=OFF \
  -DSD_WEBM=OFF \
  -DSD_VULKAN=OFF \
  -DSD_OPENCL=OFF \
  -DSD_BUILD_EXAMPLES=ON \
  -DCMAKE_BUILD_TYPE=Release
cmake --build sd-build --target sd-cli -j2

test -s sd-build/bin/sd-cli

APP="llama-upstream/examples/llama.android/app/src/main"
JAVA="$APP/java/com/example/llama"
mkdir -p "$APP/res/drawable" "$APP/jniLibs/arm64-v8a"

cp nanu-local-ai/strings.xml "$APP/res/values/strings.xml"
cp nanu-local-ai/res/values/colors.xml "$APP/res/values/colors.xml"
cp nanu-local-ai/res/layout/activity_main.xml "$APP/res/layout/activity_main.xml"
cp nanu-local-ai/res/layout/activity_trading.xml "$APP/res/layout/activity_trading.xml"
cp nanu-local-ai/res/layout/activity_talk.xml "$APP/res/layout/activity_talk.xml"
cp nanu-local-ai/res/layout/activity_create.xml "$APP/res/layout/activity_create.xml"
cp nanu-local-ai/res/layout/item_message_assistant.xml "$APP/res/layout/item_message_assistant.xml"
cp nanu-local-ai/res/layout/item_message_user.xml "$APP/res/layout/item_message_user.xml"
cp nanu-local-ai/res/drawable/ic_nanu_launcher.xml "$APP/res/drawable/ic_nanu_launcher.xml"

for src in \
  MainActivity.kt \
  MessageAdapter.kt \
  ModelCatalog.kt \
  ModelDownloadManager.kt \
  NanuBaseActivity.kt \
  TradingActivity.kt \
  TradingEngine.kt \
  MarketSnapshotClient.kt \
  TalkActivity.kt \
  CreateActivity.kt \
  ImageModelManager.kt; do
  cp "nanu-local-ai/app/$src" "$JAVA/$src"
done

# Package the CLI under lib/ so Android installs it in the native library directory.
cp sd-build/bin/sd-cli "$APP/jniLibs/arm64-v8a/libsd.so"
chmod 755 "$APP/jniLibs/arm64-v8a/libsd.so"

python3 <<'PY'
from pathlib import Path
import re

app_gradle = Path('llama-upstream/examples/llama.android/app/build.gradle.kts')
text = app_gradle.read_text()
text = text.replace('applicationId = "com.example.llama.aichat"', 'applicationId = "com.nanu.localai"')
text = text.replace('versionCode = 1', 'versionCode = 15')
text = text.replace('versionName = "1.0"', 'versionName = "1.0-rc5"')
app_gradle.write_text(text)

lib_gradle = Path('llama-upstream/examples/llama.android/lib/build.gradle.kts')
text = lib_gradle.read_text()
text = text.replace('abiFilters += listOf("arm64-v8a", "x86_64")', 'abiFilters += listOf("arm64-v8a")')
lib_gradle.write_text(text)

main = Path('llama-upstream/examples/llama.android/app/src/main/java/com/example/llama/MainActivity.kt')
text = main.read_text()
text = text.replace('class MainActivity : AppCompatActivity() {', 'class MainActivity : NanuBaseActivity() {', 1)

replacement = r'''    private fun showRecommendedModelCatalog(ramGb: Double) {
        val tasks = arrayOf(
            "Everyday chat",
            "Coding",
            "Study",
            "Maritime",
            "Trading analysis"
        )

        AlertDialog.Builder(this)
            .setTitle("Choose a task")
            .setItems(tasks) { _, which ->
                val taskId = when (which) {
                    1 -> "coding"
                    2 -> "study"
                    3 -> "maritime"
                    4 -> "trading"
                    else -> "general"
                }
                showRecommendedModelsForTask(ramGb, taskId, tasks[which])
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun showRecommendedModelsForTask(ramGb: Double, taskId: String, taskLabel: String) {
        val modeHint = if (taskId == "coding") "coding" else null
        val best = ModelCatalog.bestForRam(ramGb, modeHint)
        val ordered = listOf(best) + ModelCatalog.models.filter { it.id != best.id }

        val labels = ordered.map { model ->
            val downloaded = modelDownloader.destinationFile(model).let {
                it.exists() && modelDownloader.looksLikeGguf(it)
            }
            val badge = when {
                downloaded -> "✓ DOWNLOADED"
                model.id == best.id -> "★ BEST FOR $taskLabel"
                ramGb + 0.25 >= model.minimumRamGb -> "✓ Compatible"
                else -> "⚠ ${model.minimumRamGb} GB+ suggested"
            }
            "$badge\n${model.name} • ${model.quant}\n${model.sizeLabel} • ${model.speedLabel} • ${model.useCase}"
        }

        AlertDialog.Builder(this)
            .setTitle("$taskLabel • ${String.format(Locale.US, "%.1f", ramGb)} GB RAM")
            .setItems(labels.toTypedArray()) { _, which ->
                showModelSuggestionDetail(ordered[which], ramGb, best.id)
            }
            .setNegativeButton("Back") { _, _ -> showRecommendedModelCatalog(ramGb) }
            .show()
    }
'''

pattern = r'    private fun showRecommendedModelCatalog\(ramGb: Double\) \{.*?\n    \}\n\n    private fun showModelSuggestionDetail'
replacement_text = replacement + '\n    private fun showModelSuggestionDetail'
# Use a callable replacement so Python's regex engine does not reinterpret Kotlin escapes such as \n.
patched, count = re.subn(pattern, lambda _: replacement_text, text, count=1, flags=re.S)
if count != 1:
    raise SystemExit('Could not patch recommended model catalog dialog')
main.write_text(patched)

manifest = Path('llama-upstream/examples/llama.android/app/src/main/AndroidManifest.xml')
text = manifest.read_text()
manifest_open = '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
additions = '''

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <queries>
        <intent>
            <action android:name="android.intent.action.TTS_SERVICE" />
        </intent>
    </queries>'''
if 'android.permission.INTERNET' not in text:
    text = text.replace(manifest_open, manifest_open + additions, 1)

main_marker = '        <activity\n            android:name=".MainActivity"'
activities = (
    '        <activity\n'
    '            android:name=".TradingActivity"\n'
    '            android:exported="false" />\n\n'
    '        <activity\n'
    '            android:name=".TalkActivity"\n'
    '            android:exported="false" />\n\n'
    '        <activity\n'
    '            android:name=".CreateActivity"\n'
    '            android:exported="false" />\n\n'
)
if 'android:name=".TradingActivity"' not in text:
    text = text.replace(main_marker, activities + main_marker, 1)
text = text.replace('android:icon="@mipmap/ic_launcher_round"', 'android:icon="@drawable/ic_nanu_launcher"')
text = text.replace('android:roundIcon="@mipmap/ic_launcher_round"', 'android:roundIcon="@drawable/ic_nanu_launcher"')
text = text.replace(
    'android:name=".MainActivity"\n            android:exported="true"',
    'android:name=".MainActivity"\n            android:configChanges="orientation|screenSize|keyboardHidden"\n            android:windowSoftInputMode="adjustResize"\n            android:exported="true"'
)
manifest.write_text(text)
PY

python3 <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

manifest = Path('llama-upstream/examples/llama.android/app/src/main/AndroidManifest.xml')
ET.parse(manifest)
text = manifest.read_text()
for required in [
    'android.permission.INTERNET',
    'android.permission.RECORD_AUDIO',
    'android:name=".TradingActivity"',
    'android:name=".TalkActivity"',
    'android:name=".CreateActivity"',
    '@drawable/ic_nanu_launcher',
]:
    assert required in text, required

main = Path('llama-upstream/examples/llama.android/app/src/main/java/com/example/llama/MainActivity.kt').read_text()
assert 'Choose a task' in main
assert 'showRecommendedModelsForTask' in main
assert 'Download in Nanu' in main

native = Path('llama-upstream/examples/llama.android/app/src/main/jniLibs/arm64-v8a/libsd.so')
assert native.exists() and native.stat().st_size > 1_000_000
print('RC5 source validation passed.')
PY

(
  cd llama-upstream/examples/llama.android
  chmod +x gradlew
  ./gradlew --no-daemon :app:assembleDebug :app:bundleDebug --stacktrace
)

mkdir -p out
cp llama-upstream/examples/llama.android/app/build/outputs/apk/debug/app-debug.apk out/nanu-local-ai-v1.0-rc5.apk
cp llama-upstream/examples/llama.android/app/build/outputs/bundle/debug/app-debug.aab out/nanu-local-ai-v1.0-rc5-debug.aab
sha256sum out/* | tee out/SHA256SUMS.txt
