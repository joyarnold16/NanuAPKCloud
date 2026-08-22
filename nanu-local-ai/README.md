# Nanu Local AI 1.0 RC2

Nanu Local AI is a private, on-device Android LLM app built around llama.cpp and GGUF models.

## Proven core

- Import GGUF models from Android storage.
- Run inference locally on-device.
- Stream generated text into the chat UI.
- No cloud AI API key is required for inference.
- Nanu's local inference does not require Android INTERNET permission.

## Product UI

- Polished dark Nanu chat interface.
- Visible OFFLINE status.
- Model manager with stored-model switching and deletion.
- Last-used model restore.
- New Chat reset.
- Stop generation.
- Hidden `<think>...</think>` reasoning blocks.
- Copy and Report actions.
- General, Coding, Study and Maritime assistant modes.
- Local generation statistics including approximate tokens/second and elapsed time.

## RC2 model discovery

The Models screen now detects total device RAM and presents a built-in recommended LLM catalog. It labels a best match for the device and shows download size, suggested RAM, expected speed, use case, license and notes before opening the model's Hugging Face page in the user's browser.

Initial suggestions:

- Gemma 3 1B Instruct Q4_K_M — lightweight / lower-memory devices.
- Qwen3 1.7B Q4_K_M — fast everyday balance and the first model proven on Nanu.
- Qwen2.5 Coder 1.5B Instruct Q4_K_M — coding-focused option.
- Qwen3 4B Q4_K_M — better quality on stronger devices.
- Qwen3 8B Q4_K_M — advanced high-memory option; slower on Android CPU inference.

Downloads happen outside Nanu in the browser. Users then return to Models and import the downloaded GGUF. This preserves offline local inference and avoids silently downloading multi-gigabyte files from inside the app.

## Build

The GitHub Actions workflow produces an ARM64 debug APK and debug AAB for device validation. Production Play Store signing is intentionally kept separate until the release candidate is validated across multiple Android devices.
