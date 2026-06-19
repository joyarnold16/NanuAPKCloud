# Nanu AI Trading Bot v6.1 Professional

Nanu is a Binance Spot trading control app with strict live gates, manual micro-order execution, portfolio equity sync, Telegram alerts, and a progressive learning memory. It does not promise profit and it does not remove market risk.

## Professional v6.1 upgrades

- Real Binance Spot portfolio equity sync from signed `/api/v3/account` balances
- USDT portfolio valuation using Binance ticker prices
- Spot equity card, top assets, sync age, and portfolio warnings
- Live unlock triggers portfolio sync automatically
- Fresh Spot portfolio sync required before live/test order submission
- Binance `exchangeInfo` symbol-rule checks before order submission
- JSON parsing for Binance account/server-time responses
- API key, API secret, Telegram token, and chat ID encrypted with Android Keystore
- App PIN stored as a one-way PBKDF2 hash
- Progressive learning memory visible in Brain and Safety Report
- Signed release workflow using GitHub Secrets

## Safety design

Nanu v6.1 blocks unsafe behavior by default:

- No withdrawal permission needed
- No order spam
- No wash trading
- No fake volume
- No spoofing/cancel spam
- No blind full auto by default
- No real order without typed confirmation
- No live/test order without recent Spot portfolio sync
- No live order after Binance 429/418 until safety reset

## GitHub release build

Workflow: `Build Nanu AI Trading Bot Professional APK`

Release tag: `nanu-ai-trading-bot-v6-1-professional`

APK name: `nanu-ai-trading-bot-v6-1-professional.apk`

Required repository secrets:

- `NANU_RELEASE_KEYSTORE_B64`
- `NANU_RELEASE_KEYSTORE_PASSWORD`
- `NANU_RELEASE_KEY_ALIAS`
- `NANU_RELEASE_KEY_PASSWORD`

The keystore must not be committed to the repository. Store the Base64-encoded keystore in GitHub Secrets.

## Important note

No trading bot can guarantee 100% profit or zero bugs. Use test order mode first, keep order sizes tiny, keep withdrawals disabled on the Binance API key, and verify Binance order history after every real order.
