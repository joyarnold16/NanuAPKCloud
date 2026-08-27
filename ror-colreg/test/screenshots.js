/**
 * Regenerates the Play Store phone screenshots from the current index.html.
 *
 *   node shots.js app/src/main/assets/index.html /out/dir
 *
 * Two things this handles that hand-capturing does not:
 *
 * - The premium views render their paywall unless the app thinks it is
 *   unlocked, so the dev-unlock key is set before load.
 * - The top bar is translucent, so whatever is scrolled underneath shows
 *   through. Large headings ghosting behind it read as a rendering bug in a
 *   store listing, so frameAt() nudges the scroll until a heading is not the
 *   thing sitting under the bar.
 */
const { chromium } = require('playwright');
const path = require('path');
const OUT = process.argv[3];

const setControls = vals => {
  for (const [id, v] of Object.entries(vals)) {
    const el = document.getElementById(id);
    if (!el) { console.log('missing control ' + id); continue; }
    el.value = v;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }
};

// Put `sel` at `top` px, then step down until no text sits behind the top bar.
// The bar is semi-transparent, so text behind it ghosts through and reads as a
// rendering fault in a store listing.
//
// Two earlier probes gave false "clear" readings and are worth not repeating:
// elementsFromPoint stops at the topmost element and reports clear when that
// element is a container, and scoping the query to .workspace missed the
// headings entirely because they are not inside it. This walks every leaf that
// carries text and tests the rectangle, excluding the bar itself and the
// off-canvas sidebar (which sits at negative x on a phone viewport and would
// otherwise never let the loop terminate).
// Park `sel` exactly `top` px down, no searching. Use when the right framing is
// known: a heading sitting fully below the bar reads fine, whereas the same
// heading half-swallowed by a translucent bar looks like a rendering fault, and
// no amount of nudging hides it when it sits directly above the content.
const frameExact = (sel, top) => {
  const el = document.querySelector(sel);
  if (!el) return 'missing ' + sel;
  const y = Math.max(0, el.getBoundingClientRect().top + window.scrollY - top);
  window.scrollTo({ top: y, behavior: 'instant' });
  return 'parked at y=' + Math.round(y);
};

const frameAt = (sel, top) => {
  const el = document.querySelector(sel);
  if (!el) return 'missing ' + sel;
  const bar = document.querySelector('.topbar');
  const barH = bar ? bar.getBoundingClientRect().height : 82;
  const base = el.getBoundingClientRect().top + window.scrollY - top;
  const vw = document.documentElement.clientWidth;

  const ghosts = () => {
    const out = [];
    for (const e of document.querySelectorAll('body *')) {
      if (e.children.length || e.closest('.topbar') || e.closest('.sidebar')) continue;
      const t = (e.textContent || '').trim();
      if (!t) continue;
      const r = e.getBoundingClientRect();
      if (r.right <= 0 || r.left >= vw) continue;          // off-canvas
      if (r.bottom <= 2 || r.top >= barH - 2) continue;
      // Something always sits behind a sticky bar, and small controls showing
      // faintly through 78%-opaque navy is just how the app looks on a device.
      // Only large type ghosts badly enough to read as a rendering fault.
      if (parseFloat(getComputedStyle(e).fontSize) < 20) continue;
      out.push(e.tagName.toLowerCase() + ':' + t.slice(0, 16));
    }
    return out;
  };

  // Somewhere in a continuous page there may be no clean offset at all, so
  // score every candidate and return to the best one. The earlier version left
  // the page wherever the last iteration happened to land, which framed the
  // shot worse than not trying.
  let best = { score: Infinity, y: base, why: [] };
  for (let step = 0; step <= 12; step++) {
    const y = Math.max(0, base + step * 36);
    window.scrollTo({ top: y, behavior: 'instant' });
    const g = ghosts();
    if (g.length < best.score) best = { score: g.length, y, why: g };
    if (!g.length) break;
  }
  window.scrollTo({ top: best.y, behavior: 'instant' });
  return best.score === 0
    ? 'clear at y=' + Math.round(best.y)
    : 'best of ' + best.score + ' at y=' + Math.round(best.y) + ': ' + best.why.join(' | ');
};

(async () => {
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' });
  const file = 'file://' + path.resolve(process.argv[2]);

  const shots = [
    { name: '1-home', view: 'home', settle: 900 },
    { name: '2-encounters', view: 'encounters', settle: 1400,
      // The default is a head-on at 10.8 NM, which pins both vessels to the rim
      // and leaves the plot looking empty. A starboard crossing inside the scale
      // shows the give-way verdict the module exists for.
      setup: { targetBearing: 45, targetRange: 6, targetCourse: 300, ownCourse: 0 },
      frame: ['.plot-wrap', 150] },
    { name: '3-lights', view: 'lights', settle: 1000, frameExact: ['.visual-stage', 78] },
    { name: '4-signals', view: 'signals', settle: 900, play: true },
  ];

  for (const s of shots) {
    const page = await browser.newPage({ viewport: { width: 412, height: 915 }, deviceScaleFactor: 2 });
    await page.addInitScript(() => localStorage.setItem('ror_v3_premium_dev_unlock', 'true'));
    page.on('pageerror', e => console.log('  PAGEERROR:', e.message));
    await page.goto(file);
    // Headless Chromium renders backdrop-filter over scrolled content as a
    // white band rather than a blur. On a device the bar blurs dark content and
    // reads as the solid dark it already declares underneath, so dropping the
    // filter for the capture matches the real appearance instead of faking it.
    await page.addStyleTag({ content:
      '.topbar,.paywall-overlay{backdrop-filter:none!important;-webkit-backdrop-filter:none!important}'
      // The app scrolls smoothly, so window.scrollTo animates and a rect read
      // straight after it still reports the pre-scroll position. That made the
      // ghost probe below report a clean frame at every offset it tried.
      + '*{scroll-behavior:auto!important}' });
    await page.waitForTimeout(500);

    await page.click(`#mobileNav [data-view="${s.view}"]`);
    await page.waitForTimeout(s.settle);

    if (s.setup) { await page.evaluate(setControls, s.setup); await page.waitForTimeout(700); }
    if (s.play) {
      const b = page.locator('[data-play-signal]').first();
      if (await b.count()) { await b.click(); await page.waitForTimeout(400); }
    }
    const framing = s.frame || s.frameExact;
    if (framing) {
      const fn = (s.frame ? frameAt : frameExact).toString();
      const under = await page.evaluate(
        ([f, sel, top]) => new Function('return ' + f)()(sel, top),
        [fn, framing[0], framing[1]]);
      await page.waitForTimeout(700);
      console.log(`  ${s.name}: under top bar -> ${under}`);
    }

    const out = `${OUT}/store-${s.name}.png`;
    await page.screenshot({ path: out });
    console.log('  wrote', out);
    await page.close();
  }
  await browser.close();
})();
