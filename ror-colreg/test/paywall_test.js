/**
 * Regression test for the paywall's price states.
 *
 * Until v4.1 a billing failure produced no callback at all, so the paywall's
 * price box stayed empty forever and the buyer had nothing to act on. These
 * checks pin the three states the box can be in, and pin that a late failure
 * cannot blank out a price that has already loaded.
 *
 *   node test/paywall_test.js app/src/main/assets/index.html
 */
const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' });
  const errors = [];
  let pass = 0, fail = 0;
  const check = (name, ok, detail) => {
    (ok ? pass++ : fail++);
    console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ' — ' + detail : ''}`);
  };

  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  page.on('pageerror', e => errors.push('PAGEERROR: ' + e.message));
  page.on('console', m => { if (m.type() === 'error') errors.push('CONSOLE: ' + m.text()); });

  // Stand in for the Android bridge: locked, so the paywall renders.
  await page.addInitScript(() => {
    window.AndroidBilling = {
      isPremiumUnlocked: () => false,
      purchasePremium: () => { window.__purchaseCalled = true; },
      restorePurchases: () => { window.__restoreCalled = true; },
    };
  });
  await page.goto('file://' + path.resolve(process.argv[2]));
  await page.waitForTimeout(250);
  await page.click('#mobileNav [data-view="exam"]');
  await page.waitForTimeout(250);

  const priceSel = '#view-exam .paywall-price';
  check('paywall renders on a premium view', await page.locator(priceSel).count() === 1);

  // 1. Before any callback: a placeholder, not an empty box.
  let s = await page.evaluate(sel => {
    const el = document.querySelector(sel);
    return { text: el.textContent.trim(), unavailable: el.classList.contains('unavailable') };
  }, priceSel);
  check('shows a placeholder before billing answers', s.text.length > 0 && s.unavailable, JSON.stringify(s.text));

  // 2. A failure replaces the placeholder with the actual reason.
  const reason = 'Google Play billing is not available on this device.';
  await page.evaluate(r => window.onPremiumPriceUnavailable(r), reason);
  s = await page.evaluate(sel => {
    const el = document.querySelector(sel);
    return { text: el.textContent.trim(), unavailable: el.classList.contains('unavailable') };
  }, priceSel);
  check('a billing failure surfaces its reason in the price box', s.text === reason && s.unavailable, s.text);

  // 3. A price supersedes the reason and drops the small-text styling.
  await page.evaluate(() => window.onPremiumPriceLoaded('₹499.00'));
  s = await page.evaluate(sel => {
    const el = document.querySelector(sel);
    return { text: el.textContent.trim(), unavailable: el.classList.contains('unavailable') };
  }, priceSel);
  check('a loaded price replaces the reason', s.text === '₹499.00' && !s.unavailable, s.text);

  // 4. A later transient failure must not blank a price the buyer is reading.
  await page.evaluate(() => window.onPremiumPriceUnavailable('Could not reach Google Play.'));
  s = await page.evaluate(sel => document.querySelector(sel).textContent.trim(), priceSel);
  check('a later failure does not clobber a loaded price', s === '₹499.00', s);

  // 5. A paywall rendered afterwards on another view reuses the loaded price.
  await page.click('#mobileNav [data-view="oral"]');
  await page.waitForTimeout(250);
  s = await page.evaluate(() => {
    const el = document.querySelector('#view-oral .paywall-price');
    return el ? el.textContent.trim() : '(no paywall)';
  });
  check('a newly rendered paywall reuses the known price', s === '₹499.00', s);

  // 6. Restore that finds nothing must say so rather than appearing to do nothing.
  await page.evaluate(() => {
    window.__toasts = [];
    document.querySelector('#view-oral [data-paywall-action="restore"]').click();
  });
  await page.waitForTimeout(100);
  const restoreCalled = await page.evaluate(() => !!window.__restoreCalled);
  await page.evaluate(() => window.onPremiumStateChanged(false));
  await page.waitForTimeout(150);
  const toastText = await page.evaluate(() =>
    [...document.querySelectorAll('.toast, #toast, [class*=toast]')].map(t => t.textContent.trim()).join(' | '));
  check('Restore reaches the bridge', restoreCalled);
  check('Restore finding nothing tells the user', /No previous purchase/i.test(toastText), toastText || '(no toast)');

  // 7. Unlocking clears the paywall.
  await page.evaluate(() => {
    window.AndroidBilling.isPremiumUnlocked = () => true;
    window.onPremiumStateChanged(true);
  });
  await page.waitForTimeout(250);
  const overlays = await page.evaluate(() => document.querySelectorAll('.paywall-overlay').length);
  check('unlocking removes every paywall overlay', overlays === 0, `${overlays} left`);

  await browser.close();
  console.log(errors.length ? 'ERRORS: ' + errors.join(' | ') : 'NO PAGE ERRORS');
  console.log(`${pass} passed, ${fail} failed`);
  process.exit(fail === 0 && errors.length === 0 ? 0 : 1);
})();
