# Nanu Local AI 1.0 — Google Play Readiness

Date reviewed: 25 August 2026

## Built into the app

- Android compile/target API 36 path.
- APK/AAB CI validation and native runtime checks.
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
- Markets contains analysis/risk/journal tools and virtual-money Paper Trading.
- No broker execution, cryptocurrency exchange, custodial wallet, private-key storage or real-money order path.
- Financial-risk disclaimers in Markets/Paper Trading.

## Play-only release pipeline

`.github/workflows/build-nanu-play-release.yml` is a manual production workflow. It refuses to build unless the following are configured:

Repository variables:
- `NANU_REPORT_ENDPOINT` — maintained HTTPS endpoint that accepts Nanu safety-report JSON.
- `NANU_SUPPORT_EMAIL` — public support email used for the Play release.

GitHub Actions secrets:
- `NANU_UPLOAD_KEYSTORE_BASE64`
- `NANU_UPLOAD_STORE_PASSWORD`
- `NANU_UPLOAD_KEY_ALIAS`
- `NANU_UPLOAD_KEY_PASSWORD`

The workflow builds version `1.0` / versionCode `100`, uses the Gradle `release` build type, signs the AAB with the permanent upload key, verifies the AAB signature, and publishes `nanu-local-ai-v1.0-play-release.aab` as a workflow artifact. The private keystore is decoded only in the CI workspace and removed after the build.

## Still required before Production submission

1. Configure a maintained production AI-report endpoint and retention/deletion process.
2. Configure a valid public support email.
3. Store the permanent Play upload key securely and configure the four signing secrets above. Never commit the keystore or its passwords to Git.
4. Run the manual **Build Nanu 1.0 Play Release** workflow and keep the resulting signed AAB.
5. Enroll/use Play App Signing when creating the Play Console app/release.
6. Complete Play Console Data safety for the exact production AAB, including explicit AI-report submissions.
7. Complete the Financial features declaration because Nanu contains Forex/Crypto analysis and paper-trading tools.
8. Complete content rating, target audience, App access, Ads and other App content declarations.
9. Use a truthful store listing/screenshots: no guaranteed profit, autonomous real-money trading, impossible offline capabilities or unsupported claims.
10. Upload first to Internal or Closed testing, run the Play pre-launch report, and resolve crashes/ANRs/accessibility warnings before Production.
11. Recheck current Google Play policies immediately before submission.

## Store positioning

Recommended wording:

> Local AI assistant with optional informational Forex/Crypto analysis, risk calculators, journaling and virtual-money paper trading. Nanu does not place real-money trades or guarantee financial outcomes.

Avoid describing Nanu as a broker, exchange, wallet, guaranteed signal service or autonomous profit-making system.
