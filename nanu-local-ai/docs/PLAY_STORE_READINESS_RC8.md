# Nanu Local AI 1.0 — Google Play Readiness

Date reviewed: 26 August 2026

## Built into the app

- Android compile/target API 36 path.
- APK/AAB CI validation and native runtime checks.
- 16 KB page-size validation for every packaged native `.so` using ELF LOAD alignment checks.
- No ads or analytics SDKs in the current source.
- Local-first LLM/image inference and document processing.
- Android system document picker instead of broad storage permissions.
- Microphone permission requested contextually for voice features.
- No location, contacts, SMS, call-log, broad package-query or all-files permissions.
- Android cloud backup disabled for Nanu app-private data.
- Cleartext traffic disabled; production AI reports require HTTPS.
- Privacy & Safety screen with privacy-policy and terms links.
- **Report** action on AI responses that opens an in-app report form prefilled with the selected output.
- Direct in-app report submission client that only enables real submission when an HTTPS developer endpoint is configured.
- Shared local generative-AI safety guardrails for clearly restricted text/image requests.
- Public Nanu support contact is defined and included in the public privacy policy.
- Markets contains analysis/risk/journal tools and virtual-money Paper Trading.
- No broker execution, cryptocurrency exchange, custodial wallet, private-key storage or real-money order path.
- Financial-risk disclaimers in Markets/Paper Trading.

## Play-only release pipeline

`.github/workflows/build-nanu-play-release.yml` produces the production artifact. It can be started manually after the workflow exists on `main`; a controlled main-branch trigger file is also supported after release configuration is complete.

Production configuration:
- `NANU_REPORT_ENDPOINT` — maintained HTTPS endpoint that accepts Nanu safety-report JSON. It may be supplied as the manual workflow input or repository variable.
- The current public Nanu support mailbox is the default release support contact; it may be overridden by workflow input/repository variable if needed.

GitHub Actions secrets:
- `NANU_UPLOAD_KEYSTORE_BASE64`
- `NANU_UPLOAD_STORE_PASSWORD`
- `NANU_UPLOAD_KEY_ALIAS`
- `NANU_UPLOAD_KEY_PASSWORD`

The workflow builds version `1.0` / versionCode `100`, uses the Gradle `release` build type, signs the AAB with the permanent upload key, verifies the AAB signature, checks 16 KB native compatibility, and publishes:
- `nanu-local-ai-v1.0-play-release.aab`
- `PLAY_RELEASE_SHA256.txt`
- `nanu-upload-certificate.pem`
- `UPLOAD_CERTIFICATE_INFO.txt`

The private keystore is decoded only in the CI workspace and removed after the build.

## Remaining account-side steps before a Play upload

1. Deploy the prepared Nanu AI-report backend (or another maintained compatible HTTPS endpoint) and test a real in-app report end to end.
2. Configure the permanent Play upload-key secrets in GitHub Actions. Never commit the keystore or passwords to Git.
3. Run **Build Nanu 1.0 Play Release** and keep the signed AAB plus upload certificate/digest.
4. Enroll/use Play App Signing when creating the Play Console app/release.
5. Upload the signed AAB to **Internal Testing first**.
6. Complete Play Console Data safety for the exact production AAB, including explicit AI-report submissions.
7. Complete the Financial features declaration because Nanu contains Forex/Crypto analysis and paper-trading tools.
8. Complete content rating, target audience, App access, Ads and other App content declarations.
9. Use a truthful store listing/screenshots: no guaranteed profit, autonomous real-money trading, impossible offline capabilities or unsupported claims.
10. Run the Play pre-launch report and resolve crashes/ANRs/accessibility warnings before Production.
11. Complete Closed Testing if required for the developer account, then apply for/roll out Production.
12. Recheck current Google Play policies immediately before final submission.

## Store positioning

Recommended wording:

> Local AI assistant with optional informational Forex/Crypto analysis, risk calculators, journaling and virtual-money paper trading. Nanu does not place real-money trades or guarantee financial outcomes.

Avoid describing Nanu as a broker, exchange, wallet, guaranteed signal service or autonomous profit-making system.
