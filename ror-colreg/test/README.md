# Browser regression tests

DOM-level tests for the bundled web app, run against
`app/src/main/assets/index.html` in headless Chromium.

```
npm i playwright
node test/paywall_test.js        app/src/main/assets/index.html
node test/sticky_player_test.js  app/src/main/assets/index.html /tmp/shot
```

Two things to know before writing more of these:

- **The app code is IIFE-wrapped.** Nothing it defines is reachable from
  `page.evaluate` — no `go()`, no `playSignal`, no `audioCtx`. Drive it through
  the DOM (`#mobileNav [data-view="…"]`, `[data-play-signal]`) and assert on
  the DOM. The only globals are the ones the Android bridge calls by name:
  `window.onPremiumStateChanged`, `onPremiumPriceLoaded`,
  `onPremiumPriceUnavailable`, `onPremiumPurchaseError`.
- **The file must end in `.html`.** Chromium will not parse a `file://` URL
  with an unrecognised extension as HTML; it loads with no DOM, no scripts,
  and no error, which looks exactly like an app that failed to boot. Copying
  a baseline to `index.html.bak` and testing it will waste your afternoon.

`paywall_test.js` covers the price states the Android `BillingManager` drives.
Run it against a pre-v4.1 copy of `index.html` and it should fail on the empty
price box — that is the bug it exists to hold shut.
