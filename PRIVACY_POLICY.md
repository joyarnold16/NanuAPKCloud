# Nanu Local AI Privacy Policy

**Effective date:** 25 August 2026

Nanu Local AI is designed as a local-first Android assistant. This policy explains what the app accesses, what may leave the device, and what remains on the device.

## Local AI and user content

Nanu's downloaded language-model inference runs on the user's device. Prompts, generated text, imported documents, extracted document text, local chat/file-session data, generated images, paper-trading data, and app settings are stored locally in the app's storage unless the user intentionally shares or exports them.

Nanu does not require a Nanu account and does not include advertising or analytics SDKs in the RC8 build.

## Model downloads

When the user chooses to download a language or image model, Android's download service connects to the model host identified in the app (currently Hugging Face-hosted model files). The remote host may receive ordinary network information such as the device IP address and request metadata according to that host's own privacy practices.

## Live market snapshots

The Markets feature can request public market-reference data over the internet. Cryptocurrency snapshots currently use CoinGecko endpoints and foreign-exchange reference rates use Frankfurter/ECB-derived endpoints. The symbol or market requested and ordinary network metadata may be transmitted to those services. Nanu does not send the user's local documents, chat history, or model prompts with these market requests.

## Voice features

Text-to-speech uses Android's installed text-to-speech service. Speech recognition prefers an on-device recognizer when Android provides one. If the installed device speech service does not support offline recognition, Android may fall back to the user's installed speech-recognition service, which may process audio according to that provider's settings and privacy policy. Nanu requests microphone access only when the user starts a voice feature.

## Files and images

Files are selected with Android's system document picker. Nanu does not request broad storage access. Selected documents are processed in app storage for local question answering and attachments. Generated images are created locally and, when Android permits, copied to `Pictures/Nanu` so the user can see them in Gallery.

## Financial features

Nanu provides market information, technical-analysis tools, risk calculators, a local journal, and paper-trading simulation. RC8 does not execute real-money trades, operate a cryptocurrency exchange, or provide a custodial cryptocurrency wallet.

## AI safety reports

RC8 includes an in-app safety-report form. In RC8 test builds, reports are stored locally on the device and can be exported by the user. A production Play release must use an approved developer reporting endpoint before submission if required by Google Play's AI-generated-content policy. Nanu will not silently upload report content.

## Permissions

Nanu uses the minimum permissions needed for its features. Internet access is used for user-initiated model downloads, optional source/license links, and live market snapshots. Microphone access is requested for voice conversation. File access uses Android's system pickers rather than broad storage permissions.

## Data retention and deletion

Local Nanu data remains on the device until the user deletes it, clears app storage, removes specific stored content, or uninstalls Nanu. Because RC8 does not create a Nanu account, there is no server-side Nanu account to delete.

## Security

Nanu uses Android app-private storage for local data where appropriate and avoids unnecessary sensitive permissions. No software can guarantee absolute security, and users should avoid placing secrets in prompts or documents unless they are comfortable storing them on the device.

## Children

Nanu is a general-purpose AI productivity application and is not specifically directed to children. The final Play Store age/content rating and target audience declarations must accurately reflect the published app and Google Play requirements.

## Changes to this policy

This policy may be updated when Nanu's features or data practices change. The published policy should always match the behavior of the Play Store version.

## Contact

Before public Play Store release, the developer must publish and maintain a valid support contact and in-app AI-safety reporting destination in Play Console and in the app. Repository: `joyarnold16/NanuAPKCloud`.
