# Nanu paper hardening milestone 1

Scope: the Android DEX trading launcher and a portable DEX paper engine. Baseline: main commit `673e437`. ROR, local AI, wallet cryptography and the older Binance Spot server are outside this change. Real-money DEX execution remains unavailable. No mainnet router, signer, private-key reader or transaction broadcaster is added.

## Source audit and changes

| Baseline finding | Milestone behavior |
| --- | --- |
| `DexEngine.paperPosition` existed only in RAM. | A versioned ledger atomically commits the open position, cash, daily counters, panic state and completed history. Restart restores pair identity, quantities, stops, costs and pending exits. Corrupt/unwritable storage halts entries without resetting the account. |
| Stops depended on a token remaining in discovery results. Token-only matching could select a different pool. | A separate 15-second monitor requests the saved chain/pair/base-token identity. Discovery has its own worker. Pause, panic, empty discovery and the selected UI chain do not disable tracking. |
| Panic stopped all monitoring; Start cleared panic. The UI had no recovery control. | Panic durably requests an exit and blocks new entries. Clearing panic requires the position to be resolved and leaves entries paused. Delayed discovery callbacks cannot rearm a stopped generation. |
| Daily loss and slippage settings were not enforced. | UTC daily realized losses plus negative open P&L trigger a persistent daily halt. One-position/$100 exposure/$1 modeled stop-risk caps, finite settings, entry counts, loss streaks and cooldowns constrain entries. Clock rollback cannot reset counters. |
| Paper buys/sells filled at the last visible price with no costs. | Quantity and net proceeds include a 0.30% pool fee, 0.20% latency haircut, constant-product impact approximation, provider taxes and per-side gas assumptions ($0.15 BSC/$0.02 Solana). Round-trip flat-price trades lose money. Pending exits cannot bypass the stricter of entry-time and current slippage caps. |
| Candle names were inferred from percentage changes and activity ratios. | Closed OHLCV geometry drives hammer, shooting star, engulfing and star patterns. EMA12/26, Wilder RSI14/ATR14, MACD12/26/9 and a trailing volume ratio use real candles. EMA/MACD trend gates affect entries. |
| Liquidity/activity filters alone produced `QUALIFIED`. | GoPlus evidence is required. Missing, malformed, expired or unsupported evidence produces `WATCHING`/unknown, never a pass. Scores and chart patterns cannot override a hard gate. |
| Paper outcomes automatically adjusted trading settings. | The active ledger path no longer invokes `BotEvolution`. Historical records are retained as legacy; they are not treated as validation for strategy promotion. |
| Android was expected to provide uninterrupted execution. | The same account, safety, indicator and read-only data classes build as an independent Java NAS console application. A file lock prevents concurrent writers and atomic replacement persists state. Android service timeouts are surfaced explicitly. |

## Data and safety contract

DEX Screener provides discovery and exact-pair observations. `observedAtMs` is **response receipt time**, not proof of a fresh on-chain trade: the API does not provide a source timestamp for its USD price. Quotes older than 90 seconds locally, future observations, invalid numbers and identity mismatches cannot fill a paper exit. Provider caching can still hide stale underlying prices.

GeckoTerminal OHLCV uses the selected pool **and explicit token address**, USD pricing and ascending, contiguous closed bars. The adapter supports 1m/5m/15m/1h; this milestone trades on **5m only**, with at least 60 candles (up to 100 requested). It does not synthesize candles, fill gaps, or silently substitute another token/timeframe. A forming candle is excluded. Sparse pools and provider rate limits may therefore prevent all entries.

BSC checks include source availability, proxy/mint/owner powers, blacklist/whitelist/pause/anti-whale restrictions, tax modification, reported honeypot/sell restrictions, buy/sell taxes, holder concentration and LP evidence. Solana checks include mint/freeze/close powers, account state, mutable metadata, balance powers and Token-2022 fee/hook capabilities. Fee/hook extensions are conservatively rejected in this milestone. Solana addresses are case-sensitive and must decode to 32 bytes.

