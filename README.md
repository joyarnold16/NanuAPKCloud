# Nanu God UI APK

All-in-one Android APK source for Nanu God Mode UI.

This version does not need Termux to run the app after installation. It includes a native Android UI, an internal paper-mode bot loop, animated Nanu logo face, journal, scanner, brain, settings, and safety controls.

## UI feature: realtime Nanu face

The Nanu compass-logo face changes with performance:

- Profit: green/cyan glow, smiling face
- Big profit: big smile with teeth
- Loss: red glow, sad face
- Heavy loss: crying face with tears

## Safety

Default mode is Paper. Live trading must not be used with real funds until the order engine, exchange filters, API-key protection, and risk system are audited and tested.

## Build

Push this project to GitHub, then run `.github/workflows/build-apk.yml`.
The APK is published in GitHub Releases as `nanu-god-ui-debug.apk`.
