package com.nanu.aitradingbot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DexEngine {
    public static final class Position {
        public String chain, symbol, tokenAddress, exitReason = "";
        public double entryPrice, markPrice, quoteAmount, quantity, targetPrice, stopPrice, pnlUsd;
        public long openedAtMs;
    }

    private final DexAppStore store;
    private final List<DexCandidate> candidates = new CopyOnWriteArrayList<>();
    private final List<String> events = new CopyOnWriteArrayList<>();
    private volatile boolean scanInFlight;
    private volatile long lastScanMs;
    private volatile Position paperPosition;

    DexEngine(DexAppStore store) {
        this.store = store;
        event("DEX engine ready in paper mode. No mainnet swap is sent by the scanner.");
    }

    public List<DexCandidate> candidates() { return new ArrayList<>(candidates); }
    public List<String> events() { return new ArrayList<>(events); }
    public Position position() { return paperPosition; }
    public boolean hasPosition() { return paperPosition != null; }
    public boolean isScanning() { return scanInFlight; }

    public void start() {
        if (!store.hasWallet()) { store.lastStatus = "Create and back up the bot wallet before starting the scanner."; store.save(); return; }
        store.panic = false; store.scannerRunning = true;
        store.lastStatus = "DEX scanner started in paper mode. It will not send a mainnet swap.";
        store.save(); event("Scanner started for " + store.activeChainLabel() + "."); tick(true);
    }

    public void stop(String reason) {
        store.scannerRunning = false; store.liveDexArmed = false;
        store.lastStatus = reason == null ? "Scanner stopped by operator." : reason;
        store.save(); event(store.lastStatus);
    }

    public void panic() {
        store.scannerRunning = false; store.liveDexArmed = false; store.panic = true;
        store.lastStatus = "HALTED by operator. No new paper or live entries can start.";
        store.lastCritical = "Panic stop pressed. A DEX exit cannot be guaranteed while the device is offline.";
        store.save(); event(store.lastCritical);
    }

    public void clearPanic() {
        store.panic = false; store.lastCritical = "";
        store.lastStatus = "Panic cleared. Scanner remains stopped until you start it again.";
        store.save(); event("Panic state cleared.");
    }

    public void tick(boolean force) {
        store.lastHeartbeatMs = System.currentTimeMillis();
        if (!store.scannerRunning || store.panic || scanInFlight) { store.save(); return; }
        long interval = Math.max(30, store.scanSeconds) * 1000L;
        if (!force && System.currentTimeMillis() - lastScanMs < interval) { store.save(); return; }
        scanInFlight = true; lastScanMs = System.currentTimeMillis();
        DexMarketClient.discover(store, (items, status) -> {
            scanInFlight = false;
            if (items != null && !items.isEmpty()) {
                candidates.clear(); candidates.addAll(items);
                Collections.sort(candidates, new Comparator<DexCandidate>() {
                    @Override public int compare(DexCandidate l, DexCandidate r) { return r.riskScore - l.riskScore; }
                });
                updatePaperPosition();
            }
            store.lastStatus = status; store.save(); event(status);
        });
    }

    private void updatePaperPosition() {
        Position open = paperPosition;
        if (open != null) {
            DexCandidate live = find(open.tokenAddress);
            if (live == null || !(live.priceUsd > 0d)) return;
            open.markPrice = live.priceUsd;
            open.pnlUsd = (open.markPrice - open.entryPrice) * open.quantity;
            if (open.markPrice <= open.stopPrice) closePaper(open, live, "paper stop hit");
            else if (open.markPrice >= open.targetPrice) closePaper(open, live, "paper target hit");
            else if ("BLOCKED".equals(live.decision)) closePaper(open, live, "safety filter blocked: " + live.reason);
            return;
        }
        if (!store.paperAuto || !store.canPaperEnter()) return;
        for (DexCandidate c : candidates) {
            if (!DexSafetyPolicy.canOpenPaperPosition(c, store.minMomentumPercent)) continue;
            openPaper(c); return;
        }
    }

    private void openPaper(DexCandidate c) {
        Position p = new Position();
        p.chain = c.chain; p.symbol = c.symbol; p.tokenAddress = c.tokenAddress;
        p.entryPrice = c.priceUsd; p.markPrice = c.priceUsd; p.quoteAmount = store.maxTradeUsd;
        p.quantity = p.quoteAmount / Math.max(0.0000000001d, p.entryPrice);
        p.targetPrice = p.entryPrice * (1d + Math.max(0.5d, store.takeProfitPercent) / 100d);
        p.stopPrice = p.entryPrice * (1d - Math.max(0.5d, store.stopLossPercent) / 100d);
        p.openedAtMs = System.currentTimeMillis();
        paperPosition = p; store.tradesToday++;
        store.lastStatus = "Paper position opened: " + c.label() + ". No real transaction was sent.";
        store.save(); event(store.lastStatus);
    }

    public void closePaperNow() {
        if (paperPosition == null) { event("No paper position is open."); return; }
        closePaper(paperPosition, find(paperPosition.tokenAddress), "operator closed paper position");
    }

    private void closePaper(Position p, DexCandidate candidate, String reason) {
        p.exitReason = reason;
        TradeRecord rec = new TradeRecord();
        rec.symbol = p.symbol; rec.chain = p.chain;
        rec.entryPrice = p.entryPrice; rec.exitPrice = p.markPrice; rec.pnlUsd = p.pnlUsd;
        rec.pnlPct = p.entryPrice > 0 ? (p.markPrice - p.entryPrice) / p.entryPrice * 100.0 : 0;
        rec.win = p.pnlUsd > 0; rec.exitReason = reason;
        rec.openedAtMs = p.openedAtMs; rec.closedAtMs = System.currentTimeMillis();
        if (candidate != null) {
            rec.liquidityUsd = candidate.liquidityUsd; rec.volume24hUsd = candidate.volume24hUsd;
            rec.pairAgeMs = candidate.pairCreatedAtMs > 0 ? System.currentTimeMillis() - candidate.pairCreatedAtMs : 0;
            rec.change1h = candidate.change1h; rec.change24h = candidate.change24h; rec.riskScore = candidate.riskScore;
        }
        store.addTradeRecord(rec);
        paperPosition = null;
        store.lastStatus = "Paper " + (p.pnlUsd >= 0 ? "WIN" : "LOSS") + ": " + p.symbol + " " + money(p.pnlUsd) + " via " + reason;
        store.save(); event(store.lastStatus);
    }

    private DexCandidate find(String addr) {
        if (addr == null) return null;
        for (DexCandidate c : candidates) if (addr.equalsIgnoreCase(c.tokenAddress)) return c;
        return null;
    }

    public void event(String value) {
        if (value == null || value.trim().isEmpty()) return;
        events.add(0, time() + "  " + value.trim());
        while (events.size() > 40) events.remove(events.size() - 1);
    }

    private static String time() { return new java.text.SimpleDateFormat("HH:mm", Locale.US).format(new java.util.Date()); }
    private static String money(double v) { return String.format(Locale.US, "%+.2f USD", v); }
}
