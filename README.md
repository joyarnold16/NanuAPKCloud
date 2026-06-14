# Nanu AI Trading Bot v5.2 — Profit Guard + Alert System

Nanu AI Trading Bot v5.2 keeps the v5.1 no-flicker UI and adds a safety layer for scalping:

- Daily Profit Target: 50 / 100 / 150 / custom USDT
- Auto stop when target is reached
- Phone notification on guard stop
- Long alarm sound and vibration for critical alerts
- Duplicate / repeated P&L guard to catch stale API, frozen P&L or loop errors
- Test Long Alert button inside Security
- Sound ON/OFF and Notification ON/OFF controls
- Profit Guard card on Bridge screen
- Security → Profit Guard & Alerts controls

Important safety note: live trading remains locked. Test everything in Paper mode first.

## Build with GitHub Actions

```bash
cd ~/nanu_ai_trading_bot_v5_2_source

git init
git branch -M main
git add .
git commit -m "Nanu AI Trading Bot v5.2 profit guard alerts"

git remote remove origin 2>/dev/null || true
git remote add origin https://github.com/joyarnold16/NanuAPKCloud.git

git fetch origin main
git push --force-with-lease origin main
```

Run:

```bash
gh workflow run build-apk.yml -R joyarnold16/NanuAPKCloud --ref main
```

Download release:

```bash
mkdir -p /sdcard/Download/NanuAPK
gh release download nanu-ai-trading-bot-v5-2 -R joyarnold16/NanuAPKCloud -p "*.apk" -D /sdcard/Download/NanuAPK --clobber
```
