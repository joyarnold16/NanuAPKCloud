# Nanu Local AI Privacy Policy

**Effective date:** 25 August 2026

Nanu Local AI is designed as a local-first Android assistant. This policy explains what the app accesses, what may leave the device, and what remains on the device.

## Local AI and user content

Nanu's downloaded language-model inference runs on the user's device. Prompts, generated text, imported documents, extracted document text, local chat/file-session data, generated images, paper-trading data, and app settings are stored locally in the app's storage unless the user intentionally shares, exports, or submits a safety report.

Nanu does not require a Nanu account and does not include advertising or analytics SDKs in the current build.

## Model downloads

When the user chooses to download a language or image model, Nanu connects to the model host identified in the app (currently Hugging Face-hosted model files). The remote host may receive ordinary network information such as the device IP address and request metadata according to that host's own privacy practices.

## Live market snapshots

The Markets feature can request public market-reference data over the internet. Cryptocurrency snapshots currently use CoinGecko endpoints and foreign-exchange reference rates use Frankfurter/ECB-derived endpoints. The symbol or market requested and ordinary network metadata may be transmitted to those services. Nanu does not send the user's local documents, chat history, or model prompts with these market requests.

## Voice features

Text-to-speech uses Android's installed text-to-speech service. Speech recognition prefers an on-device recognizer when Android provides one. If the installed device speech service does not support offline recognition, Android may fall back to the user's installed speech-recognition service, which may process audio according to that provider's settings and privacy policy. Nanu requests microphone access only when the user starts a voice feature.

## Files and images

Files are selected with Android's system document picker. Nanu does not request broad storage access. Selected documents are processed in app storage for local question answering and attachments. Generated images are created locally and, when Android permits, copied to `Pictures/Nanu` so the user can see them in Gallery.

## Financial features

Nanu provides market information, technical-analysis tools, risk calculators, a local journal, and paper-trading simulation. Nanu does not execute real-money trades, operate a cryptocurrency exchange, or provide a custodial cryptocurrency wallet.

## AI safety reports

Nanu includes an in-app safety-report form and a **Report** action on AI responses. Report details are not silently uploaded.

When a production build is configured with the developer reporting service and the user explicitly taps **Submit to developer**, Nanu sends the report category, the selected/reported AI output or details, a generated report reference, app identifier, timestamp, and ordinary network metadata to the configured HTTPS developer reporting endpoint so the developer can review and act on the report.

The user may separately save a local copy of a report or export a local report through Android's share sheet. Test builds may have developer submission disabled until the production endpoint is configured.

## Permissions

Nanu uses the minimum permissions needed for its features. Internet access is used for user-initiated model downloads, optional source/license links, live market snapshots, and AI safety reports the user explicitly submits. Microphone access is requested for voice conversation. File access uses Android's system pickers rather than broad storage permissions.

## Data retention and deletion

Local Nanu data remains on the device until the user deletes it, clears app storage, removes specific stored content, or uninstalls Nanu. Because Nanu does not create a Nanu account, there is no server-side Nanu account to delete.

Submitted AI safety reports are retained only according to the production developer reporting service's moderation and support process. Before public release, the developer should keep the reporting service and support mailbox available and apply a reasonable moderation/retention process.

## Security

Nanu uses Android app-private storage for local data where appropriate, requires HTTPS for the production AI-report endpoint, disables cleartext traffic, and avoids unnecessary sensitive permissions. No software can guarantee absolute security, and users should avoid placing secrets in prompts or documents unless they are comfortable storing them on the device.

## Children

Nanu is a general-purpose AI productivity application and is not specifically directed to children. The final Play Store age/content rating and target audience declarations must accurately reflect the published app and Google Play requirements.

## Changes to this policy

This policy may be updated when Nanu's features or data practices change. The published policy should always match the behavior of the Play Store version.

## Contact

Questions about Nanu Local AI, privacy, or safety reports can be sent to: **nanuai.1991@gmail.com**
