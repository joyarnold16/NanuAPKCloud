# Nanu Local AI 1.0 — Google Play Readiness

Date reviewed: 18 September 2026

## Built into the app

- Android compile/target API 36 path.
- APK/AAB CI validation and native runtime checks.
- 16 KB page-size validation for every packaged native `.so` using ELF LOAD alignment checks.
- No ads or analytics SDKs in the current source.
- Local-first LLM/image inference and document processing.
- Bundled Latin/Devanagari OCR for selected images and scanned PDF pages; no OCR cloud service.
- Adaptive Compose home, light/dark/system themes and tablet-aware two-column layout.
- Local project RAG, user-controlled memory notes and bounded allow-listed agent tools.
- Optional read-only weather/time, crypto, FX, news, web and reusable-image tools with a Home-screen off switch, source links and retrieval times.
- Offline 78-card Tarot reference/readings with a local history and explicit entertainment/reflection boundary.
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
- Trading Lab, Paper Trading, technical-analysis UI, risk calculators, trade journals and Trading chat mode are excluded from Nanu Local AI.
- General current crypto-price and currency-rate answers remain read-only information lookups.
- Older test installations erase their obsolete Paper Trading and Trading Lab preferences once after upgrading.
- Legacy trading source/resources have been removed from Nanu Local AI; build and artifact checks fail if trading implementation returns.

## Play-only release pipeline

`.github/workflows/build-nanu-play-release.yml` produces signed release artifacts. It runs for the reviewed RC8 branch and can also be started manually or by the controlled main-branch trigger after release configuration is complete.

Production configuration:
- `NANU_REPORT_ENDPOINT` — maintained HTTPS endpoint that accepts Nanu safety-report JSON. It may be supplied as the manual workflow input or repository variable.
- `NANU_PLAY_PUBLIC_KEY` — public RSA billing key for verifying the one-time `nanu_pro_lifetime` receipt.
- The current public Nanu support mailbox is the default release support contact; it may be overridden by workflow input/repository variable if needed.

GitHub Actions secrets:
- `NANU_UPLOAD_KEYSTORE_BASE64`
- `NANU_UPLOAD_STORE_PASSWORD`
- `NANU_UPLOAD_KEY_ALIAS`
- `NANU_UPLOAD_KEY_PASSWORD`

The workflow also accepts the repository's existing Nanu signing secret names as
fallbacks: `NANU_RELEASE_KEYSTORE_B64`, `NANU_RELEASE_KEYSTORE_PASSWORD`,
`NANU_RELEASE_KEY_ALIAS`, and `NANU_RELEASE_KEY_PASSWORD`, respectively. Keep a
complete matching set; do not mix credentials from different keys. Secret presence
alone does not prove that the key opens or matches an existing Play app's upload
certificate; verify the exported certificate against Play Console before upload.

The workflow builds version `1.0` / versionCode `100`, runs release lint, uses the Gradle `release` build type, signs both artifacts with the permanent upload key, verifies signatures/alignment, checks 16 KB native compatibility and verifies that trading implementation is absent. It publishes:
- `nanu-local-ai-v1.0-release.apk` — signed installable device-test file
- `nanu-local-ai-v1.0-play-release.aab`
- `PLAY_RELEASE_SHA256.txt`
- `PLAY_APK_SIGNATURE.txt`
- `PLAY_APK_ALIGNMENT.txt`
- `PLAY_LINT_REPORT.html`
- `nanu-upload-certificate.pem`
- `UPLOAD_CERTIFICATE_INFO.txt`

The private keystore is decoded only in the CI workspace and removed after the build.

## Remaining account-side steps before a Play upload

1. Deploy the prepared Nanu AI-report backend (or another maintained compatible HTTPS endpoint) and test a real in-app report end to end.
2. Configure the permanent Play upload-key secrets in GitHub Actions. Never commit the keystore or passwords to Git.
3. Run **Build Nanu 1.0 Play Release** and keep the signed APK, signed AAB, upload certificate and digest.
4. Enroll/use Play App Signing when creating the Play Console app/release.
5. Upload the signed AAB to **Internal Testing first**.
6. Complete Play Console Data safety for the exact production AAB, including explicit AI-report submissions.
7. Review the current Financial features declaration against the exact AAB. Nanu Local AI has no trading analysis or paper trading, but it can return user-requested current crypto-price and currency-rate information.
8. Complete content rating, target audience, App access, Ads and other App content declarations.
9. Use a truthful store listing/screenshots: no guaranteed profit, autonomous real-money trading, impossible offline capabilities or unsupported claims.
10. Run the Play pre-launch report and resolve crashes/ANRs/accessibility warnings before Production.
11. For a personal developer account created after 13 November 2023, complete a closed test with at least 12 continuously opted-in testers for 14 days, then apply for Production access.
12. Recheck current Google Play policies immediately before final submission.

## Store positioning

Recommended wording:

> Private local AI for chat, files, voice and images, with optional read-only current information and internet search.

Avoid describing Nanu Local AI as a trading, broker, exchange, wallet, signal or autonomous profit-making product. Trading belongs in a separate application.
