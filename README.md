# Nanu AI Trading Bot v8.0 DEX Safety Paper

Nanu v8 is a local Android DEX research and paper-trading app. Its active application route does not expose Binance, API-key, Telegram, static-IP, or exchange OCO controls.

## Included

- Separate locally encrypted bot-wallet identity using Trust Wallet Core
- Public BNB Chain and Solana addresses derived from one recovery backup
- DEX Screener candidate discovery for the selected chain
- Hard local filters for liquidity, 24-hour volume, pair age, buy/sell activity, and extreme price movement
- Clear `BLOCKED`, `WATCHING`, and `QUALIFIED` decisions with the reason shown in the app
- Paper-only automatic positions, position monitor, daily trade cap, stop/target reference, pause, and panic stop
- Local foreground scanner and event history

## Important boundary

This release does **not** sign or broadcast PancakeSwap or Solana swap transactions. It will not spend a bot-wallet balance. Real DEX execution remains blocked until the complete wallet-signing, route-quoting, simulation, token-approval, transaction-reconciliation, and device-level test suite passes.

No token safety score can prove a token safe, guarantee an exit, or guarantee profit. Never enter an existing Trust Wallet recovery phrase into Nanu. If you create a Nanu bot wallet, back up its recovery phrase offline before funding it.

## Build

GitHub Actions creates the signed APK release:

- Tag: `nanu-ai-trading-bot-v8-0-dex-safety-paper`
- APK: `nanu-ai-trading-bot-v8-0-dex-safety-paper.apk`

The build needs the existing Android signing secrets and a GitHub Packages token only if GitHub's default workflow token cannot download the public Trust Wallet Core package:

- `NANU_RELEASE_KEYSTORE_B64`
- `NANU_RELEASE_KEYSTORE_PASSWORD`
- `NANU_RELEASE_KEY_ALIAS`
- `NANU_RELEASE_KEY_PASSWORD`
- Optional: `NANU_WALLET_CORE_TOKEN`
