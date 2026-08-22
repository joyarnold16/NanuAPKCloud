# Nanu Local AI 1.0 RC5

Nanu Local AI is a local-first Android AI app built around llama.cpp, GGUF language models, deterministic trading tools, Android speech services, and a bundled stable-diffusion.cpp image engine.

## RC5 test goals

RC5 is the integrated device-test candidate. It is not yet the production Play Store release.

### Local chat and in-app LLM downloads

- GGUF inference runs on-device with llama.cpp.
- Models can be switched, deleted, restored, or manually imported.
- The Models flow first asks what the user wants the model for: Everyday chat, Coding, Study, Maritime, or Trading analysis.
- The best compatible model for the selected task and detected device RAM is placed first.
- Tapping a recommended model opens its details and a **Download in Nanu** action.
- Android DownloadManager downloads recommended GGUF files directly into app-owned storage, shows progress, resumes after relaunch, validates GGUF format, and automatically loads a completed model.
- Initial LLM choices remain Gemma 3 1B, Qwen3 1.7B, Qwen2.5 Coder 1.5B, Qwen3 4B, and Qwen3 8B.

Internet access is used for optional model downloads and online market snapshots. LLM inference itself remains on-device after a model is installed.

### Talk to Nanu

RC5 adds a dedicated Talk screen:

- Tap-to-talk microphone flow.
- Uses Android on-device speech recognition when the device exposes it; otherwise Nanu requests offline-preferred recognition from the installed Android speech service.
- Recognized speech is sent to the same local llama.cpp LLM.
- The response is shown as text and can be spoken with Android Text-to-Speech.
- Voice reply can be turned off and listening/generation can be stopped.

Speech recognition availability and whether the speech service is fully offline depend on the speech packages installed on the Android device. The LLM response itself remains local.

### Nanu Create — local image generation

RC5 bundles a CPU build of stable-diffusion.cpp for ARM64 Android and adds a Create screen.

- Recommended starter model: Stable Diffusion 1.5 Q4_0 GGUF.
- The image model can be downloaded inside Nanu from Hugging Face into app-owned storage.
- Download progress is shown and the completed model is checked for GGUF format and SHA-256 integrity.
- Text prompt and optional negative prompt.
- 512 × 512 generation, 12 steps, VAE tiling.
- Generated PNG is previewed in-app and stored in Nanu's app-specific Pictures directory.
- After the image model is downloaded, generation is local; no cloud image API is used.

CPU image generation is much heavier than chat and can take several minutes, consume multiple gigabytes of RAM, and warm the device. RC5 intentionally starts with a conservative 512 × 512 path before GPU/Vulkan optimization.

The image engine is pinned to stable-diffusion.cpp commit `97d2990807fe6d558e395f8764198d7c7e7b411c`.

### Nanu Trading Lab

The existing Forex + Crypto Trading Lab remains included:

- Pasted OHLCV analysis that can work offline.
- SMA/EMA, RSI, MACD, ATR, ADX, stochastic, Bollinger Bands, VWAP.
- Support/resistance, HH/HL/LH/LL structure, Fibonacci, RSI divergence heuristics.
- Candlestick and chart-pattern heuristics.
- Confluence scoring and volatility/risk classification.
- Forex and Crypto position-size calculators.
- Local trade journal and offline pattern library.
- Optional online price snapshots.

Indicator calculations remain deterministic Kotlin code rather than values guessed by the LLM. RC5 does not place real trades or promise outcomes.

### Product UI

- Nanu launcher icon.
- Header says `LOCAL AI` rather than implying the whole app never uses network access.
- Home navigation: Talk, Create, Trading, Mode, Models.
- General, Coding, Study, and Maritime chat modes.
- New Chat, Stop generation, hidden `<think>` blocks, Copy/Report, and local generation statistics.

## Build

GitHub Actions builds ARM64 debug APK/AAB artifacts for device testing. The workflow packages the pinned llama.cpp Android engine and cross-compiles stable-diffusion.cpp for Android ARM64. Production signing, broader ABI support, privacy-policy finalization, and Play submission remain separate until RC5 is validated on real devices.
