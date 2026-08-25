// Nanu Local AI — Google Apps Script safety-report endpoint
//
// Deploy this as a Web app that executes as the developer and allows access to
// anyone. Before deployment, create a Script Property named
// NANU_SUPPORT_EMAIL containing the mailbox that should receive reports.
//
// Nanu sends JSON POST bodies. Reports are emailed to the configured mailbox;
// this sample does not create a public database or expose a report listing.

const APP_NAME = 'Nanu Local AI';
const MAX_DETAILS = 8000;
const MAX_CATEGORY = 80;

function doGet() {
  return json_({ ok: true, service: 'Nanu AI report endpoint' });
}

function doPost(e) {
  try {
    const supportEmail = PropertiesService.getScriptProperties()
      .getProperty('NANU_SUPPORT_EMAIL');
    if (!supportEmail || supportEmail.indexOf('@') < 1) {
      return json_({ ok: false, error: 'Support email is not configured' });
    }

    const raw = e && e.postData && e.postData.contents ? e.postData.contents : '';
    if (!raw || raw.length > 20000) {
      return json_({ ok: false, error: 'Invalid report body' });
    }

    const data = JSON.parse(raw);
    const reportId = clean_(data.report_id, 80);
    const category = clean_(data.category || 'AI output', MAX_CATEGORY);
    const details = clean_(data.details, MAX_DETAILS);
    const app = clean_(data.app || APP_NAME, 80);
    const createdAt = Number(data.created_at_ms || Date.now());

    if (!reportId || !details) {
      return json_({ ok: false, error: 'Missing report fields' });
    }

    // Deduplicate accidental retries for six hours.
    const cache = CacheService.getScriptCache();
    const dedupeKey = 'nanu-report-' + reportId;
    if (cache.get(dedupeKey)) {
      return json_({ ok: true, report_id: reportId, duplicate: true });
    }
    cache.put(dedupeKey, '1', 21600);

    const when = new Date(isFinite(createdAt) ? createdAt : Date.now());
    const subject = '[Nanu AI Report] ' + category + ' — ' + reportId.substring(0, 8);
    const body = [
      'Nanu Local AI safety report',
      '',
      'Reference: ' + reportId,
      'App: ' + app,
      'Category: ' + category,
      'Submitted: ' + when.toISOString(),
      '',
      'Reported content / details:',
      details,
      '',
      'This email was generated only after a user explicitly tapped Submit to developer in Nanu.'
    ].join('\n');

    MailApp.sendEmail({
      to: supportEmail,
      subject: subject,
      body: body,
      name: APP_NAME + ' Safety Reports'
    });

    return json_({ ok: true, report_id: reportId });
  } catch (err) {
    console.error(err && err.stack ? err.stack : err);
    return json_({ ok: false, error: 'Could not process report' });
  }
}

function clean_(value, limit) {
  return String(value == null ? '' : value)
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, '')
    .trim()
    .substring(0, limit);
}

function json_(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
