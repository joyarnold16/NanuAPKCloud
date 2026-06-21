package com.nanu.aitradingbot;

import java.util.HashMap;
import java.util.Map;

/**
 * Foreground-service automatic execution coordinator. It never decides a
 * position from an open candle and it will not submit a second entry while a
 * Binance OCO-protected position or an unresolved submitted order exists.
 */
public final class AutoExecutionEngine {
    public interface Callback { void done(String result); }

    private final AppStore store;
    private final NanuEngine engine;
    private boolean requestInFlight = false;
    private long lastReconcileMs = 0L;

    public AutoExecutionEngine(AppStore store, NanuEngine engine) {
        this.store = store;
        this.engine = engine;
    }

    public void start(Callback callback) {
        String blockers = store.autoStartBlockers();
        if (!blockers.isEmpty()) {
            callback.done("Automatic LIVE start is blocked:\n\n" + blockers);
            return;
        }
        BinanceClient.syncSpotPortfolio(store, ignored -> {
            if (!store.portfolioSyncOk) {
                callback.done("Automatic LIVE start is blocked: Spot portfolio sync failed. Check API Doctor and your connection.");
                return;
            }
            BinanceClient.verifyTrustedIp(store, ipResult -> {
                if (!ipResult.ok) {
                    callback.done("Automatic LIVE start is blocked:\n\n" + ipResult.report);
                    return;
                }
                store.autoRunning = true;
                store.autoPanic = false;
                store.autoStatus = "Automatic Spot executor running. Waiting for a closed-candle setup.";
                store.autoLastHeartbeatMs = System.currentTimeMillis();
                store.autoConsecutiveFailures = 0;
                store.save();
                engine.start();
                store.triggerAlert("Nanu Automatic Spot Bot Started", "Automatic LIVE execution is running for BTC, ETH, BNB and SOL. One protected position maximum; four entries maximum today.", true, "live");
                callback.done("Automatic LIVE Spot executor started. It will scan the four approved pairs and submit only a qualifying protected entry.");
            });
        });
    }

    public void stop(String reason) {
        store.autoRunning = false;
        store.autoLiveArmed = false;
        store.autoStatus = reason == null ? "Automatic executor stopped." : reason;
        store.save();
    }

    public void panic(String reason) {
        store.autoRunning = false;
        store.autoLiveArmed = false;
        store.autoPanic = true;
        store.autoStatus = reason == null ? "Panic stop active. Existing Binance OCO protection remains in place." : reason;
        store.save();
    }

    public void tick(boolean force) {
        if (!store.autoRunning || store.autoPanic || engine.panic || !engine.running) return;
        long now = System.currentTimeMillis();
        store.autoLastHeartbeatMs = now;
        if (requestInFlight) return;

        // Reconcile submitted orders and exchange-side OCO state before looking
        // for any new setup. This is also the restart recovery path.
        if (force || store.hasAutoPendingOrder() || store.hasAutoPosition() || now - lastReconcileMs >= 15_000L) {
            lastReconcileMs = now;
            requestInFlight = true;
            BinanceClient.reconcileAutomaticState(store, result -> {
                requestInFlight = false;
                if (!result.ok) {
                    haltForReview("Automatic executor halted: " + result.report);
                    return;
                }
                if (store.hasAutoPosition() || store.hasAutoPendingOrder()) return;
                dispatchScanIfDue(force);
            });
            return;
        }
        dispatchScanIfDue(force);
    }

    private void dispatchScanIfDue(boolean force) {
        if (!store.autoRunning || store.hasAutoPosition() || store.hasAutoPendingOrder()) return;
        long now = System.currentTimeMillis();
        long scanEveryMs = Math.max(30, store.scalperScanSeconds) * 1000L;
        if (!force && now - store.autoLastScanMs < scanEveryMs) return;
        requestInFlight = true;
        store.autoLastScanMs = now;
        scanPairs(0, new HashMap<String, ScalpingStrategy.Signal>());
    }

    private void scanPairs(int index, Map<String, ScalpingStrategy.Signal> signals) {
        if (!store.autoRunning || store.autoPanic) {
            requestInFlight = false;
            return;
        }
        if (index >= AutoTradingPolicy.PAIRS.length) {
            onScanComplete(signals);
            return;
        }
        String symbol = AutoTradingPolicy.PAIRS[index];
        BinanceClient.scanScalperSymbol(store, symbol, false, (signal, report) -> {
            if (signal != null) signals.put(symbol, signal);
            scanPairs(index + 1, signals);
        });
    }

    private void onScanComplete(Map<String, ScalpingStrategy.Signal> signals) {
        String selected = AutoTradingPolicy.chooseBestBuy(signals, store.autoMinConfidence);
        if (selected.isEmpty()) {
            requestInFlight = false;
            store.autoStatus = "Scan complete. No qualified BUY setup across the four approved pairs.";
            store.save();
            return;
        }
        ScalpingStrategy.Signal signal = signals.get(selected);
        store.scalperSymbol = selected;
        store.lastScalperPrice = signal.price;
        store.lastScalperSignal = signal.action.name();
        store.lastScalperConfidence = signal.confidence;
        store.lastScalperReport = signal.report(selected, store.stopLoss, store.takeProfit);
        store.autoStatus = "Qualified " + selected + " BUY setup at " + signal.confidence + "/100. Verifying device IP before protected entry.";
        store.save();
        // A device can change networks after the session starts. Verify the
        // external IP again immediately before an exchange order is submitted.
        BinanceClient.verifyTrustedIp(store, ipResult -> {
            if (!ipResult.ok) {
                requestInFlight = false;
                haltForReview("Automatic entry blocked by device IP check: " + ipResult.report);
                return;
            }
            if (!store.autoRunning || store.autoPanic || engine.panic) {
                requestInFlight = false;
                return;
            }
            BinanceClient.placeAutomaticMarketBuy(store, selected, signal, result -> {
                requestInFlight = false;
                if (!result.ok) {
                    haltForReview("Automatic entry failed: " + result.report);
                    return;
                }
                store.autoStatus = result.report;
                store.autoConsecutiveFailures = 0;
                store.save();
            });
        });
    }

    public void emergencyClose(Callback callback) {
        if (!store.hasAutoPosition()) {
            callback.done("No tracked automatic Binance position is open. Check Binance Open Orders before taking action.");
            return;
        }
        store.autoRunning = false;
        store.autoLiveArmed = false;
        store.autoStatus = "Emergency close requested. Reconciling Binance now.";
        store.save();
        BinanceClient.emergencyCloseAutomaticPosition(store, result -> {
            if (!result.ok) {
                haltForReview("Emergency close needs manual Binance review: " + result.report);
                callback.done(result.report);
                return;
            }
            engine.running = false;
            store.autoStatus = result.report;
            store.save();
            store.triggerAlert("Nanu Automatic Emergency Close", result.report, true, "panic");
            callback.done(result.report);
        });
    }

    private void haltForReview(String reason) {
        requestInFlight = false;
        store.autoRunning = false;
        store.autoLiveArmed = false;
        store.autoConsecutiveFailures++;
        store.autoStatus = reason;
        engine.running = false;
        engine.addJournal("AUTO EXECUTOR HALT: " + reason);
        store.save();
        store.triggerAlert("Nanu Automatic Bot Halted", reason + " Check Binance Open Orders and order history before restarting.", true, "api");
        store.stopBackgroundEngine();
    }
}
