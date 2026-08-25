# Nanu RC8 — Data Safety Working Notes

This is a preparation aid for Play Console. The developer must verify the exact production build and answer Play Console's current form.

## Local data

RC8 locally stores or processes user prompts, model files, imported documents/extracted text, generated images, settings, Ask My Files history, local AI safety reports, trade journal data and paper-trading data. Local-only processing is generally not considered "collection" for Play Data safety unless data is transmitted off device.

## Network activity to disclose/review

- User-initiated model downloads contact the configured model host (currently Hugging Face URLs).
- Live market snapshot requests contact public market-data services used by MarketSnapshotClient.
- User-opened model source/license, privacy-policy and terms links open external network destinations.
- Speech recognition prefers on-device recognition but Android may use the user's installed speech service when offline recognition is unavailable; the production disclosure must reflect the actual speech provider behavior on supported devices.
- RC8 test AI safety reports are local until explicitly exported. If a production reporting endpoint is added, update this document and the Play Data safety declaration to cover transmitted report content.

## Permissions expected in RC8

- `android.permission.INTERNET`
- `android.permission.RECORD_AUDIO`

RC8 preflight rejects broad storage, location, contacts, SMS, call-log, all-packages and overlay permission markers.

## Privacy-policy consistency

The public privacy policy must be updated whenever the production build adds analytics, ads, accounts, cloud sync, crash reporting, remote AI inference, a remote reporting endpoint, a new market provider, or any other off-device data flow.
