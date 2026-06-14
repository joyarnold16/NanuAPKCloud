# Safety and Risk Notes

Nanu v1 is a bot framework, not a guarantee of profit.

## Important limits

- Crypto markets can move violently.
- API outages can delay orders.
- Market orders can slip.
- Demo/testnet behavior can differ from live liquidity.
- Strategy signals can fail in sideways or news-driven markets.
- A bug, network failure, wrong key permission, or wrong config can cause loss.

## Recommended Binance API permissions

Use Spot trading permission only if needed. Do not enable withdrawal permission for Nanu.

## Live-mode checklist

Before live:

- Paper mode tested.
- Demo/testnet tested.
- Small quote size.
- Stop-loss and daily loss guard enabled.
- Panic command tested.
- Telegram alerts working.
- `config.ini` not uploaded to GitHub.

## v1 trading style

Nanu v1 is long-only Spot scalping:

- no futures
- no leverage
- no shorts
- no martingale
- no grid averaging
- no revenge trading
