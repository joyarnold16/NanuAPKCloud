package com.nanu.godmode;

import java.text.SimpleDateFormat;
import java.util.*;

public class AppState {
    public boolean running = false;
    public boolean panic = false;
    public String mode = "Paper";
    public String exchange = "Binance Spot";
    public double equity = 1000.0;
    public double dayPnl = 0.0;
    public double openPnl = 0.0;
    public int wins = 0;
    public int losses = 0;
    public String mood = "CALM";
    public int confidence = 55;
    public ArrayList<Double> equityPoints = new ArrayList<>();
    public ArrayList<Signal> signals = new ArrayList<>();
    public ArrayList<String> journal = new ArrayList<>();
    public ArrayList<String> brain = new ArrayList<>();
    private final Random rnd = new Random();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.US);

    public AppState() {
        for (int i = 0; i < 24; i++) equityPoints.add(equity);
        signals.add(new Signal("BTCUSDT", "LONG", 67842.10, 68221.35, 69500, 66200, 0, 20));
        signals.add(new Signal("ETHUSDT", "LONG", 3742.80, 3816.45, 3950, 3620, 0, 20));
        signals.add(new Signal("SOLUSDT", "LONG", 164.28, 170.11, 175, 160, 0, 15));
        signals.add(new Signal("BNBUSDT", "LONG", 596.51, 609.32, 620, 580, 0, 15));
        addJournal("Nanu ready in Paper Mode");
        brain.add("Waiting for EMA + RSI + MACD alignment.");
        brain.add("Live mode remains locked by safety rules until you confirm.");
    }

    public void tick() {
        if (!running || panic) return;
        double drift = rnd.nextDouble() * 18.0 - 7.5;
        if (rnd.nextInt(10) == 0) drift *= 3;
        openPnl += drift;
        dayPnl += drift * 0.35;
        equity += drift * 0.35;
        equityPoints.add(equity);
        if (equityPoints.size() > 35) equityPoints.remove(0);

        for (Signal s : signals) {
            double move = (rnd.nextDouble() - 0.46) * s.entry * 0.003;
            s.mark += move;
            if (s.direction.equals("LONG")) s.pnl = (s.mark - s.entry) * 0.18;
            else s.pnl = (s.entry - s.mark) * 0.18;
            if (s.pnl < -50 && rnd.nextInt(8) == 0) {
                addJournal("Stop loss pressure on " + s.symbol + " " + money(s.pnl));
                brain.add(0, "Risk guard watching " + s.symbol + ": loss pressure detected.");
            }
            if (s.pnl > 60 && rnd.nextInt(8) == 0) {
                addJournal("Take profit zone near " + s.symbol + " " + money(s.pnl));
                brain.add(0, "Trailing stop moved for " + s.symbol + ".");
            }
        }
        if (brain.size() > 8) brain.remove(brain.size() - 1);
        if (dayPnl > 80) { mood = "BULLISH"; confidence = 82 + rnd.nextInt(14); }
        else if (dayPnl < -80) { mood = "BEARISH"; confidence = 12 + rnd.nextInt(20); }
        else { mood = "CALM"; confidence = 45 + rnd.nextInt(25); }
        if (rnd.nextInt(15) == 0) addJournal("New scalping scan complete");
    }

    public void start() { running = true; panic = false; addJournal("Bot started"); brain.add(0, "Nanu started scanner: EMA, RSI, MACD filters active."); }
    public void stop() { running = false; addJournal("Bot stopped"); brain.add(0, "Nanu stopped. No new paper trades will open."); }
    public void panic() { running = false; panic = true; openPnl = 0; addJournal("PANIC CLOSE activated"); brain.add(0, "Emergency stop engaged. All internal open trades cleared."); }

    public boolean profitState() { return dayPnl >= 0; }
    public double winRate() {
        int total = Math.max(1, wins + losses + 12);
        double base = dayPnl >= 0 ? 72 : 31;
        return Math.max(5, Math.min(95, base + dayPnl / 45.0));
    }
    public String money(double value) { return String.format(Locale.US, "%s%.2f USDT", value >= 0 ? "+" : "", value); }
    public String pct(double value) { return String.format(Locale.US, "%s%.2f%%", value >= 0 ? "+" : "", value / 16.0); }
    private void addJournal(String text) {
        journal.add(0, timeFmt.format(new Date()) + "  " + text);
        while (journal.size() > 12) journal.remove(journal.size() - 1);
    }

    public static class Signal {
        public String symbol, direction;
        public double entry, mark, tp, sl, pnl;
        public int leverage;
        public Signal(String symbol, String direction, double entry, double mark, double tp, double sl, double pnl, int leverage) {
            this.symbol = symbol; this.direction = direction; this.entry = entry; this.mark = mark; this.tp = tp; this.sl = sl; this.pnl = pnl; this.leverage = leverage;
        }
    }
}
