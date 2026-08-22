# Nanu Local AI 1.0 RC1

Nanu Local AI is a private Android AI chat app that runs compatible GGUF language models directly on-device. The current release candidate keeps inference offline and does not require a cloud AI account or API key.

## Product features

- Polished dark chat interface for phones and tablets
- Local GGUF model import and app-private model storage
- Model manager for switching between stored models
- Automatic reopening of the last-used model
- General, Coding, Study, and Maritime assistant modes
- New Chat resets model conversation state
- Streaming responses with Stop generation
- Hidden Qwen `<think>` / internal-reasoning blocks in the visible UI
- Copy actions on user and assistant messages
- In-app Report action that opens an email draft for user-controlled reporting
- Approximate generation token count, tokens/second, and elapsed time
- Clear OFFLINE status and local-only model information
- No cloud AI API key required
- No Android INTERNET permission required by the local-inference build

## Android target

- Application ID: `com.nanu.localai`
- Target SDK: Android 16 / API 36
- Current minimum SDK inherited from the upstream Android engine: API 33
- Native ABI in the release-candidate build: `arm64-v8a`

This means the app is not Samsung-specific. It is intended for modern ARM64 Android phones and tablets that meet the Android-version, RAM, and storage requirements of the selected model.

## Engine baseline

The build uses the Android inference implementation from `ggml-org/llama.cpp`, pinned to commit `9a286ac98d2cab74231bd3f1fc3f2b8bdf05422e` for reproducibility.

## Build outputs

GitHub Actions builds:

- `nanu-local-ai-v1.0-rc1.apk` for sideload/device testing
- `nanu-local-ai-v1.0-rc1-debug.aab` to verify the Android App Bundle build path

A production Play Store AAB will use a dedicated release/upload signing key after device validation of this release candidate.

## Privacy

Prompts and generation stay on-device in this build. Model files are copied to app-private storage. The Report action only opens a user-visible email draft and sends nothing unless the user explicitly sends it.
