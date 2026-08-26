# Nanu Local AI 1.0 — Data Safety Working Notes

Reviewed: 26 August 2026

This is a preparation aid for the exact signed production AAB. Play Console wording/taxonomy can change, so verify the final form at submission time.

## Local-only app content

Nanu locally stores or processes:
- user prompts and local model responses
- downloaded language/image models
- imported documents and extracted document text
- generated images
- app settings
- Ask My Files history
- optional local copies of AI safety reports
- trade journal data and virtual-money paper-trading data

These flows stay on the device unless the user deliberately shares/exports content or explicitly submits an AI safety report.

## Intentional network flows

- User-initiated language/image model downloads contact the configured model host (currently Hugging Face URLs).
- Live market snapshot requests contact the public market-data services used by `MarketSnapshotClient`.
- User-opened model source/license, privacy-policy and terms links open external destinations.
- Speech recognition prefers an on-device recognizer but Android may use the user's installed speech service when offline recognition is unavailable.
- When the user explicitly taps **Submit to developer**, Nanu transmits the selected AI-report category, reported output/details, a generated report reference, app identifier, timestamp and ordinary network metadata to the configured HTTPS developer reporting endpoint. Nanu does not silently submit reports.
- Users may separately save a local report copy or export it through Android's share sheet.

## Production AI-report Data Safety working position

For the Google Apps Script / support-mailbox backend prepared with this repo, review the current Play taxonomy with the following working answers in mind:

- **Collection is optional:** Yes. Nothing is submitted until the user chooses Report and then taps Submit to developer.
- **Likely user-data type:** Other user-generated content (reported AI output/details). Confirm the closest current Play Console label.
- **Primary purposes:** App functionality/support and safety/security/moderation. Select only purposes that match the current Play form and actual operation.
- **Encrypted in transit:** Yes. The production build only accepts an `https://` report endpoint and Android cleartext traffic is disabled.
- **Sold:** No.
- **Advertising use:** No.
- **Deletion request:** Supported through the public Nanu support contact. Users should include the report reference shown after submission when available so the report can be located.
- **Sharing/service-provider treatment:** Determine this from the final Play definition for the selected Google backend. Do not guess; verify whether Google Apps Script/Gmail is treated as a service provider for this specific flow.

Do not mark ordinary local prompts, local document contents, local generated images, paper-trading state or local model inference as uploaded to Nanu servers when they are not transmitted by the production build.

## Permissions expected

- `android.permission.INTERNET`
- `android.permission.RECORD_AUDIO`

Nanu CI rejects broad storage, location, contacts, SMS, call-log, all-packages and overlay permission markers.

## Financial declaration reminder

Nanu contains Forex/Crypto informational analysis, risk calculations and virtual-money paper trading. Complete the Financial features declaration accurately; do not represent the app as having no financial functionality merely because it does not place real-money trades.

## Privacy-policy consistency

The public privacy policy must stay consistent with the exact production build. Update it if Nanu later adds or changes analytics, ads, accounts, cloud sync, crash reporting, remote AI inference, the reporting backend, market providers, data retention or any other off-device data flow.
