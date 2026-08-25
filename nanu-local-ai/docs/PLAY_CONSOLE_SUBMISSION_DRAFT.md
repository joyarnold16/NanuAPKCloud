# Nanu Local AI 1.0 — Play Console Submission Draft

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

AI-generated content can be reported from inside Nanu using the Report action.

## App content / declarations

### Ads
Current source contains no advertising SDK. Answer **No** only if the production AAB remains ad-free.

### App access
Current app does not require a Nanu account or login. If that remains true, no reviewer credentials should be necessary.

### Target audience
Choose this deliberately before submission. Because Nanu includes general-purpose generative AI plus Forex/Crypto market-analysis and paper-trading features, an adult-focused audience is the simplest positioning. Do not select child-directed audiences unless the app, content, disclosures and compliance work are intentionally redesigned for them.

### Content rating
Complete the Play rating questionnaire truthfully for generative AI, user-entered prompts, generated images/text, market-related content and internet access. Do not guess the final rating in advance; use the rating produced by the Play questionnaire.

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

When a user explicitly taps **Submit to developer** on an AI safety report, the production build transmits report details to the configured HTTPS reporting endpoint. Review the current Data Safety taxonomy and disclose this optional collection accurately. A likely relevant data type is **Other user-generated content**. Confirm the exact purposes, retention/deletion policy and whether the chosen backend is acting as a service provider before finalizing the form.

The production app uses encryption in transit for the configured report endpoint and disables cleartext traffic.

### AI-generated content
Nanu contains text and image generative-AI functionality. It provides an in-app Report action and local safety guardrails. Test restricted-content prevention and reporting before release.

If any Play Store listing, promotional or YouTube assets are AI-generated/AI-edited and fall within Play's current declaration flow, mark the asset-level AI declaration accurately in Play Console.

## Required production values

Before the signed Play workflow can run:

Repository variables:
- `NANU_REPORT_ENDPOINT` = maintained HTTPS report endpoint
- `NANU_SUPPORT_EMAIL` = public support email

Repository secrets:
- `NANU_UPLOAD_KEYSTORE_BASE64`
- `NANU_UPLOAD_STORE_PASSWORD`
- `NANU_UPLOAD_KEY_ALIAS`
- `NANU_UPLOAD_KEY_PASSWORD`

Never commit the keystore or passwords to the repository.

## Release path

1. Finish on-device testing of the Play-prep build.
2. Deploy the AI report backend and test a real report end-to-end.
3. Configure support email and GitHub release variables/secrets.
4. Merge the reviewed feature branch into `main` when ready.
5. Run **Build Nanu 1.0 Play Release** manually.
6. Verify the signed `nanu-local-ai-v1.0-play-release.aab` and SHA-256 artifact.
7. Create/configure the Play Console app and enable Play App Signing.
8. Upload the AAB to Internal Testing first.
9. Complete App content, Data Safety, Financial features, content rating, target audience, store listing and privacy-policy fields.
10. Run Play pre-launch testing and fix crashes/ANRs/accessibility issues.
11. Move to Closed Testing if required by the developer account.
12. Apply for / roll out Production only after all policy/testing requirements are satisfied.
