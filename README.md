# Nanu AI Trading Bot v6.0 — Controlled Live Scalping System

This package upgrades Nanu from dry-run-only into a **controlled live scalping control system** with strict gates. It does **not** promise profit and does **not** remove market risk.

## Added in v6.0

- Controlled Live Scalping dashboard card
- Binance Compliance Guard
- Manual confirmed micro BUY
- Manual confirmed SELL / close by quantity
- Binance `/order/test` mode ON by default
- Real micro order arming gate with typed confirmation
- Real order mode resets after one real order
- Full-auto trading remains locked behind proof gate
- Semi-auto approval switch
- Rate-limit lock for Binance 429 / 418 danger responses
- Error Doctor for Binance status codes
- Safe Telegram `/status` message template
- Backup export without secrets
- Safety Report v2 with v6.0 status
- Live Face Expression Engine preserved
- Telegram ON/OFF + Quiet Mode preserved
- Profit Guard, Panic Guard, API Doctor, and trusted IP helper preserved

## Safety design

Nanu v6.0 blocks unsafe behavior:

- No withdrawal permission needed
- No order spam
- No wash trading
- No fake volume
- No spoofing/cancel spam
- No blind full auto by default
- No real order without typed confirmation
- No live order after Binance 429/418 until safety reset

## Build output

GitHub Actions workflow: `Build Nanu AI Trading Bot V6.0 APK`

Release tag: `nanu-ai-trading-bot-v6-0`

APK name: `nanu-ai-trading-bot-v6-0-debug.apk`

## Important note

No trading bot can guarantee 100% profit or zero bugs. Use test order mode first, then tiny real orders only after checking every safety gate.
