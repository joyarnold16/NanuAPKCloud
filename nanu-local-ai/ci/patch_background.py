"""Apply background/history build configuration to the generated upstream Android project."""
from pathlib import Path
import xml.etree.ElementTree as ET
import shutil

project = Path('llama-upstream/examples/llama.android')
app = project / 'app/src/main'
for name in ['ChatStore.kt', 'LocalTaskService.kt', 'TaskScreenSession.kt']:
    shutil.copyfile(Path('nanu-local-ai/app') / name, app / 'java/com/example/llama' / name)
android = '{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android', android[1:-1])
path = app / 'AndroidManifest.xml'
tree = ET.parse(path)
manifest = tree.getroot()
for name in ['FOREGROUND_SERVICE', 'FOREGROUND_SERVICE_SPECIAL_USE', 'POST_NOTIFICATIONS', 'WAKE_LOCK']:
    full = 'android.permission.' + name
    if not any(e.get(android + 'name') == full for e in manifest.findall('uses-permission')):
        ET.SubElement(manifest, 'uses-permission', {android + 'name': full})
application = manifest.find('application')
if not any(s.get(android + 'name') == '.LocalTaskService' for s in application.findall('service')):
    service = ET.SubElement(application, 'service', {android + 'name': '.LocalTaskService', android + 'exported': 'false', android + 'stopWithTask': 'false', android + 'foregroundServiceType': 'specialUse'})
    ET.SubElement(service, 'property', {android + 'name': 'android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE', android + 'value': 'User-initiated on-device language-model inference and image generation; saves progress while minimized, stops when complete or cancelled.'})
tree.write(path, encoding='utf-8', xml_declaration=True)
gradle = project / 'app/build.gradle.kts'
text = gradle.read_text()
if 'robolectric' not in text:
    text = text.replace('dependencies {', 'dependencies {\n    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")\n    testImplementation("org.robolectric:robolectric:4.14.1")', 1)
    text = text.replace('android {', 'android {\n    testOptions { unitTests.isIncludeAndroidResources = true }', 1)
gradle.write_text(text)
tests = project / 'app/src/test/java/com/example/llama'
tests.mkdir(parents=True, exist_ok=True)
for source in Path('nanu-local-ai/tests').glob('*.kt'):
    shutil.copyfile(source, tests / source.name)
print('Background service manifest, sources, and history tests configured.')
