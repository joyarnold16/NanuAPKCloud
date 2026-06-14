# Nanu Final Loaded APK — Feature Map

This APK is the native Android cockpit for the Nanu Python engine running in Termux.
The engine remains the trading core; the APK connects to `http://127.0.0.1:8765`.

## Loaded screens

1. **Bridge**
   - Start bot
   - Stop bot
   - Panic close
   - Mode/status display
   - Open trades
   - Daily PnL
   - Equity pulse sparkline

2. **Scanner**
   - Watchlist chips
   - Last strategy signal
   - Confidence gauge
   - Recent signal feed

3. **Brain**
   - Scalping-only rule display
   - EMA + RSI + MACD brain map
   - Journal-learning placeholder/memory layer
   - Risk-shield explanation

4. **Journal**
   - Recent trades
   - Recent events/logs
   - Open/closed trade details

5. **Security**
   - Mode field: paper/demo/testnet/live
   - Binance API key field
   - Binance API secret field
   - Telegram bot token and chat ID
   - Symbols
   - Risk settings
   - Save Settings button
   - Open Web Dashboard button

## Safety

- Paper mode is the default.
- Empty secret fields do not erase saved secrets.
- Live mode requires config changes.
- Start with paper, then demo/testnet, then live only after long testing.

## How to build

Push this repo to GitHub, then open:

`Actions → Build Nanu Final Loaded APK → Run workflow`

Download artifact:

`nanu-final-loaded-debug-apk`

Inside it:

`nanu-final-loaded-debug.apk`
