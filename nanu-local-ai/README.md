# Nanu Local AI 1.0 RC3

Nanu Local AI is a private, on-device Android LLM app built around llama.cpp and GGUF models.

## Proven core

- Run GGUF inference locally on-device.
- Stream generated text into the chat UI.
- No cloud AI API key is required for inference.
- Local inference continues to work without a network connection after a model is installed.

## Product UI

- Polished dark Nanu chat interface.
- Visible OFFLINE/local status.
- Model manager with stored-model switching and deletion.
- Last-used model restore.
- New Chat reset.
- Stop generation.
- Hidden `<think>...</think>` reasoning blocks.
- Copy and Report actions.
- General, Coding, Study and Maritime assistant modes.
- Local generation statistics including approximate tokens/second and elapsed time.

## RC3 model catalog and in-app downloads

The Models screen detects total device RAM and presents a built-in recommended LLM catalog. It labels the best match for the device and current assistant mode, and shows download size, suggested RAM, expected speed, use case, license and notes.

Initial suggestions:

- Gemma 3 1B Instruct Q4_K_M — lightweight / lower-memory devices.
- Qwen3 1.7B Q4_K_M — fast everyday balance and the first model proven on Nanu.
- Qwen2.5 Coder 1.5B Instruct Q4_K_M — coding-focused option.
- Qwen3 4B Q4_K_M — better quality on stronger devices.
- Qwen3 8B Q4_K_M — advanced high-memory option; slower on Android CPU inference.

Recommended models can now be downloaded directly inside Nanu Local AI. Android DownloadManager handles the transfer so a large download can continue in the background. Nanu displays progress, allows cancellation/hiding, checks free storage before starting, validates the GGUF file signature after completion, stores the model in app-owned storage, and automatically loads it when ready. An optional Source / license button still opens the upstream model page.

Because RC3 can download model files, the app requests Android INTERNET permission. That permission is used for optional model downloads; LLM inference itself remains on-device and does not call a cloud AI service.

Users can still import their own compatible GGUF models manually.

## Build

The GitHub Actions workflow produces an ARM64 debug APK and debug AAB for device validation. Production Play Store signing is intentionally kept separate until the release candidate is validated across multiple Android devices.
