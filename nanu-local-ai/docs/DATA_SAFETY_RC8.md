# Nanu Local AI 1.0 — Data Safety Working Notes

Reviewed: 14 September 2026

This is a preparation aid for the exact signed production AAB. Play Console wording/taxonomy can change, so verify the final form at submission time.

## Local-only app content

Nanu locally stores or processes:
- user prompts and local model responses
- downloaded language/image models
- imported documents and extracted document text
- explicit project memory notes and local-agent instructions
- generated images
- app settings
- Ask My Files history
- optional local copies of AI safety reports
- bounded local tool calls and results, including offline tarot readings and their optional local history

These flows stay on the device unless the user deliberately shares/exports content or explicitly submits an AI safety report.

## Intentional network flows

- User-initiated language/image model downloads contact the configured model host (currently Hugging Face URLs).
- When the user asks for current information and Online Tools are enabled, the bounded agent may send only the short place, symbol, currency pair or search term required to Open-Meteo (weather/time), CoinGecko (current crypto reference prices), Frankfurter (currency reference rates), Google News RSS (news), Jina Search (web search), or Wikimedia Commons (reusable image search). Full chats, documents and local RAG memory are not included in these requests. Online Tools can be disabled from Home.
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

Do not mark ordinary local prompts, local document contents, local generated images or local model inference as uploaded to Nanu servers when they are not transmitted by the production build.

## Permissions expected

- `android.permission.INTERNET` (model downloads and optional, user-requested online tools/reporting)
- `android.permission.RECORD_AUDIO`

Nanu CI rejects broad storage, location, contacts, SMS, call-log, all-packages and overlay permission markers.

## Product boundary reminder

Nanu Local AI does not contain Trading Lab, Paper Trading, trade journals, risk calculators, wallet connections or order execution. Current crypto-price and currency-rate answers are general read-only information lookups. Recheck the final Play Console declaration against the exact production AAB and current policy wording.

## Privacy-policy consistency

The public privacy policy must stay consistent with the exact production build. Update it if Nanu later adds or changes online-tool providers, analytics, ads, accounts, cloud sync, crash reporting, remote AI inference, the reporting backend, market providers, data retention or any other off-device data flow.
