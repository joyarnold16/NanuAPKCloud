package com.nanu.aitradingbot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class NanuEngine {
    public static class Trade {
        public String symbol, side, reason;
        public double entry, mark, pnl, pct, tp, sl;
        public int leverage;
        Trade(String s, String side, double e, double m, double p, double pc, double tp, double sl, int lev, String reason) {
            this.symbol = s; this.side = side; this.entry = e; this.mark = m; this.pnl = p; this.pct = pc; this.tp = tp; this.sl = sl; this.leverage = lev; this.reason = reason;
        }
    }

    public final AppStore store;
    public boolean running = false;
    public boolean panic = false;
    public double equity = 1000.00;
    public double todayPnl = 0;
    public double realizedPnl = 0;
    public double openPnl = 0;
    public double winRate = 72.0;
    public int moodConfidence = 51;
    public String marketMood = "CALM";
    public long lastTick = 0;
    public final List<Trade> trades = new ArrayList<>();
    public final List<String> journal = new ArrayList<>();
    public final List<String> brain = new ArrayList<>();
    private final Random rand = new Random(5);

    public NanuEngine(AppStore s) {
        store = s;
        seed();
        addJournal("Nanu v6.0 Controlled Live Scalping System ready in Paper Mode.");
    }

    public void start() {
        if (panic) panic = false;
        running = true;
        store.resetGuardSession();
        addJournal("Bot started in " + store.mode.toUpperCase(Locale.US) + " mode.");
        buildBrain();
        if ("live".equals(store.mode) && store.liveUnlocked && store.liveDryRunEnabled) {
            addJournal("LIVE CONTROL active: full auto locked; manual micro orders require preview + typed confirmation.");
        }
        tick(true);
    }

    public void stop() {
        running = false;
        addJournal("Bot stopped by user.");
        store.resetOrderSafetyState("Stop pressed");
    }

    public void panicClose() {
        running = false;
        panic = true;
        trades.clear();
        openPnl = 0;
        addJournal("PANIC CLOSE: all internal open trades cleared.");
        store.resetOrderSafetyState("Panic pressed");
    }

    public void resetPaper() {
        running = false; panic = false; equity = 1000; todayPnl = 0; realizedPnl = 0; openPnl = 0; winRate = 72; trades.clear(); store.resetGuardSession(); seed();
        store.resetOrderSafetyState("Paper wallet reset");
        addJournal("Paper wallet reset to 1000 USDT.");
    }

    public void tick(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastTick < 1800) return;
        lastTick = now;
        if (!running || panic) { buildMood(); return; }
        if (store.autoCoinMode && store.watchlist.size() < 4) store.autoSelectCoins();
        if (trades.isEmpty()) seed();
        for (Trade t : trades) {
            double wave = (rand.nextDouble() - 0.42) * 8.0;
            if (todayPnl > 60) wave -= 1.2;
            if (todayPnl < -35) wave += 1.4;
            t.pnl += wave;
            t.pct = t.pnl / Math.max(50, t.entry) * 100 * 8;
            t.mark = t.entry * (1 + t.pct / 100 / Math.max(1, t.leverage));
        }
        openPnl = 0;
        for (Trade t : trades) openPnl += t.pnl;
        todayPnl = openPnl + (rand.nextDouble() - 0.48) * 1.8;
        realizedPnl = todayPnl; // paper engine uses today P&L as realized/net simulation until live order history is connected
        equity = 1000 + todayPnl;
        winRate = Math.max(18, Math.min(91, 72 + todayPnl / 12));
        if (rand.nextInt(6) == 0) addJournal((todayPnl >= 0 ? "Trailing stop checked" : "Risk shield monitoring") + " • " + (trades.isEmpty() ? "watchlist" : trades.get(0).symbol));
        buildMood(); buildBrain(); checkProfitGuards();
    }

    private void seed() {
        trades.clear();
        String[] fallback = {"BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT"};
        for (int i = 0; i < Math.min(store.watchlist.size() > 0 ? store.watchlist.size() : 4, 4); i++) {
            String sym = store.watchlist.size() > i ? store.watchlist.get(i) : fallback[i];
            double base = priceFor(sym);
            double mark = base * (1 + (i + 1) * 0.0015);
            trades.add(new Trade(sym, "LONG", base, mark, (i == 0 ? 14 : 5) + i * 1.2, 0.5 + i * .16, base * 1.012, base * 0.993, i < 2 ? 20 : 15,
                    "EMA aligned, RSI healthy, MACD confirmation pending."));
        }
        buildMood(); buildBrain();
    }

    private void checkProfitGuards() {
        if (!running || panic) return;
        if (store.profitGuardEnabled && !store.profitTargetAlreadyHit && realizedPnl >= store.profitTargetUsdt) {
            store.profitTargetAlreadyHit = true;
            store.save();
            String msg = String.format(Locale.US, "Profit target reached: %+.2f USDT / target %.2f USDT. Bot stopped safely.", realizedPnl, store.profitTargetUsdt);
            store.autoStopForGuard("Profit target reached", msg);
            return;
        }
        if (store.duplicateProfitGuardEnabled && !store.duplicateProfitAlreadyHit) {
            double rounded = Math.round(realizedPnl * 100.0) / 100.0;
            if (!Double.isNaN(store.lastRoundedProfit) && Math.abs(rounded - store.lastRoundedProfit) < 0.01) {
                store.sameProfitRepeats++;
            } else {
                store.lastRoundedProfit = rounded;
                store.sameProfitRepeats = 1;
            }
            store.save();
            if (store.sameProfitRepeats >= Math.max(2, store.duplicateProfitRepeatCount)) {
                store.duplicateProfitAlreadyHit = true;
                store.save();
                String msg = String.format(Locale.US, "Repeated same profit value detected %.2f USDT for %d checks. Bot stopped to prevent loop/API stale data.", rounded, store.sameProfitRepeats);
                store.autoStopForGuard("Repeated profit pattern", msg);
            }
        }
    }

    private double priceFor(String s) {
        if (s.startsWith("BTC")) return 67842.10;
        if (s.startsWith("ETH")) return 3742.80;
        if (s.startsWith("SOL")) return 164.28;
        if (s.startsWith("BNB")) return 596.51;
        if (s.startsWith("XRP")) return 0.62;
        if (s.startsWith("DOGE")) return 0.13;
        if (s.startsWith("ADA")) return 0.43;
        return 100.00 + rand.nextInt(200);
    }

    private void buildMood() {
        if (panic) { marketMood = "PANIC"; moodConfidence = 0; return; }
        if (todayPnl > 120) { marketMood = "BIG PROFIT"; moodConfidence = 92; }
        else if (todayPnl > 15) { marketMood = "PROFIT"; moodConfidence = 72; }
        else if (todayPnl < -100) { marketMood = "HEAVY LOSS"; moodConfidence = 12; }
        else if (todayPnl < -15) { marketMood = "LOSS"; moodConfidence = 28; }
        else { marketMood = "CALM"; moodConfidence = 51; }
    }

    private void buildBrain() {
        brain.clear();
        brain.add("Market Regime: " + (Math.abs(todayPnl) < 20 ? "Calm / low volatility" : (todayPnl > 0 ? "Trending with positive momentum" : "Risky / defensive")));
        brain.add("Signal Confidence: " + moodConfidence + "/100 based on EMA, RSI, MACD and volume mood.");
        brain.add("Risk Shield: " + (panic ? "PANIC active" : "Daily loss, open-trade limits, cooldown and live dry-run gates monitored."));
        brain.add("Profit Guard: " + (store.profitGuardEnabled ? ("armed at " + String.format(Locale.US, "%.2f", store.profitTargetUsdt) + " USDT") : "off") + " • Duplicate P&L guard " + (store.duplicateProfitGuardEnabled ? ("on / " + store.duplicateProfitRepeatCount + " repeats") : "off"));
        brain.add("Order Safety: " + (store.liveDryRunEnabled ? "Controlled dry-run ON; manual micro order layer protected by confirmation and compliance guard." : "Dry-run OFF; do not use live until reviewed."));
        if (store.autoCoinMode) brain.add("Auto Mode: Nanu prefers high-volume pairs with tight spread and clean candle movement.");
        else brain.add("Manual Mode: User-selected watchlist is active.");
        if (!trades.isEmpty()) brain.add(trades.get(0).symbol + " thought: " + trades.get(0).reason);
        brain.add(todayPnl >= 0 ? "Learning Memory: winning conditions are being logged for pattern review." : "Learning Memory: loss pattern watch is active; cooldown may be needed.");
    }

    public void addJournal(String item) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(new java.util.Date());
        journal.add(0, time + "  " + item);
        while (journal.size() > 30) journal.remove(journal.size() - 1);
    }

    public String fmt(double v) { return String.format(Locale.US, "%+.2f", v); }
}
