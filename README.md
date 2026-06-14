# Nanu God Mode — All-In-One APK Safe Edition

This is the native Android version of Nanu. It runs inside the APK as a foreground service and does not need Termux.

## What is included

- Native Android Java app
- Start / Stop / Panic controls inside APK
- Foreground bot service
- Paper mode by default
- Demo / Testnet / Live mode fields
- Binance public candle scanner
- Binance signed market-order function, locked behind live safety gates
- EMA9 / EMA21 / RSI / MACD scalping strategy
- Stop-loss, take-profit, trailing stop, max hold time, daily loss guard
- SQLite trade journal and event log
- Telegram alert sender
- Bridge, Scanner, Brain, Journal, Security tabs
- GitHub Actions APK builder that publishes APK under Releases, not Artifacts

## Safety

God Mode means full control panel and safety doors loaded. It does not mean guaranteed profit.
The default mode is `paper`. Keep paper mode until signals, journal, risk rules, and app behavior are tested.

For Binance live keys:
- Never enable withdrawal permission.
- Use smallest balance first.
- Prefer IP restrictions where possible.
- Test demo/testnet before live.

## Build on GitHub

Push this project to GitHub, then run:

Actions → Build Nanu All In One APK → Run workflow

Download from:

Releases → Nanu All-In-One God Mode APK → nanu-all-in-one-god-mode-debug.apk

## How to use

1. Install APK.
2. Open Nanu God Mode.
3. Go to Security.
4. Keep mode as paper.
5. Save symbols and risk.
6. Tap START.
7. Watch Bridge, Journal, Scanner, and Brain.

No Termux is required for this version.
