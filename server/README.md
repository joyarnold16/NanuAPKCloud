# Nanu Spot Executor

This service is the only component allowed to hold Binance API credentials for Nanu automatic trading. It controls a maximum of one protected Spot position and four daily entries across the approved pairs.

## Private environment

Copy `.env.example` to `.env`, set permissions to `600`, and set a unique `NANU_CONTROL_TOKEN` of at least 32 random characters. The Android app needs the HTTPS address and this control token; it does not need Binance credentials.

`NANU_MODE` values:

- `paper`: simulated position and journal only. Default.
- `test`: sends Binance `POST /api/v3/order/test`; no order is placed, but a restricted Spot API key is required.
- `live`: may submit actual Spot orders only when `NANU_AUTO_LIVE_ENABLED=true` is also present. Both values are server-only.

## Controls

All `/v1/*` endpoints require `Authorization: Bearer <NANU_CONTROL_TOKEN>`.

- `GET /health`: unauthenticated process health only.
- `GET /v1/status`: configuration, heartbeat, positions, decision log, and recent trades.
- `POST /v1/control/start`: starts the scan loop immediately.
- `POST /v1/control/stop`: stops new scans; active exchange OCO orders stay intact.
- `POST /v1/control/panic` with `{ "closePositions": false }`: pauses new entries while retaining active OCO protection.
- `POST /v1/control/panic` with `{ "closePositions": true }`: requests cancellation of protection and emergency market closes. Confirm Binance history after using it.
- `POST /v1/portfolio/sync`: returns the actual Spot portfolio valuation through the protected control channel. It requires `test` or `live` mode and the restricted Binance key.
- `POST /v1/config`: updates bounded amount, stop, target, daily-loss percentage, interval, signal confidence, and approved pairs.

The server persists decisions and positions in `server/data/` when Compose is used. Back up that directory securely. It contains trading activity metadata but never the API secret.

## Binance behavior

The service uses official Spot REST endpoints for symbol rules, closed candles, account state, market orders, and OCO exit placement. It stops when Binance returns 418 or 429, saves its state, and does not retry aggressively. The process has no withdrawal endpoint and should use an API key with withdrawals disabled.

The first live amount should be the smallest valid amount for the selected pair. Verify a successful entry creates both exchange-side OCO child orders before considering automatic operation trustworthy.
