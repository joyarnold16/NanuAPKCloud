const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const errors = [];
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' });
  let allPass = true;

  for (const vp of [{ w: 1400, h: 1000, label: 'desktop', expectTop: 82 },
                    { w: 390, h: 844, label: 'phone', expectTop: 70 }]) {
    const page = await browser.newPage({ viewport: { width: vp.w, height: vp.h } });
    page.on('pageerror', e => errors.push(`${vp.label} PAGEERROR: ` + e.message));
    page.on('console', m => { if (m.type() === 'error') errors.push(`${vp.label} CONSOLE: ` + m.text()); });
    await page.goto('file://' + path.resolve(process.argv[2]));
    await page.waitForTimeout(250);
    await page.click(vp.w <= 920 ? '#mobileNav [data-view="signals"]' : '[data-view="signals"]');
    await page.waitForTimeout(250);

    // Start a signal so there is a live playhead worth keeping on screen.
    await page.locator('[data-play-signal]').first().click();
    await page.waitForTimeout(150);

    const before = await page.evaluate(() => Math.round(document.querySelector('.signal-player').getBoundingClientRect().top));

    // Scroll well down the signal list, as if hunting for another signal.
    await page.evaluate(() => window.scrollTo(0, 1200));
    await page.waitForTimeout(300);

    const after = await page.evaluate(() => {
      const p = document.querySelector('.signal-player').getBoundingClientRect();
      const tb = document.querySelector('.topbar').getBoundingClientRect();
      return { top: Math.round(p.top), bottom: Math.round(p.bottom), topbarBottom: Math.round(tb.bottom) };
    });

    const stuck = Math.abs(after.top - vp.expectTop) <= 2;
    const noOverlap = after.top >= after.topbarBottom - 2;
    const onScreen = after.bottom > 0 && after.top < vp.h;
    // The timeline itself must still be visible, not just the panel's top edge.
    const timelineVisible = await page.evaluate((vh) => {
      const t = document.querySelector('.signal-timeline').getBoundingClientRect();
      return t.top >= 0 && t.bottom <= vh;
    }, vp.h);

    console.log(`${vp.label}: top ${before} -> ${after.top} after scrolling (expected ${vp.expectTop})`);
    console.log(`   stuck below topbar: ${stuck} | no topbar overlap: ${noOverlap} | on screen: ${onScreen} | timeline visible: ${timelineVisible}`);
    if (!(stuck && noOverlap && onScreen && timelineVisible)) allPass = false;

    await page.screenshot({ path: `${process.argv[3]}-${vp.label}.png` });
    await page.close();
  }

  await browser.close();
  console.log(errors.length ? 'ERRORS: ' + errors.join(' | ') : 'NO ERRORS');
  console.log(allPass ? 'ALL STICKY CHECKS PASS' : 'SOME CHECKS FAILED');
})();
