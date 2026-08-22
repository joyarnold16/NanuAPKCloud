# Nanu Local AI 1.0 RC4

Nanu Local AI is a private, on-device Android AI app built around llama.cpp, GGUF models, and local-first specialist tools.

## Proven core

- Run GGUF inference locally on-device.
- Stream generated text into the chat UI.
- No cloud AI API key is required for inference.
- Local inference continues to work without a network connection after a model is installed.

## Product UI

- Polished dark Nanu chat interface.
- Visible OFFLINE/local status for the local inference path.
- Model manager with stored-model switching and deletion.
- Last-used model restore.
- New Chat reset.
- Stop generation.
- Hidden `<think>...</think>` reasoning blocks.
- Copy and Report actions.
- General, Coding, Study and Maritime assistant modes.
- Local generation statistics including approximate tokens/second and elapsed time.
- Dedicated Trading entry for the Nanu Trading Lab.

## In-app model catalog and downloads

The Models screen detects total device RAM and presents a built-in recommended LLM catalog. It labels the best match for the device and current assistant mode, and shows download size, suggested RAM, expected speed, use case, license and notes.

Initial suggestions:

- Gemma 3 1B Instruct Q4_K_M — lightweight / lower-memory devices.
- Qwen3 1.7B Q4_K_M — fast everyday balance and the first model proven on Nanu.
- Qwen2.5 Coder 1.5B Instruct Q4_K_M — coding-focused option.
- Qwen3 4B Q4_K_M — better quality on stronger devices.
- Qwen3 8B Q4_K_M — advanced high-memory option; slower on Android CPU inference.

Recommended models can be downloaded directly inside Nanu Local AI. Android DownloadManager handles the transfer so a large download can continue in the background. Nanu displays progress, allows cancellation/hiding, checks free storage before starting, validates the GGUF file signature after completion, stores the model in app-owned storage, and automatically loads it when ready. An optional Source / license button still opens the upstream model page.

Because Nanu can download model files and optional market snapshots, the app requests Android INTERNET permission. LLM inference and the deterministic Trading Lab calculations remain on-device.

Users can still import their own compatible GGUF models manually.

## RC4 Nanu Trading Lab

RC4 adds a dedicated Forex + Crypto workspace. The important design rule is that indicator values and chart-pattern heuristics are calculated by deterministic Kotlin code rather than guessed by the LLM.

Trading Lab includes:

- Forex and Crypto modes.
- Pasted OHLCV candle analysis that can work offline.
- SMA 20/50 and EMA 20/50.
- RSI 14.
- MACD + signal line.
- ATR 14.
- ADX 14.
- Stochastic %K / %D.
- Bollinger Bands.
- VWAP when volume is supplied.
- Support and resistance heuristics.
- HH/HL/LH/LL market-structure classification.
- Fibonacci retracement and extension reference levels.
- RSI divergence heuristics.
- Candlestick detection including doji, hammer, shooting star, engulfing, morning/evening star, and three-soldier/crow structures.
- Chart-pattern heuristics including double top/bottom, head-and-shoulders candidates, triangles, wedges, channels, breakout/breakdown conditions.
- Confluence scoring with bullish/bearish/neutral bias.
- Volatility/risk classification.
- Forex position-size calculator.
- Crypto position-size and R:R calculator.
- Local-only trade journal.
- Offline chart/candlestick pattern knowledge library.
- Optional online Crypto price snapshots and Forex reference-rate snapshots.
- Chart-image picker wired as the foundation for future local vision-model chart analysis.

RC4 does not place real trades and does not present patterns as guaranteed buy/sell signals. Screenshot interpretation remains disabled until a real local vision model is integrated; the app does not pretend the text-only LLM can see an image.

## Planned specialist engines

- Local speech-to-text + text-to-speech for Talk to Nanu.
- Local image generation using a dedicated image engine.
- Local vision model for chart screenshots and other images.
- Persistent chat history and document/RAG tools.
- Additional audited market-data adapters and multi-symbol scanners.

## Build

The GitHub Actions workflow produces an ARM64 debug APK and debug AAB for device validation. Production Play Store signing is intentionally kept separate until the release candidate is validated across multiple Android devices.
