#!/usr/bin/env bash
set -euo pipefail

python3 <<'PY'
from pathlib import Path

talk = Path('nanu-local-ai/app/TalkActivity.kt').read_text()
create = Path('nanu-local-ai/app/CreateActivity.kt').read_text()
layout = Path('nanu-local-ai/res/layout/activity_create.xml').read_text()

assert 'Reusing loaded local AI' in talk
assert 'waitForStableEngine' in talk
assert 'if (state is InferenceEngine.State.ModelReady)' in talk
assert 'Local AI is busy' in talk
assert 'FLAG_KEEP_SCREEN_ON' in create
assert 'saveToGallery' in create
assert 'timeoutMinutes' in create
assert 'KEY_LAST_IMAGE' in create
assert 'image_quality_button' in create
assert 'Mode: Fast' in layout
assert 'Pictures/Nanu' in layout

base = Path('nanu-local-ai/ci/build_rc5.sh').read_text()
base = base.replace("versionCode = 17", "versionCode = 18")
base = base.replace("rc5.2", "rc5.3")
base = base.replace("RC5.2", "RC5.3")
Path('/tmp/build_nanu_rc53.sh').write_text(base)
PY

chmod +x /tmp/build_nanu_rc53.sh
bash /tmp/build_nanu_rc53.sh

# Extra post-build validation for the RC5.3 behavior changes.
python3 <<'PY'
from pathlib import Path
from zipfile import ZipFile

apk = Path('out/nanu-local-ai-v1.0-rc5.3.apk')
assert apk.exists() and apk.stat().st_size > 1_000_000
with ZipFile(apk) as z:
    names = set(z.namelist())
for required in ['lib/arm64-v8a/libsd.so', 'lib/arm64-v8a/libc++_shared.so']:
    assert required in names, required
print('RC5.3 behavior and APK validation passed.')
PY
