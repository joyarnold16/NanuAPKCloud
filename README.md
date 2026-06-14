# Nanu AI Trading Bot v4 — Professional Cockpit

Nanu AI Trading Bot v4 is a native Android APK source package. It is designed to run as an all-in-one mobile app: UI + internal paper bot engine + scanner + Brain/ML explanation screen + journal + security/settings.

## Main features

- App name: **Nanu AI Trading Bot**
- Native Android Java application
- No Termux engine required after APK install
- Premium dark illuminated cockpit UI
- Safe top spacing below phone status bar
- Working top-right settings icon
- Bridge, Scanner, Brain, Journal, Security tabs
- Nanu compass face logo changes with P&L:
  - calm / idle face
  - profit smile
  - big profit teeth smile
  - loss sad face
  - heavy loss crying tears
- Paper mode default
- Demo, Testnet, Live modes in Security screen
- Live mode locked by checklist and `UNLOCK LIVE` confirmation
- Manual coin selection
- Auto scalping coin selection
- Binance API key and secret storage using SharedPreferences
- Binance API health check:
  - public ping
  - server time
  - signed account request if API key/secret are added
- Internal paper engine and simulated trades
- Start / Stop / Panic Close
- Professional Brain page:
  - market regime
  - signal reason
  - risk thought
  - indicator matrix
- Journal and Developer Console
- GitHub Actions release build, not artifact upload

## Important safety note

This project is paper-first. Live trading must stay locked until Binance API execution, symbol filters, minimum notional checks, order errors, balance logic, and panic close behavior are fully audited. Never enable withdrawal permission on a Binance API key.

## Build with GitHub Actions

Push this source into your GitHub repo and run:

```bash
gh workflow run build-apk.yml -R joyarnold16/NanuAPKCloud --ref main
sleep 10
RUN_ID=$(gh run list -R joyarnold16/NanuAPKCloud --workflow "Build Nanu AI Trading Bot V4 APK" --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch "$RUN_ID" -R joyarnold16/NanuAPKCloud
```

After the green tick, download APK:

```bash
mkdir -p /sdcard/Download/NanuAPK
gh release download nanu-ai-trading-bot-v4 \
  -R joyarnold16/NanuAPKCloud \
  -p "*.apk" \
  -D /sdcard/Download/NanuAPK \
  --clobber
ls -lh /sdcard/Download/NanuAPK
```

APK path:

```text
/sdcard/Download/NanuAPK/nanu-ai-trading-bot-v4-debug.apk
```

## How to use the app

1. Open **Nanu AI Trading Bot**.
2. Stay in **Paper** mode first.
3. Use **Scanner** to select Auto Scalping or Manual Coins.
4. Use **Security** to add API keys and run Binance API Health Check.
5. Use **Bridge** to Start / Stop / Panic.
6. Use **Brain** to understand why Nanu is accepting/rejecting trades.
7. Use **Journal** to review events and developer status.

