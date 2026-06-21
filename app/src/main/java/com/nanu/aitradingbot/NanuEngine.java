package com.nanu.aitradingbot;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public class NanuEngine {
    public static class Trade {
        public String symbol, side, reason;
        public double entry, mark, pnl, pct, tp, sl, quantity;
        // Retained for UI compatibility. Spot positions always use 1x.
        public int leverage;

        Trade(String symbol, double entry, double quantity, double tp, double sl, String reason) {
            this.symbol = symbol;
            this.side = "SPOT PAPER BUY";
            this.entry = entry;
            this.mark = entry;
            this.quantity = quantity;
            this.tp = tp;
            this.sl = sl;
            this.reason = reason;
            this.leverage = 1;
        }
    }

    public final AppStore store;
    public boolean running = false;
    public boolean panic = false;
    public double equity = 1000.00;
    public double todayPnl = 0;
    public double realizedPnl = 0;
    public double openPnl = 0;
    public double winRate = 0;
    public int moodConfidence = 0;
    public String marketMood = "WAITING";
    public long lastTick = 0;
    public final List<Trade> trades = new CopyOnWriteArrayList<>();
    public final List<String> journal = new CopyOnWriteArrayList<>();
    public final List<String> brain = new CopyOnWriteArrayList<>();
    public final AutoExecutionEngine autoExecution;

    private long lastScalperDispatchMs = 0L;
    private String lastHandledAction = "";
    private int paperWins = 0;
    private int paperLosses = 0;

    public NanuEngine(AppStore store) {
        this.store = store;
        this.autoExecution = new AutoExecutionEngine(store, this);
        buildMood();
        buildBrain();
        addJournal("Nanu v6.2 ready. Live candle scanner is OFF until Start is pressed.");
    }

    public void start() {
        panic = false;
        running = true;
        store.resetGuardSession();
        addJournal("Scalper started in " + store.mode.toUpperCase(Locale.US) + " mode. Automatic live orders require the separate armed automatic session.");
        tick(true);
    }

    public void stop() {
        running = false;
        autoExecution.stop("Automatic executor stopped by operator.");
        addJournal("Scalper stopped. No new scans or paper entries will be created.");
        store.resetOrderSafetyState("Stop button");
        buildMood();
        buildBrain();
    }

    public void panicClose() {
        running = false;
        panic = true;
        autoExecution.panic("Panic stop active. Existing Binance OCO protection remains in place.");
        trades.clear();
        openPnl = 0;
        addJournal("PANIC: scanner stopped and paper positions cleared. Check Binance manually; this cannot cancel exchange orders.");
        store.resetOrderSafetyState("Panic pressed");
        buildMood();
        buildBrain();
    }

    public void resetPaper() {
        running = false;
        panic = false;
        equity = 1000;
        todayPnl = 0;
        realizedPnl = 0;
        openPnl = 0;
        winRate = 0;
        paperWins = 0;
        paperLosses = 0;
        trades.clear();
        store.resetGuardSession();
        store.resetOrderSafetyState("Paper wallet reset");
        addJournal("Paper wallet reset to 1000 USDT. No simulated trades are preloaded.");
        buildMood();
        buildBrain();
    }

    public void tick(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastTick < 1800L) return;
        lastTick = now;
        if (!running || panic) {
            buildMood();
            buildBrain();
            return;
        }

        if ("live".equals(store.mode) && store.autoRunning) {
            autoExecution.tick(force);
            refreshMetrics();
            buildMood();
            buildBrain();
            checkGuards();
            return;
        }

        long scanEveryMs = Math.max(30, store.scalperScanSeconds) * 1000L;
        if (store.scalperEnabled && (force || now - lastScalperDispatchMs >= scanEveryMs)) {
            lastScalperDispatchMs = now;
            BinanceClient.scanScalper(store, (signal, report) -> {
                if (signal != null) onScalperSignal(signal);
                else addJournal("Market scan failed: " + report);
            });
        }

        refreshMetrics();
        buildMood();
        buildBrain();
        checkGuards();
    }

    private synchronized void onScalperSignal(ScalpingStrategy.Signal signal) {
        if (!running || panic) return;
        boolean changed = !signal.action.name().equals(lastHandledAction);
        lastHandledAction = signal.action.name();

        if ("paper".equals(store.mode) && store.scalperPaperAutoTrade) {
            updatePaperPosition(signal);
        } else if (changed && (signal.action == ScalpingStrategy.Action.BUY || signal.action == ScalpingStrategy.Action.EXIT)) {
            String message = signal.action + " signal for " + store.scalperSymbol + " at " + String.format(Locale.US, "%.8f", signal.price)
                    + ". No automatic Binance order was sent.";
            addJournal(message);
            store.triggerAlert("Nanu AI Trading Bot Signal", message, false, "dryrun");
        }

        if (changed) addJournal("Live candle signal: " + signal.action + " " + store.scalperSymbol + " (" + signal.confidence + "/100).");
        refreshMetrics();
        buildMood();
        buildBrain();
        checkGuards();
    }

    private void updatePaperPosition(ScalpingStrategy.Signal signal) {
        if (signal.price <= 0 || Double.isNaN(signal.price)) return;
        Trade open = trades.isEmpty() ? null : trades.get(0);
        if (open != null) {
            mark(open, signal.price);
            boolean hitStop = open.mark <= open.sl;
            boolean hitTarget = open.mark >= open.tp;
            boolean exitSignal = signal.action == ScalpingStrategy.Action.EXIT;
            if (hitStop || hitTarget || exitSignal) {
                realizedPnl += open.pnl;
                if (open.pnl >= 0) paperWins++; else paperLosses++;
                String reason = hitStop ? "paper stop reference" : (hitTarget ? "paper target reference" : "strategy exit signal");
                addJournal("Paper position closed: " + open.symbol + " " + fmt(open.pnl) + " USDT via " + reason + ".");
                trades.clear();
            }
            return;
        }

        if (signal.action != ScalpingStrategy.Action.BUY) return;
        double amount = Math.max(5.0, store.scalperTradeAmountUsdt);
        double quantity = amount / signal.price;
        Trade created = new Trade(
                store.scalperSymbol,
                signal.price,
                quantity,
                signal.price * (1.0 + Math.max(0.1, store.takeProfit) / 100.0),
                signal.price * (1.0 - Math.max(0.1, store.stopLoss) / 100.0),
                signal.reason
        );
        trades.add(created);
        addJournal("Paper position opened: " + created.symbol + " using " + String.format(Locale.US, "%.2f", amount) + " USDT from a live candle signal.");
    }

    private void refreshMetrics() {
        openPnl = 0;
        if (!trades.isEmpty() && !Double.isNaN(store.lastScalperPrice) && store.lastScalperPrice > 0) mark(trades.get(0), store.lastScalperPrice);
        for (Trade trade : trades) openPnl += trade.pnl;

        if ("live".equals(store.mode) && store.portfolioSyncOk && !Double.isNaN(store.spotEquityUsdt)) {
            if (Double.isNaN(store.liveEquityBaselineUsdt)) {
                store.liveEquityBaselineUsdt = store.spotEquityUsdt;
                store.save();
            }
            equity = store.spotEquityUsdt;
            todayPnl = equity - store.liveEquityBaselineUsdt;
            realizedPnl = todayPnl;
            openPnl = todayPnl;
        } else {
            todayPnl = realizedPnl + openPnl;
            equity = 1000.0 + todayPnl;
        }
        int completed = paperWins + paperLosses;
        winRate = completed == 0 ? 0 : paperWins * 100.0 / completed;
    }

    private void mark(Trade trade, double price) {
        trade.mark = price;
        trade.pnl = (trade.mark - trade.entry) * trade.quantity;
        trade.pct = (trade.mark - trade.entry) / Math.max(0.00000001, trade.entry) * 100.0;
    }

    private void checkGuards() {
        if (!running || panic) return;
        double guardedPnl = store.autoRunning ? store.autoRealizedPnlUsdt : todayPnl;
        double guardBase = "live".equals(store.mode) && !Double.isNaN(store.liveEquityBaselineUsdt)
                ? store.liveEquityBaselineUsdt : 1000.0;
        double maxLossUsdt = Math.max(0.0, guardBase * Math.max(0.0, store.dailyLossLimit) / 100.0);
        if (maxLossUsdt > 0 && guardedPnl <= -maxLossUsdt) {
            store.autoStopForGuard("Daily loss limit reached", String.format(Locale.US, "Daily loss limit reached: %+.2f USDT (%.2f%% limit). Scanner stopped; inspect Binance before any new order.", guardedPnl, store.dailyLossLimit));
            return;
        }
        if (store.profitGuardEnabled && guardedPnl >= store.profitTargetUsdt) {
            store.autoStopForGuard("Profit target reached", String.format(Locale.US, "Profit target reached: %+.2f USDT. Scanner stopped safely.", guardedPnl));
        }
    }

    private void buildMood() {
        if (panic) {
            marketMood = "PANIC";
            moodConfidence = 0;
        } else if (!running) {
            marketMood = "IDLE";
            moodConfidence = 0;
        } else if ("BUY".equals(store.lastScalperSignal)) {
            marketMood = "BULLISH SETUP";
            moodConfidence = store.lastScalperConfidence;
        } else if ("EXIT".equals(store.lastScalperSignal)) {
            marketMood = "EXIT / DEFENSIVE";
            moodConfidence = store.lastScalperConfidence;
        } else {
            marketMood = "WAITING";
            moodConfidence = store.lastScalperConfidence;
        }
    }

    private void buildBrain() {
        brain.clear();
        brain.add("Live-data scanner: " + (store.scalperEnabled ? "ON" : "OFF") + " • " + store.scalperSymbol + " • 1m closed candles • every " + Math.max(30, store.scalperScanSeconds) + "s.");
        brain.add("Latest signal: " + store.lastScalperSignal + " • confidence " + store.lastScalperConfidence + "/100 • checked " + store.scalperAgeLabel() + ".");
        brain.add("Strategy: EMA 9/21 trend filter plus RSI 14 momentum band. It is deterministic, not predictive machine learning.");
        brain.add("Execution: " + (store.autoRunning ? "automatic protected Binance Spot execution" : ("paper".equals(store.mode) && store.scalperPaperAutoTrade ? "automatic PAPER positions only" : "signals only")) + ".");
        brain.add("Risk: per-paper-trade " + String.format(Locale.US, "%.2f", store.scalperTradeAmountUsdt) + " USDT • stop reference " + String.format(Locale.US, "%.2f", store.stopLoss) + "% • target reference " + String.format(Locale.US, "%.2f", store.takeProfit) + "%.");
        if ("live".equals(store.mode)) brain.add("Live equity: " + (store.portfolioSyncOk ? store.portfolioEquityLabel() : "not synced; do not place a real order"));
        brain.add("Automatic LIVE execution requires a foreground service, fresh API and Telegram Doctors, static-IP verification, a one-time arm, and Binance OCO protection.");
        if (store.lastScalperError != null && !store.lastScalperError.isEmpty()) brain.add(store.lastScalperError);
    }

    public synchronized void addJournal(String item) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(new java.util.Date());
        journal.add(0, time + "  " + item);
        while (journal.size() > 40) journal.remove(journal.size() - 1);
    }

    public String fmt(double value) {
        return String.format(Locale.US, "%+.2f", value);
    }

    public int paperCompletedTrades() {
        return paperWins + paperLosses;
    }

    public int paperWins() {
        return paperWins;
    }
}
