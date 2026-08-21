# Nanu Local AI v0.1

This branch bootstraps a local/offline Android AI app for ARM64 tablets.

## v0.1 goal

- Build a normal Android APK in GitHub Actions.
- Import a GGUF model from Android storage.
- Run inference locally on-device after the model is imported.
- Stream generated text into the chat UI.
- No cloud API key is required for inference.

## Engine baseline

The first build is intentionally based on the pinned Android example from `ggml-org/llama.cpp` at commit `9a286ac98d2cab74231bd3f1fc3f2b8bdf05422e`. The build workflow applies Nanu branding, uses application ID `com.nanu.localai`, and limits native binaries to `arm64-v8a` for the target Samsung tablet.

## First device test

1. Install the debug APK from the GitHub Actions artifact.
2. Open **Nanu Local AI**.
3. Tap the action button and choose a small GGUF model.
4. Wait while the model is imported and loaded.
5. Send a short prompt and confirm that generation works with airplane mode enabled.

Start with a small 1B-2B Q4 GGUF model. Larger models come only after measuring RAM use and tokens/second on the device.
