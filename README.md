# Nanu AI Trading Bot v6.4 Tablet Edition

Nanu is an Android Binance Spot assistant designed to run directly on a private tablet. It does not require a VPS, executor URL, or executor control token.

No strategy can guarantee profit, avoid every loss, or make real-money trading risk-free.

## What it does

- Binance Spot only; no Futures and no leverage
- Direct encrypted on-device storage for the Binance API key and secret
- Direct Spot equity and balance sync from the Binance account endpoint
- Four approved pairs only: `BTCUSDT`, `ETHUSDT`, `BNBUSDT`, and `SOLUSDT`
- Closed one-minute candle scanner using EMA 9/21, RSI 14, volatility, and volume checks
- Automatic paper positions only; it does not place unattended real orders
- Configurable manual BUY amount and a separate local manual order limit
- Maximum four real BUY entries per day, with a cooldown and one-time real-order arm
- Binance Test Order mode for validating an order request without creating a fill
- After a confirmed real BUY fill, requests a Binance OCO sell list with a take-profit and stop-loss exit
- If the OCO request fails, attempts an emergency market sell and reports the result prominently
- App-wide PIN lock, Telegram/phone alerts, safety checklist, API Doctor, and an exportable non-secret safety report

## Real-money boundaries

1. The scanner may trade only the internal paper wallet automatically.
2. A real BUY needs LIVE mode, a successful API Doctor, fresh balance sync, the safety checklist, a dry-run, a typed arm phrase, and a typed confirmation.
3. A manually confirmed SELL is treated as an exit path and is not blocked by the daily BUY cap or entry cooldown.
4. Always verify a real BUY, the OCO list, and any emergency sell in Binance order history and Open Orders.

## Binance API key setup

Create a dedicated Binance API key for this tablet:

- Enable Spot trading only.
- Keep withdrawals disabled.
- Do not enable Futures, margin, transfers, or any permission you do not need.
- Use IP restrictions only when the tablet has a stable public IP. Mobile IP addresses often change.
- Start in Binance Test Order mode. Then use the smallest amount the pair permits.

The app cannot prove the API-key withdrawal permission from the account response. You must check it in Binance API Management and confirm it in the app.

## Android APK build

GitHub workflow: `Build Nanu AI Trading Bot Professional APK`

Release tag: `nanu-ai-trading-bot-v6-4-tablet-edition`

APK: `nanu-ai-trading-bot-v6-4-tablet-edition.apk`

Required GitHub Actions signing secrets:

- `NANU_RELEASE_KEYSTORE_B64`
- `NANU_RELEASE_KEYSTORE_PASSWORD`
- `NANU_RELEASE_KEY_ALIAS`
- `NANU_RELEASE_KEY_PASSWORD`

The workflow runs regression tests, Android unit tests, lint, and a signed release build.
