# Nanu AI Trading Bot v5.4 — Live Gate Fix + Telegram Doctor + Safety Lock

This release fixes the strict LIVE gate bug and upgrades Telegram alerts.

## Fixed in v5.4

- LIVE mode can now be selected for API Doctor testing.
- LIVE auto-trading remains locked separately until API Doctor passes and user types `UNLOCK LIVE`.
- API Doctor status is mode-aware, so old TESTNET failure will not block a LIVE API check.
- API key/secret changes reset API Doctor status to avoid stale OK/failed results.
- Start/Stop/Panic now send phone alerts and Telegram alerts when configured.
- Telegram Doctor sends a real test message and shows exact Telegram HTTP result/error.
- Profit Guard, long sound, notification, vibration, and duplicate-profit detector are preserved.
- v5.1 no-flicker UI behavior is preserved.

## Safety rule

- LIVE can be selected to run API Doctor.
- LIVE trading cannot start until:
  - API key and secret are saved
  - API Doctor passes in LIVE mode
  - Spot trading permission is detected
  - User manually unlocks with `UNLOCK LIVE`

Keep Binance withdrawals OFF always.

## Build

Push this source to GitHub and run:

```bash
gh workflow run build-apk.yml -R joyarnold16/NanuAPKCloud --ref main
```

Release tag: `nanu-ai-trading-bot-v5-4`
APK name: `nanu-ai-trading-bot-v5-4-debug.apk`
