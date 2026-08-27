# Nanu Local AI 1.0 — Play Console Submission Draft

Reviewed: 26 August 2026

Use this as a working checklist for the exact signed production AAB. Recheck Play Console wording before submitting because Google can change form labels.

## Store listing draft

**App name:** Nanu Local AI

**Suggested category:** Productivity

**Short description:**
Private local AI for chat, files, images, voice and market analysis.

**Full description draft:**
Nanu Local AI is a privacy-focused Android AI workspace designed to run core AI tasks on your device.

Features include:
- Local AI chat with downloadable GGUF language models
- Multiple model choices for everyday use, coding and higher-quality responses
- Ask My Files for supported documents
- Local AI image generation with a downloadable image model
- Voice conversation and text-to-speech
- Informational Forex and cryptocurrency market-analysis tools
- Risk calculators, journaling and virtual-money paper trading

Nanu is local-first. Core language-model inference and image generation run on the device after the selected models are downloaded. Internet access is used for user-initiated model downloads, optional live market-reference data, source/privacy links, and AI safety reports that the user explicitly chooses to submit.

Nanu does not execute real-money trades, operate a cryptocurrency exchange or custodial wallet, store private keys, or guarantee financial outcomes. Market tools are informational and paper trading uses virtual funds only.

AI-generated content can be reported from inside Nanu using the Report action. Shared local safety guardrails are applied before clearly restricted text/image requests are sent to the generative engines.

## App content / declarations

### Ads
Current source contains no advertising SDK. Answer **No** only if the production AAB remains ad-free.

### App access
Current app does not require a Nanu account or login. If that remains true, no reviewer credentials should be necessary.

### Target audience
Choose this deliberately before submission. Because Nanu includes general-purpose generative AI plus Forex/Crypto market-analysis and paper-trading features, an adult-focused audience is the simplest positioning. Do not select child-directed audiences unless the app, content, disclosures and compliance work are intentionally redesigned for them.

### Content rating
Complete the Play rating questionnaire truthfully for generative AI, user-entered prompts, generated images/text, market-related content and internet access. Use the rating produced by the Play questionnaire.

### Financial features declaration
Nanu does **not** provide:
- Cryptocurrency wallet
- Cryptocurrency exchange
- Stock-trading execution
- Banking/loans/payments
- Real-money brokerage/order execution

Because Nanu provides Forex/Crypto analysis, risk calculations and market-oriented AI guidance, review and declare **Financial advice** and, if Play's current form warrants it, **Other** for informational technical-analysis/paper-trading functionality. Do not claim "My app doesn't provide any financial features" while these market-analysis features are present.

### Data Safety working position
Core prompts/documents/images are processed locally and are not intentionally uploaded to Nanu servers.

When a user explicitly taps **Submit to developer** on an AI safety report, the production build transmits report details to the configured HTTPS reporting endpoint. Review the current Data Safety taxonomy and disclose this optional collection accurately. A likely relevant data type is **Other user-generated content**. The public privacy policy provides a support-contact deletion-request path for submitted reports.

The production app requires encryption in transit for the configured report endpoint and disables cleartext traffic.

### AI-generated content
Nanu contains text and image generative-AI functionality. It provides an in-app Report action and local restricted-content guardrails. Test both prevention and reporting end-to-end before release.

If any Play Store listing, promotional or YouTube assets are AI-generated/AI-edited and fall within Play's current declaration flow, complete the asset-level AI declaration accurately.

## Production release configuration

Report service:
- `NANU_REPORT_ENDPOINT` = maintained HTTPS report endpoint. Supply as a manual workflow input or repository variable.

Public support contact:
- The release workflow has the current public Nanu support mailbox as its default. It can be overridden by workflow input/repository variable if needed.

Private GitHub Actions secrets:
- `NANU_UPLOAD_KEYSTORE_BASE64`
- `NANU_UPLOAD_STORE_PASSWORD`
- `NANU_UPLOAD_KEY_ALIAS`
- `NANU_UPLOAD_KEY_PASSWORD`

Never commit the keystore or passwords to the repository.

## Release validation

The Play release pipeline must pass all of the following before its AAB is used:
- API 36 build path
- Gradle `release` bundle build
- permanent upload-key signing
- AAB signature integrity verification
- 16 KB ELF LOAD alignment validation for every packaged native `.so`
- HTTPS production report endpoint injection
- public support contact injection
- static AI reporting/safety-policy checks
- SHA-256 generation for the final AAB
- public upload-certificate export

Expected artifact files:
- `nanu-local-ai-v1.0-play-release.aab`
- `PLAY_RELEASE_SHA256.txt`
- `nanu-upload-certificate.pem`
- `UPLOAD_CERTIFICATE_INFO.txt`

## Release path

1. Finish on-device testing of the Play-prep build.
2. Deploy the prepared AI report backend and test a real report end-to-end.
3. Configure the permanent signing secrets and report endpoint.
4. Merge the reviewed feature branch into `main`.
5. Run **Build Nanu 1.0 Play Release**.
6. Verify/download the signed AAB, digest and public upload certificate.
7. Create/configure the Play Console app for package `com.nanu.localai` and enable Play App Signing.
8. Upload the AAB to **Internal Testing first**.
9. Complete App content, Data Safety, Financial features, content rating, target audience, store listing and privacy-policy fields.
10. Run Play pre-launch testing and fix crashes/ANRs/accessibility issues.
11. Move to Closed Testing if required by the developer account.
12. Apply for / roll out Production only after all policy/testing requirements are satisfied.
