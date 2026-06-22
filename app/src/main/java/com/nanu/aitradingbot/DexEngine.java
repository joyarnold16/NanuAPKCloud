package com.nanu.aitradingbot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/** Scanner and paper execution engine. Mainnet signing is deliberately not invoked from this class. */
public final class DexEngine {
    public static final class Position {
        public String chain;
        public String symbol;
        public String tokenAddress;
        public double entryPrice;
        public double markPrice;
        public double quoteAmount;
        public double quantity;
        public double targetPrice;
        public double stopPrice;
        public double pnlUsd;
        public long openedAtMs;
        public String exitReason = "";
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
        store.panic = false;
        store.scannerRunning = true;
        store.lastStatus = "DEX scanner started in paper mode. It will not send a mainnet swap.";
        store.save();
        event("Scanner started for " + store.activeChainLabel() + ".");
        tick(true);
    }

    public void stop(String reason) {
        store.scannerRunning = false;
        store.liveDexArmed = false;
        store.lastStatus = reason == null ? "Scanner stopped by operator." : reason;
        store.save();
        event(store.lastStatus);
    }

    public void panic() {
        store.scannerRunning = false;
        store.liveDexArmed = false;
        store.panic = true;
        store.lastStatus = "HALTED by operator. No new paper or live entries can start.";
        store.lastCritical = "Panic stop pressed. A DEX exit cannot be guaranteed while the device is offline.";
        store.save();
        event(store.lastCritical);
    }

    public void clearPanic() {
        store.panic = false;
        store.lastCritical = "";
        store.lastStatus = "Panic cleared. Scanner remains stopped until you start it again.";
        store.save();
        event("Panic state cleared.");
    }

    public void tick(boolean force) {
        store.lastHeartbeatMs = System.currentTimeMillis();
        if (!store.scannerRunning || store.panic || scanInFlight) { store.save(); return; }
        long interval = Math.max(30, store.scanSeconds) * 1000L;
        if (!force && System.currentTimeMillis() - lastScanMs < interval) { store.save(); return; }
        scanInFlight = true;
        lastScanMs = System.currentTimeMillis();
        DexMarketClient.discover(store, (items, status) -> {
            scanInFlight = false;
            if (items != null && !items.isEmpty()) {
                candidates.clear();
                candidates.addAll(items);
                Collections.sort(candidates, new Comparator<DexCandidate>() {
                    @Override public int compare(DexCandidate left, DexCandidate right) {
                        return right.riskScore - left.riskScore;
                    }
                });
                updatePaperPosition();
            }
            store.lastStatus = status;
            store.save();
            event(status);
        });
    }

    private void updatePaperPosition() {
        Position open = paperPosition;
        if (open != null) {
            DexCandidate live = find(open.tokenAddress);
            if (live == null || !(live.priceUsd > 0d)) return;
            open.markPrice = live.priceUsd;
            open.pnlUsd = (open.markPrice - open.entryPrice) * open.quantity;
            if (open.markPrice <= open.stopPrice) closePaper(open, "paper stop reference");
            else if (open.markPrice >= open.targetPrice) closePaper(open, "paper target reference");
            else if ("BLOCKED".equals(live.decision)) closePaper(open, "paper risk filter changed: " + live.reason);
            return;
        }
        if (!store.paperAuto || !store.canPaperEnter()) return;
        for (DexCandidate candidate : candidates) {
            if (!DexSafetyPolicy.canOpenPaperPosition(candidate, 1.0d)) continue;
            openPaper(candidate);
            return;
        }
    }

    private void openPaper(DexCandidate candidate) {
        Position p = new Position();
        p.chain = candidate.chain;
        p.symbol = candidate.symbol;
        p.tokenAddress = candidate.tokenAddress;
        p.entryPrice = candidate.priceUsd;
        p.markPrice = candidate.priceUsd;
        p.quoteAmount = store.maxTradeUsd;
        p.quantity = p.quoteAmount / Math.max(0.0000000001d, p.entryPrice);
        p.targetPrice = p.entryPrice * (1d + Math.max(0.5d, store.takeProfitPercent) / 100d);
        p.stopPrice = p.entryPrice * (1d - Math.max(0.5d, store.stopLossPercent) / 100d);
        p.openedAtMs = System.currentTimeMillis();
        paperPosition = p;
        store.tradesToday++;
        store.lastStatus = "Paper position opened: " + candidate.label() + ". No real transaction was sent.";
        store.save();
        event(store.lastStatus);
    }

    public void closePaperNow() {
        if (paperPosition == null) { event("No paper position is open."); return; }
        closePaper(paperPosition, "operator closed paper position");
    }

    private void closePaper(Position p, String reason) {
        p.exitReason = reason;
        paperPosition = null;
        store.lastStatus = "Paper position closed: " + p.symbol + " " + money(p.pnlUsd) + " via " + reason + ". No real transaction was sent.";
        store.save();
        event(store.lastStatus);
    }

    private DexCandidate find(String tokenAddress) {
        for (DexCandidate candidate : candidates) if (candidate.tokenAddress.equalsIgnoreCase(tokenAddress)) return candidate;
        return null;
    }

    private void event(String value) {
        if (value == null || value.trim().isEmpty()) return;
        events.add(0, time() + "  " + value.trim());
        while (events.size() > 40) events.remove(events.size() - 1);
    }

    private static String time() { return new java.text.SimpleDateFormat("HH:mm", Locale.US).format(new java.util.Date()); }
    private static String money(double value) { return String.format(Locale.US, "%+.2f USD", value); }
}
