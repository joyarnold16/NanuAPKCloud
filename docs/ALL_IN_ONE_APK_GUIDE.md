# All-In-One APK Guide

This version moves the Nanu engine from Termux into the Android APK.

## Difference from older version

Old version:
- Termux ran `python main.py run`
- APK opened `http://127.0.0.1:8765`

New version:
- Android APK runs a foreground service
- UI and engine are in the same app
- No local web server
- No Termux required

## Tabs

- Bridge: status, mode, open trades, PnL, events
- Scanner: symbols, prices, signal logic
- Brain: strategy explanation and learning memory
- Journal: trades and event logs
- Security: Binance/Telegram/risk settings and live safety switches

## Modes

- paper: internal simulated trades only
- demo: Binance demo base URL, but orders require keys and safety switch
- testnet: Binance testnet base URL, but orders require keys and safety switch
- live: Binance live URL; orders blocked unless live risk checkbox and real orders checkbox are enabled

## Live trading warning

This app contains code that can send Binance market orders when configured. Keep live order switches off unless you fully understand the risk.
