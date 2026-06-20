# Nanu AI Trading Bot v6.3 Spot Executor

Nanu v6.3 is an Android control console plus a separate private VPS service for Binance Spot automation. The Android app does not hold the Binance secret for automatic trading. The VPS does.

No strategy can guarantee profit, avoid every loss, or make a live account safe without careful verification.

## What v6.3 implements

- Binance Spot only: no Futures and no leverage
- Four approved pairs only: `BTCUSDT`, `ETHUSDT`, `BNBUSDT`, `SOLUSDT`
- Maximum four new entries per UTC day and one open position at a time
- Custom quote amount, bounded to at least 5 USDT
- Closed 1-minute candles with EMA 9/21, RSI 14, ATR, volume, and trend filters
- A bounded learning bias based on recorded closed trades; it cannot override the risk limits
- Daily loss stop, Binance exchange filters, notional/lot/tick rounding, rate-limit lock, and persistent journal
- Real market buy is followed by a Binance SELL OCO target and stop order. If protection cannot be placed, the server attempts an emergency sell and stops.
- Recovery logic for a restarted server with a persisted pending market order
- HTTPS-only app controls protected by a separate random control token

## Deliberate safety boundaries

- Automatic live execution defaults to off, even if the server is configured for `live` mode.
- The phone cannot turn on `NANU_AUTO_LIVE_ENABLED`; that requires an explicit server environment change.
- The app cannot enter Binance API credentials for the VPS flow. Keep the Binance API key and secret only in `server/.env`.
- Create a dedicated Binance API key with Spot trading only, withdrawals disabled, and an IP restriction matching the VPS public IP.
- Binance API keys, Binance secrets, the VPS `.env`, keystores, and control tokens must never be committed or uploaded to GitHub Actions secrets.

## Server deployment

The server needs a small Linux VPS with Docker, a domain name, and HTTPS. The included Compose file listens only on `127.0.0.1:8080`; expose it to the app through a TLS reverse proxy such as Caddy.

```bash
cd server
cp .env.example .env
chmod 600 .env
openssl rand -base64 48 | tr -d '\n'
# Put that value in NANU_CONTROL_TOKEN in .env, then set NANU_MODE=paper.
docker compose up -d --build
docker compose logs -f
```

Use `server/Caddyfile.example` after your DNS record points at the VPS. Do not publish the Node port directly to the internet.

Required progression:

1. Start in `NANU_MODE=paper`; connect the APK and verify the status, amount, pairs, daily cap, start/stop, and pause controls.
2. Set `NANU_MODE=test`; use the restricted key and verify Binance Test Order behavior and the server journal. This validates request signing without spending funds.
3. Review Binance order history and server logs. Then set `NANU_MODE=live` while leaving `NANU_AUTO_LIVE_ENABLED=false`.
4. Only after those checks, set `NANU_AUTO_LIVE_ENABLED=true`, restart the container, sync the app, and begin with the smallest amount the pair permits.

See [server deployment notes](server/README.md) for the exact environment fields and control endpoints.

## Android APK build

Workflow: `Build Nanu AI Trading Bot Professional APK`

Release tag: `nanu-ai-trading-bot-v6-3-spot-executor`

APK: `nanu-ai-trading-bot-v6-3-spot-executor.apk`

Required GitHub Actions secrets for APK signing only:

- `NANU_RELEASE_KEYSTORE_B64`
- `NANU_RELEASE_KEYSTORE_PASSWORD`
- `NANU_RELEASE_KEY_ALIAS`
- `NANU_RELEASE_KEY_PASSWORD`

The workflow runs Node executor tests, Android unit tests, lint, and the signed release build.
