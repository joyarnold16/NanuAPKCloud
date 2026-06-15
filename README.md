# Nanu AI Trading Bot v5.3 — API Doctor + Trusted IP Helper

This release keeps the v5.2 Profit Guard + Alert System and adds a safer Binance API setup flow.

## Added in v5.3

- API Doctor with clearer diagnosis
- Public IP helper for Binance trusted IP whitelist
- API Mode Guide: Paper / Demo / Testnet / Live key rules
- Detects private account access success/failure
- Detects read-only vs trading permission when Binance account endpoint succeeds
- Blocks Live auto trading until API Doctor confirms private access + spot trading permission
- Clear warning to keep withdrawal permission OFF
- Preserves v5.2 settings and Profit Guard preferences

## Safe Binance rule

- Paper: no API key required
- Demo: Binance Demo Trading key only
- Testnet: Binance Spot Testnet key only
- Live: real Binance key + trusted IP + Spot trading permission only

Keep Withdrawals OFF always.

## Build

Push this source to GitHub and run the workflow:

```bash
gh workflow run build-apk.yml -R joyarnold16/NanuAPKCloud --ref main
```

Release tag: `nanu-ai-trading-bot-v5-3`
APK name: `nanu-ai-trading-bot-v5-3-debug.apk`
