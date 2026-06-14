# Nanu AI Trading Bot v5 — Premium UI System

This is the all-in-one Android APK source for Nanu AI Trading Bot.

## What changed in v5

- Uses a proper Android view layout instead of a cramped custom canvas for the whole app.
- Adds safe top spacing so the app title does not hide under the phone battery/signal bar.
- Cleans font hierarchy: app title, section titles, labels, numbers, tabs and buttons now use fixed sizes.
- Removes unreliable decorative text-symbol icons from the main UI and uses clear text buttons.
- Makes the top Settings control useful: it opens Security / Settings directly.
- Moves Paper / Demo / Testnet / Live mode selection into Security where it belongs.
- Adds large mode buttons and a live unlock flow requiring `UNLOCK LIVE`.
- Adds Manual Coin Mode and Auto Scalping Mode.
- Adds a professional Brain / ML Decision Room.
- Adds Binance API Health Check logic.
- Keeps Nanu's live emotion face: calm, profit smile, big-profit teeth smile, loss sad face, heavy-loss tears, panic alarm.
- Keeps the app internal: no Termux engine is required after install.

## Safety

Default mode is Paper. Live mode stays locked until API keys are added and the user confirms the live unlock phrase.

Never enable Binance withdrawal permission for a trading bot API key.

## Build through GitHub Actions

```bash
gh workflow run build-apk.yml -R joyarnold16/NanuAPKCloud --ref main

sleep 10

RUN_ID=$(gh run list -R joyarnold16/NanuAPKCloud --workflow "Build Nanu AI Trading Bot V5 APK" --limit 1 --json databaseId --jq '.[0].databaseId')

gh run watch "$RUN_ID" -R joyarnold16/NanuAPKCloud
```

## Download APK after green tick

```bash
mkdir -p /sdcard/Download/NanuAPK

gh release download nanu-ai-trading-bot-v5 \
  -R joyarnold16/NanuAPKCloud \
  -p "*.apk" \
  -D /sdcard/Download/NanuAPK \
  --clobber

ls -lh /sdcard/Download/NanuAPK
```

APK output:

```text
/sdcard/Download/NanuAPK/nanu-ai-trading-bot-v5-debug.apk
```
