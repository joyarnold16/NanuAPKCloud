package com.nanu.aitradingbot;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/** Android coordinator; portable PaperLedger owns financial state. */
public final class DexEngine {
    public static final class Position {
        public String chain, symbol, tokenAddress, pairAddress, exitReason = "";
        public double entryPrice, markPrice, quoteAmount, quantity, targetPrice, stopPrice, pnlUsd;
        public long openedAtMs, markAtMs;
    }
    private final DexAppStore store;
    private final PaperLedger ledger;
    private final DexDataClient data = new DexDataClient();
    private final List<DexCandidate> candidates = new CopyOnWriteArrayList<>();
    private final List<String> events = new CopyOnWriteArrayList<>();
    private boolean scanInFlight, priceInFlight;
    private long lastScanMs, lastPriceMs, generation;
    DexEngine(DexAppStore store) {
        this.store = store;
        ledger = new PaperLedger(store.paperStorage());
        try {
            long now = System.currentTimeMillis(); double realized = 0; JSONArray history = new JSONArray();
            for (TradeRecord r : store.tradeHistory) {
                history.put(r.toJson());
                if (r.closedAtMs / 86_400_000L == now / 86_400_000L && Ohlcv.finite(r.pnlUsd)) realized += r.pnlUsd;
            }
            ledger.migrate(store.tradesToday, realized, store.panic, history, now);
            store.scannerRunning = false;
            sync(); event("Paper ledger restored. Mainnet DEX execution is blocked.");
        } catch (Exception e) { fault(e); }
    }
    public List<DexCandidate> candidates() { return new ArrayList<>(candidates); }
    public List<String> events() { return new ArrayList<>(events); }
    public synchronized Position position() {
        JSONObject o = ledger.position(); if (o == null) return null;
        Position p = new Position(); p.chain = o.optString("chain"); p.symbol = o.optString("symbol");
        p.tokenAddress = o.optString("tokenAddress"); p.pairAddress = o.optString("pairAddress");
        p.entryPrice = o.optDouble("entryPrice"); p.markPrice = o.optDouble("markPrice");
        p.quoteAmount = o.optDouble("quoteAmount"); p.quantity = o.optDouble("quantity");
        p.targetPrice = o.optDouble("targetPrice"); p.stopPrice = o.optDouble("stopPrice"); p.pnlUsd = o.optDouble("pnlUsd");
        p.openedAtMs = o.optLong("openedAtMs"); p.markAtMs = o.optLong("markAtMs"); p.exitReason = o.optString("pendingExit");
        return p;
    }
    public synchronized boolean hasPosition() { return ledger.position() != null; }
    public synchronized boolean isScanning() { return scanInFlight; }
    private PaperLedger.Limits limits() {
        PaperLedger.Limits l = new PaperLedger.Limits();
        l.tradeUsd = store.maxTradeUsd; l.dailyLossUsd = store.maxDailyLossUsd;
        l.maxTrades = store.maxTradesPerDay; l.stopPct = store.stopLossPercent;
        l.targetPct = store.takeProfitPercent; l.slippagePct = store.maxSlippagePercent;
        return l;
    }
    public synchronized void start() {
        if (store.panic || !ledger.healthy()) { event("Clear Panic or recover paper storage before starting."); return; }
        try { limits().validate(); } catch (Exception e) { fault(e); return; }
        generation++; store.scannerRunning = true; store.liveDexArmed = false;
        store.lastStatus = "Paper scanner started. No mainnet swaps."; store.save(); tick(true);
    }
    public synchronized void stop(String reason) {
        generation++; store.scannerRunning = false; store.liveDexArmed = false;
        store.lastStatus = reason == null ? "Entries paused; positions remain monitored." : reason;
        store.save(); event(store.lastStatus);
    }
    public synchronized void panic() {
        stop("Panic: entries halted; fresh-price paper exit pending.");
        try { ledger.panic(); sync(); tick(true); } catch (Exception e) { fault(e); }
    }
    public synchronized void clearPanic() {
        try {
            ledger.clearPanic(); store.scannerRunning = false; store.lastCritical = "";
            sync(); event("Panic cleared. Start explicitly to allow entries.");
        } catch (Exception e) { event(e.getMessage()); }
    }
    public synchronized void tick(boolean force) {
        long now = System.currentTimeMillis(); store.lastHeartbeatMs = now;
        if (!ledger.healthy()) return;
        try { if (ledger.advanceClock(now)) sync(); } catch (Exception e) { fault(e); return; }
        // Price monitoring does not depend on discovery, selected chain, entry state or panic.
        if (hasPosition() && !priceInFlight && (force || now - lastPriceMs >= 15_000)) {
            Position p = position(); priceInFlight = true; lastPriceMs = now;
            new Thread(() -> {
                try {
                    DexCandidate quote = data.pair(p.chain, p.pairAddress, p.tokenAddress);
                    data.security(quote);
                    synchronized (DexEngine.this) {
                        String result = ledger.mark(quote, limits(), System.currentTimeMillis());
                        store.lastStatus = result; sync();
                        if (result.contains("pending") || result.contains("closed")) event(result);
                    }
                } catch (Exception e) {
                    synchronized (DexEngine.this) {
                        store.lastCritical = "Position needs attention: " + e.getMessage(); store.save(); event(store.lastCritical);
                        if (!ledger.healthy()) fault(e);
                    }
                } finally { synchronized (DexEngine.this) { priceInFlight = false; } }
            }, "nanu-pair-monitor").start();
        }
        if (!store.scannerRunning || store.panic || scanInFlight || hasPosition()) return;
        if (!force && now - lastScanMs < Math.max(60, store.scanSeconds) * 1000L) return;
        scanInFlight = true; lastScanMs = now; final long requestGeneration = generation;
        DexMarketClient.discover(store, (items, status) -> {
            synchronized (DexEngine.this) {
                try {
                    candidates.clear(); candidates.addAll(items);
                    if (requestGeneration != generation || !store.scannerRunning || store.panic) return;
                    store.lastStatus = status;
                    if (store.paperAuto) for (DexCandidate c : items) {
                        DexDataClient.evaluate(c, store.minLiquidityUsd, store.minVolumeUsd, store.minPairAgeHours);
                        if (!DexSafetyPolicy.canOpenPaperPosition(c, store.minMomentumPercent)) continue;
                        store.lastStatus = ledger.open(c, limits(), System.currentTimeMillis(), "paper");
                        event(store.lastStatus); if (hasPosition()) break;
                    }
                    sync();
                } catch (Exception e) { fault(e); }
                finally { scanInFlight = false; }
            }
        });
    }
    public synchronized void closePaperNow() {
        try { ledger.requestExit("operator exit"); event("Exit requested; waiting for a fresh pair quote."); tick(true); }
        catch (Exception e) { fault(e); }
    }
    private void sync() throws Exception {
        JSONObject s = ledger.snapshot();
        store.panic = s.getBoolean("panic"); store.tradesToday = s.getInt("entries");
        store.tradeHistory.clear(); JSONArray history = s.getJSONArray("history");
        for (int i = 0; i < history.length(); i++) store.tradeHistory.add(TradeRecord.fromJson(history.getString(i)));
        if (ledger.halted()) store.scannerRunning = false;
        store.liveDexArmed = false; store.save();
    }
    public synchronized String riskSummary() {
        JSONObject s = ledger.snapshot();
        if (!ledger.healthy()) return "Paper storage unavailable. Recovery required.";
        return String.format(Locale.US, "UTC daily P/L: %+.2f USD | Paper cash: %.2f USD | Loss streak: %d%s",
            s.optDouble("realized"), s.optDouble("cash"), s.optInt("losses"), s.optBoolean("dailyHalt") ? " | DAILY HALT" : "");
    }
    private void fault(Exception e) {
        generation++; store.scannerRunning = false; store.panic = true; store.liveDexArmed = false;
        store.lastCritical = "Paper engine halted: " + e.getMessage(); store.lastStatus = store.lastCritical;
        store.save(); event(store.lastCritical);
    }
    public void event(String value) {
        if (value == null || value.trim().isEmpty()) return;
        events.add(0, new java.text.SimpleDateFormat("HH:mm", Locale.US).format(new java.util.Date()) + "  " + value);
        while (events.size() > 40) events.remove(events.size() - 1);
    }
}