Limits are deliberately conservative: individual reported holders above 20%, aggregate top holders above 50%, taxes above 5%, or less than 90% reported LP burned/locked beyond seven days block eligibility. Selected-pool coverage is required. **Provider LP evidence is token-level and is not independently proven to describe that selected pool.** This remains a material limitation. The implementation does not independently audit bytecode, validate locker contracts/partial lock amounts, trace deployer/whale history, verify Token-2022 byte layouts, or simulate on-chain buy/sell transactions. Passing evidence is not proof that a token can be safely sold.

## Execution and recovery semantics

- Paper account starts at $1,000; the configurable trade amount is a ceiling, not a promised fill size. Position sizing reserves modeled entry/exit costs plus the configured stop loss against a $1 risk budget and the remaining daily loss budget.
- Only one position can exist. Net equity/cash is simulated. Historical pre-upgrade records are preserved; their zero-cost P&L is not retroactively recalculated. A position lost by an old version before this upgrade cannot be reconstructed from absent data.
- The price monitor estimates open liquidation P&L, including modeled exit costs. Daily risk uses realized P&L plus negative open P&L, conservatively retaining overnight unrealized losses. UTC rollover resets the daily count and daily loss latch; it does not erase positions, panic, a loss streak or cooldown. A three-loss streak requires explicit panic recovery. Loss cooldown is five minutes; profitable exits have a 30-second spacing floor.
- Exit requests survive restarts. A missing quote, excessive simulated slippage, unknown tax/security evidence or a reported sell restriction keeps the position open with a pending reason. Repeated successful callbacks cannot double-count a completed exit because the position and history update in one commit.
- A stop is not a guaranteed loss ceiling. Gaps, failed providers, disappearing liquidity and taxes can exceed the configured budget. Slippage caps may prevent the simulated exit entirely.
- Costs are an inspectable **approximation**, not an executable route quote. The model does not reproduce concentrated liquidity, MEV, route changes, priority-fee auctions or transaction failures after broadcast. Rejected preflight attempts do not pretend that a transaction was submitted or burn fictitious transaction gas.
- Android snapshots use synchronous SharedPreferences commit in one ledger key. Ordinary UI preferences are separate and cannot re-enable live execution. Reopening restores positions but requires an explicit Start for new entries. The service remains active on pause/panic; Android may still stop it, including data-sync foreground-service time limits. This does not promise 24/7 Android operation.
- Do not run Android and NAS as joint owners of an account. No cross-device synchronization, authenticated remote dashboard, key migration or production deployment is included. The NAS console is groundwork, not a deployed service.

## Validation

Portable tests cover financial restart/replay, durable panic, stale/wrong pair quotes, liquidity shock, loss limits, clock rollback, cooldown/streak, storage failure/corruption, costs, known indicator values, candle parsing/geometry, chain-specific security schemas and provider failure. NAS tests exercise actual file recovery and exclusive writer locking. Fixtures are synthetic and are not evidence of profitable trading or live provider completeness.

Commands:

```text
gradle -p nas test installDist
gradle testDebugUnitTest lintDebug assembleDebug
powershell -File tools/test-paper.ps1 -Dependencies <directory containing json.jar,junit.jar,hamcrest.jar>
```

The Android build retains the existing Trust Wallet Core 4.0.26 package dependency. It requires an authorized GitHub package credential (`NANU_WALLET_CORE_TOKEN` or `GITHUB_TOKEN`); no credential belongs in source or build artifacts. CI builds a debug APK and reports only, without publishing a release or enabling execution. Existing Android lint is non-blocking and must be reviewed separately from test success.

## Primary references

- [DEX Screener API](https://docs.dexscreener.com/api/reference): exact pair and chain-scoped token-pair discovery.
- [GeckoTerminal API](https://api.geckoterminal.com/docs/index.html): pool OHLCV.
- [GoPlus BSC/EVM response fields](https://docs.gopluslabs.io/reference/response-details) and [Solana response fields](https://docs.gopluslabs.io/reference/response-detail-1): nullable risk fields, authorities, fees and holder data.
