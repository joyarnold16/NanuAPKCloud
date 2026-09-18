# Nanu Local AI 1.0 Final Test

Nanu Local AI is a local-first Android AI workspace built around llama.cpp, GGUF language models, bounded read-only information tools, local document retrieval, Android speech services, offline tarot, and a bundled stable-diffusion.cpp image engine.

## Final-candidate test goals

Version `1.0-final-test` (versionCode 27) is the integrated device-acceptance candidate. It is not the signed Play Store release.

### Local chat and in-app LLM downloads

- GGUF inference runs on-device with llama.cpp.
- Models can be switched, deleted, restored, or manually imported.
- The Models flow lists every supported model in one place and puts the best compatible everyday or coding option first for the detected device RAM.
- Tapping a recommended model opens its details and a **Download in Nanu** action.
- Android DownloadManager downloads recommended GGUF files directly into app-owned storage, shows progress, resumes after relaunch, validates GGUF format, and automatically loads a completed model.
- Initial LLM choices remain Gemma 3 1B, Qwen3 1.7B, Qwen2.5 Coder 1.5B, Qwen3 4B, and Qwen3 8B.

Internet access is used for optional model downloads and user-requested read-only information tools. LLM inference itself remains on-device after a model is installed. General web lookup uses DuckDuckGo's key-free HTML results; it does not require a Nanu subscription or API key, but the public provider may rate-limit requests.

The final test build returns complete weather, time, current-price, currency-rate, news, reusable-image, calculator and Tarot tool results directly. This preserves the source and retrieved time exactly, avoids needless model loading, and lets these specific tools work even before an LLM is installed. Open-ended web results still pass through the local model for synthesis.

Ask My Files accepts up to five sources at once. It extracts embedded text from PDF and Office files and uses bundled, on-device Latin/Devanagari OCR for images and scanned PDF pages. Retrieval remains local and answers cite the exact source and section supplied to the model.

### Talk to Nanu

RC8 includes a dedicated Continuous Talk screen:

- Tap-to-talk microphone flow.
- Uses Android on-device speech recognition when the device exposes it; otherwise Nanu requests offline-preferred recognition from the installed Android speech service.
- Recognized speech is sent to the same local llama.cpp LLM.
- The response is shown as text and can be spoken with Android Text-to-Speech.
- Voice reply can be turned off and listening/generation can be stopped.

Speech recognition availability and whether the speech service is fully offline depend on the speech packages installed on the Android device. The LLM response itself remains local.

### Nanu Create — local image generation

RC8 bundles a CPU build of stable-diffusion.cpp for ARM64 Android and adds Create Studio.

- Recommended starter model: Stable Diffusion 1.5 Q4_0 GGUF.
- The image model can be downloaded inside Nanu from Hugging Face into app-owned storage.
- Download progress is shown and the completed model is checked for GGUF format and SHA-256 integrity.
- Text prompt and optional negative prompt.
- Fast and High quality modes, square/landscape/portrait ratios, image-to-image editing, change strength and VAE tiling.
- Generated PNG is previewed in-app and stored in Nanu's app-specific Pictures directory.
- After the image model is downloaded, generation is local; no cloud image API is used.

CPU image generation is much heavier than chat and can take several minutes, consume multiple gigabytes of RAM, and warm the device.

The image engine is pinned to stable-diffusion.cpp commit `97d2990807fe6d558e395f8764198d7c7e7b411c`.

### Product boundary: trading is separate

RC8 intentionally excludes Trading Lab, Paper Trading, trade journals, technical-analysis screens, trading modes and position-size tools. Those features belong in a separate trading application.

General current-information questions remain available through the bounded assistant. For example, a user can ask for a current BTC reference price or a currency exchange rate. These are factual, read-only lookups and are not trading analysis or execution.

### Product UI

- Nanu launcher icon.
- Adaptive Jetpack Compose home for phones and tablets.
- Animated Nanu orb, layered colour, restrained motion and quick actions.
- System, light and dark appearance modes shared by Compose and legacy screens.
- Consistent vector icons, natural message entrance motion, explicit thinking state, voice activity visualizer and live-data insight cards.
- First-run privacy/onboarding guide.
- Header says `LOCAL AI` rather than implying the whole app never uses network access.
- Home navigation: Chat, Online Tools, Continuous Talk, Ask My Files, Create Studio, Tarot, Privacy/Safety and Nanu Pro.
- General, Coding, Academics and Create Image modes.
- New Chat, searchable history with message counts, rename/delete/clear controls, Stop generation, hidden `<think>` blocks, Copy/Report, and local generation statistics.

## Build

GitHub Actions builds ARM64 debug APK/AAB artifacts for device testing. The debug APK exposes the full Projects/Agents workspace so every feature can be accepted before billing is configured; release builds remain receipt-gated. The workflow packages the pinned llama.cpp Android engine, cross-compiles stable-diffusion.cpp for Android ARM64, runs unit tests and verifies that legacy trading components are excluded. Production signing and Play submission remain separate until the final candidate is validated on real devices.
