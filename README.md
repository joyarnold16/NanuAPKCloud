# Nanu AI Trading Bot v5.6 — Controlled Live Dry-Run + Order Safety Engine

This is the APK source package for Nanu AI Trading Bot v5.6.

## Main purpose

v5.6 adds a controlled live dry-run layer before any real live order execution. It previews and validates an order plan using the app safety state, but it does **not** submit real Binance live orders.

## Added in v5.6

- Controlled Live Dry-Run mode
- Order Safety Engine
- Order Preview report
- Minimum notional check
- Quantity rounding simulation
- Slippage limit setting
- Order cooldown guard
- Max live trades/day guard
- First real-order confirmation gate preserved
- Live unlock wording changed to Live Dry-Run Gate
- Dry-run alerts to phone and Telegram
- Developer report updated with dry-run status

## Preserved from earlier versions

- v5.5 Final Live Safety Checklist
- One API Doctor dialog behavior
- Manual API-key withdrawals OFF confirmation
- Telegram Doctor PASS/FAIL
- Phone alert / long sound / vibration test
- v5.4 Live Gate Fix
- v5.3 API Doctor + Trusted IP Helper
- v5.2 Profit Guard + repeated-profit guard
- v5.1 no-flicker UI refresh

## Safety note

v5.6 is still a development build. Even when LIVE mode is selected and the dry-run gate is unlocked, this version only previews and validates order logic. Real live order placement must be added later only after audit and tiny controlled testing.

## Build output

GitHub Actions release tag: `nanu-ai-trading-bot-v5-6`

APK name: `nanu-ai-trading-bot-v5-6-debug.apk`
