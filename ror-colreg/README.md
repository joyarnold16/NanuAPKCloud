# ROR Visual Deck (ROR / COLREG Android App)

Offline Android training app for COLREG/ROR: rules, lights and shapes, sound
signals, distress signals, IALA buoyage, a vessel-type/day-night bridge
simulator with live give-way/stand-on verdicts, a radar/CPA plotting tool,
exercises, and an exam question bank. The whole app is a single self-contained
`index.html` (inline CSS/JS, no external assets) shown in a `WebView`.

This folder is the sole source of the app and is built independently from the
Nanu AI Trading Bot project by `.github/workflows/build-ror-colreg.yml`
(`gradle -p ror-colreg assembleDebug`).

## Layout

- `app/src/main/assets/index.html` — the entire app UI/logic
- `app/src/main/java/.../MainActivity.java` — thin `WebView` host
- `app/src/main/AndroidManifest.xml`, `app/src/main/res/` — Android shell

## Build

```
gradle -p ror-colreg assembleDebug
```

Debug builds need no secrets. A signed release `.aab` for Play Store is
also wired up (`gradle -p ror-colreg bundleRelease`) but needs a release
keystore supplied via the `ROR_RELEASE_*` environment variables/secrets —
see `PLAY_STORE_CHECKLIST.md` for exact setup steps.

## Publishing

See `PRIVACY.md` (privacy policy, ready to host) and
`PLAY_STORE_CHECKLIST.md` (what's automated vs. what still needs a human in
Play Console) before submitting to Google Play.
