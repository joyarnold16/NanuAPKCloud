# Nanu AI Safety Report Backend — Google Apps Script

This is the simplest no-server-cost backend for Nanu's in-app **Report → Submit to developer** feature.

## What it does

`nanu_ai_report_backend.gs` accepts Nanu's HTTPS JSON POST and emails the submitted safety report to the developer support mailbox. It does not expose a public report list and does not silently collect chat data. Nanu sends a report only after the user explicitly taps **Submit to developer**.

## Deploy once

1. Open Google Apps Script and create a new standalone project.
2. Replace the default code with `nanu_ai_report_backend.gs` from this folder.
3. Open **Project Settings → Script properties** and add:
   - Property: `NANU_SUPPORT_EMAIL`
   - Value: the support mailbox that should receive Nanu safety reports.
4. Run any function once from the editor if Google asks you to authorize MailApp.
5. Choose **Deploy → New deployment → Web app**.
6. Set **Execute as: Me**.
7. Set access so Nanu users can POST to the web app without signing into your Google account (the exact label can vary by account, commonly **Anyone**).
8. Deploy and copy the HTTPS `/exec` web-app URL.
9. Test the URL in a browser. It should return JSON similar to:
   `{"ok":true,"service":"Nanu AI report endpoint"}`

## Connect the Android release

In the GitHub repository, configure the repository variable:

- `NANU_REPORT_ENDPOINT` = the deployed HTTPS `/exec` URL

Also configure:

- `NANU_SUPPORT_EMAIL` = the same public support mailbox

The Play release workflow refuses to build if these production values are missing.

## Operational notes

- Keep the Apps Script deployment active while Nanu is published.
- Reports are delivered to the configured mailbox, so define and follow a reasonable moderation/retention/deletion process for that mailbox.
- Do not use a personal mailbox you are unwilling to publish as the app's support contact.
- The sample deduplicates the same report ID for six hours to reduce accidental retry duplicates.
- Apps Script and email sending have Google account quotas. If Nanu grows beyond those limits, move the same JSON contract to a managed backend such as Cloud Run, Cloudflare Workers, Firebase, or another maintained HTTPS service.
- If you replace the endpoint, update the privacy policy and Play Data Safety answers if the data handling changes.
