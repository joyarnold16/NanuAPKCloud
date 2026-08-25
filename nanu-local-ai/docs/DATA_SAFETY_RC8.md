# Nanu 1.0 — Data Safety Working Notes

This is a preparation aid for Play Console. The developer must verify the exact production AAB and answer Play Console's current form.

## Local data

Nanu locally stores or processes user prompts, model files, imported documents/extracted text, generated images, settings, Ask My Files history, local AI safety-report copies, trade journal data and paper-trading data. Local-only processing is generally not considered collection for Play Data safety unless data is transmitted off device.

## Network activity to disclose/review

- User-initiated language/image model downloads contact the configured model host (currently Hugging Face URLs).
- Live market snapshot requests contact the public market-data services used by `MarketSnapshotClient`.
- User-opened model source/license, privacy-policy and terms links open external network destinations.
- Speech recognition prefers on-device recognition but Android may use the user's installed speech service when offline recognition is unavailable; production disclosures must reflect the actual supported-device behavior.
- When the user explicitly taps **Submit to developer**, Nanu transmits the selected AI-report category, reported output/details, a generated report reference, app identifier and ordinary network metadata to the configured HTTPS developer reporting endpoint. Nanu does not silently submit safety reports.
- Users may separately save a local report copy or export it through Android's share sheet.

## Permissions expected

- `android.permission.INTERNET`
- `android.permission.RECORD_AUDIO`

Nanu preflight rejects broad storage, location, contacts, SMS, call-log, all-packages and overlay permission markers.

## Play Data Safety review points

Before publishing the production AAB, review whether submitted AI safety-report content must be declared as user-generated content / app interactions / other user content under the current Play Console taxonomy. The declaration must match the actual reporting backend, its retention, encryption, sharing, deletion and access practices.

Model hosts, speech-service providers and market-data providers may receive ordinary network identifiers such as IP address as part of normal network operation. Verify current Play guidance for whether each flow is treated as collection by the app/developer or by an independent service provider.

## Privacy-policy consistency

The public privacy policy must be updated whenever the production build adds or changes analytics, ads, accounts, cloud sync, crash reporting, remote AI inference, the reporting backend, market providers, data retention or any other off-device data flow.
