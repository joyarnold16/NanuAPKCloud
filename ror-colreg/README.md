# ROR Visual Deck (ROR / COLREG Android App)

Offline Android training app for COLREG/ROR: rules, lights and shapes, sound
signals, distress signals, IALA buoyage, a radar/CPA plotting simulator,
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

The output APK is unsigned/debug-signed only; a Play Store upload would need
a release keystore and a signed AAB, neither of which is configured yet.
