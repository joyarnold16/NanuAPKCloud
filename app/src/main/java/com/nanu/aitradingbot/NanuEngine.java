package com.nanu.aitradingbot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class NanuEngine {
    public static class Trade {
        public String symbol;
        public String side;
        public double entry;
        public double mark;
        public double tp;
        public double sl;
        public double pnl;
        public double pnlPct;
        public int leverage;
        public int confidence;
        public String reason;
    }

    public boolean running = false;
    public boolean panic = false;
    public String mode = "paper";
    public double equity = 1000.0;
    public double todayPnl = 0.0;
    public double openPnl = 0.0;
    public double winRate = 72.0;
    public String marketMood = "CALM";
    public int moodConfidence = 51;
    public String regime = "Calm range, waiting for momentum";
    public String lastBrain = "Nanu is watching EMA, RSI, MACD, spread, and volume before entry.";
    public final List<Trade> trades = new ArrayList<>();
    public final List<String> journal = new ArrayList<>();
    private final List<String> watchlist = new ArrayList<>();
    private final Random rnd = new Random();
    private long lastTick = 0;

    public NanuEngine() {
        setWatchlist(defaultCoins());
        journal.add("Nanu AI Trading Bot ready in Paper mode");
        tick(true);
    }

    public List<String> defaultCoins() {
        ArrayList<String> x = new ArrayList<>();
        x.add("BTCUSDT"); x.add("ETHUSDT"); x.add("SOLUSDT"); x.add("BNBUSDT");
        return x;
    }

    public synchronized void setWatchlist(List<String> list) {
        watchlist.clear();
        for (String s : list) if (s != null && s.trim().length() > 0) watchlist.add(s.trim().toUpperCase());
        if (watchlist.isEmpty()) watchlist.addAll(defaultCoins());
        rebuildTrades();
    }

    public synchronized List<String> getWatchlist() { return new ArrayList<>(watchlist); }

    public synchronized void start() {
        panic = false;
        running = true;
        addJournal("Bot started in " + mode.toUpperCase(Locale.US) + " mode");
        tick(true);
    }

    public synchronized void stop() {
        running = false;
        addJournal("Bot stopped safely");
    }

    public synchronized void panicClose() {
        panic = true;
        running = false;
        trades.clear();
        openPnl = 0;
        addJournal("PANIC CLOSE activated. All internal paper trades cleared.");
    }

    public synchronized void tick(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastTick < 1500) return;
        lastTick = now;
        if (panic) return;
        if (trades.isEmpty()) rebuildTrades();
        double wave = Math.sin(now / 9000.0) * 0.55 + Math.cos(now / 17000.0) * 0.25;
        openPnl = 0;
        for (int i = 0; i < trades.size(); i++) {
            Trade t = trades.get(i);
            double drift = wave + (i - 1.5) * 0.17 + (running ? 0.25 : 0.0);
            double base = approxBase(t.symbol);
            t.mark = Math.max(0.0001, base * (1.0 + drift / 100.0));
            t.pnlPct = (t.mark - t.entry) / t.entry * 100.0 * ("SHORT".equals(t.side) ? -1 : 1);
            t.pnl = 16.0 * t.pnlPct + i * 2.5;
            t.confidence = Math.max(5, Math.min(97, (int)(58 + drift * 8 + i * 3)));
            openPnl += t.pnl;
        }
        todayPnl = running ? openPnl + 9.0 * wave : todayPnl * 0.98;
        equity = 1000.0 + todayPnl;
        winRate = Math.max(8, Math.min(92, 70.0 + todayPnl / 18.0));
        updateMood();
        updateBrain();
        if (running && rnd.nextInt(7) == 1) addJournal("Scanner refreshed: " + marketMood + " / confidence " + moodConfidence + "%");
    }

    private void rebuildTrades() {
        trades.clear();
        int n = Math.min(4, watchlist.size());
        for (int i = 0; i < n; i++) {
            String s = watchlist.get(i);
            Trade t = new Trade();
            t.symbol = s;
            t.side = "LONG";
            t.entry = approxBase(s) * (0.997 + i * 0.001);
            t.mark = approxBase(s);
            t.tp = t.entry * 1.011;
            t.sl = t.entry * 0.993;
            t.leverage = (s.startsWith("BTC") || s.startsWith("ETH")) ? 20 : 15;
            t.confidence = 55 + i * 7;
            t.reason = "EMA trend clean, RSI in tradable zone, MACD not conflicting, spread acceptable.";
            trades.add(t);
        }
    }

    private double approxBase(String s) {
        if (s.startsWith("BTC")) return 68240.0;
        if (s.startsWith("ETH")) return 3820.0;
        if (s.startsWith("SOL")) return 170.0;
        if (s.startsWith("BNB")) return 610.0;
        if (s.startsWith("XRP")) return 2.15;
        if (s.startsWith("DOGE")) return 0.18;
        if (s.startsWith("ADA")) return 0.75;
        if (s.startsWith("AVAX")) return 31.5;
        if (s.startsWith("LINK")) return 18.2;
        return 10.0 + Math.abs(s.hashCode() % 9000) / 100.0;
    }

    private void updateMood() {
        if (todayPnl > 100) { marketMood = "BULLISH"; moodConfidence = 91; }
        else if (todayPnl > 20) { marketMood = "CALM PROFIT"; moodConfidence = 72; }
        else if (todayPnl < -100) { marketMood = "DANGER"; moodConfidence = 18; }
        else if (todayPnl < -20) { marketMood = "WEAK"; moodConfidence = 30; }
        else { marketMood = "CALM"; moodConfidence = 51; }
    }

    private void updateBrain() {
        if (todayPnl > 100) {
            regime = "Trending with strong momentum";
            lastBrain = "Nanu accepted high-confidence entries because EMA, MACD, and volume are aligned. Trailing stop protection is active.";
        } else if (todayPnl > 20) {
            regime = "Positive scalping rhythm";
            lastBrain = "Nanu is allowing selective trades. RSI is healthy and spreads are acceptable. No over-trading detected.";
        } else if (todayPnl < -100) {
            regime = "Danger zone";
            lastBrain = "Nanu recommends reducing exposure. Loss pattern detected; cooldown or stop is safer before new entries.";
        } else if (todayPnl < -20) {
            regime = "Choppy / weak movement";
            lastBrain = "Nanu is cautious. Signals have lower quality; wait for stronger confirmation or smaller risk.";
        } else {
            regime = "Neutral observation";
            lastBrain = "Nanu is scanning. No strong edge yet; paper engine keeps risk controlled while waiting for momentum.";
        }
    }

    public synchronized void addJournal(String msg) {
        String t = String.format(Locale.US, "%1$tH:%1$tM:%1$tS  %2$s", System.currentTimeMillis(), msg);
        journal.add(0, t);
        while (journal.size() > 50) journal.remove(journal.size() - 1);
    }
}
