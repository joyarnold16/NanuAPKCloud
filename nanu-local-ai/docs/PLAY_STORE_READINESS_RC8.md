# Nanu Local AI RC8 — Google Play Readiness

Date reviewed: 25 August 2026

## Built into RC8

- Android compile/target API 36 path retained for the 31 August 2026 Play target-API deadline.
- AAB produced by CI in addition to the APK.
- No ads or analytics SDKs in the RC8 source.
- Local-first AI inference and document processing.
- Android system document picker instead of broad storage permissions.
- Microphone permission is requested contextually for voice features.
- No location, contacts, SMS, call-log, broad package-query or all-files permissions.
- Android cloud backup disabled for Nanu app-private data in the generated RC8 manifest.
- Cleartext traffic disabled in the generated RC8 manifest.
- In-app Privacy & Safety screen with public privacy-policy and terms links.
- AI-output reporting workflow in the app; RC8 test reports are stored locally and exportable.
- Markets contains analysis/risk/journal tools and a separate virtual-money Paper Trading mode.
- No broker execution, cryptocurrency exchange, custodial wallet, private-key storage, or real-money order path in RC8.
- Financial-risk disclaimers are displayed in the Markets/Paper Trading experiences.

## Required before production Play submission

These items require developer/account or hosted-service actions and cannot be completed solely by compiling the APK:

1. Configure production app signing / Play App Signing and upload a release AAB (the current CI artifact is a debug/test AAB).
2. Complete Play Console Data safety accurately for the exact production build.
3. Complete the Financial features declaration because Nanu contains market/financial analysis tools.
4. Complete content rating, target audience, app access, ads, and other App content declarations.
5. Provide a valid developer support contact in Play Console.
6. Replace the RC8 local-only AI safety report storage with a maintained in-app developer reporting endpoint before production if Google Play requires reports to reach the developer without leaving the app.
7. Review the final store listing and screenshots so they do not promise guaranteed profit, autonomous trading, impossible offline capabilities, or unsupported features.
8. Run device testing and Play pre-launch report, then resolve crashes/ANRs/accessibility warnings.
9. Recheck Google Play policies immediately before submission because policies and deadlines can change.

## Financial positioning for the store listing

Recommended wording: "Local AI assistant with optional informational Forex/Crypto analysis, risk calculators, journaling and virtual-money paper trading. Nanu does not place real-money trades or guarantee financial outcomes."

Avoid describing RC8 as a broker, exchange, wallet, guaranteed signal service, or autonomous profit-making system.
