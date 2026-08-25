#!/usr/bin/env python3
from pathlib import Path

path = Path("nanu-local-ai/app/MainActivity.kt")
text = path.read_text()

old = '            .setMessage("All Nanu models are shown here. Tap any model to download or load it. Only one large model downloads at a time.")\n'
if old not in text:
    raise SystemExit("Could not locate conflicting model-dialog message")

# Android AlertDialog list content can be suppressed when a message and item list
# are configured together. Keep the title plus item rows only so every model is
# visible and tappable on Samsung/Material dialogs.
text = text.replace(old, "", 1)

for marker in [
    '.setTitle("Choose a local model',
    '.setItems(labels.toTypedArray())',
    'qwen3-1.7b-q4km',
    'qwen3-4b-q4km',
    'qwen3-8b-q4km',
]:
    if marker not in text:
        raise SystemExit(f"Model dialog patch missing expected marker: {marker}")

path.write_text(text)
print("RC8 model dialog list rendering fix applied.")
