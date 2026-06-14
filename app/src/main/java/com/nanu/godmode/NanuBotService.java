package com.nanu.godmode;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NanuBotService extends Service {
    public static final String ACTION_START = "com.nanu.godmode.START";
    public static final String ACTION_STOP = "com.nanu.godmode.STOP";
    public static final String ACTION_PANIC = "com.nanu.godmode.PANIC";
    public static final String CHANNEL_ID = "nanu_bot_channel";

    private AppConfig cfg;
    private JournalDb db;
    private BinanceClient binance;
    private TelegramClient telegram;
    private Strategy strategy;
    private ScheduledExecutorService exec;
    private final Random random = new Random();
    private volatile boolean running = false;

    @Override public void onCreate() {
        super.onCreate();
        cfg = new AppConfig(this);
        db = new JournalDb(this);
        binance = new BinanceClient(cfg);
        telegram = new TelegramClient(cfg);
        strategy = new Strategy();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopBot("Stopped by user.");
            return START_NOT_STICKY;
        }
        if (ACTION_PANIC.equals(action)) {
            panicStop();
            return START_NOT_STICKY;
        }
        startBot();
        return START_STICKY;
    }

    private void startBot() {
        if (running) return;
        cfg.setPanic(false);
        cfg.setStatus("RUNNING");
        db.event("SYSTEM", "Nanu all-in-one APK engine started. Mode=" + cfg.mode());
        telegram.send("Engine started in " + cfg.mode() + " mode.");
        startForeground(7, notification("Nanu running", "Paper first. Mode: " + cfg.mode()));
        running = true;
        exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleWithFixedDelay(this::safeTick, 1, cfg.intervalSeconds(), TimeUnit.SECONDS);
    }

    private void stopBot(String reason) {
        running = false;
        cfg.setStatus("STOPPED");
        if (exec != null) exec.shutdownNow();
        db.event("SYSTEM", reason);
        telegram.send(reason);
        stopForeground(true);
        stopSelf();
    }

    private void panicStop() {
        cfg.setPanic(true);
        cfg.setStatus("PANIC");
        List<TradeRow> open = db.openTrades();
        for (TradeRow t : open) {
            double price = cfg.getLastPrice(t.symbol);
            if (price <= 0) price = t.entry;
            db.closeTrade(t.id, price, "PANIC CLOSE from app.");
        }
        stopBot("PANIC activated. All paper/open internal trades closed.");
    }

    private void safeTick() {
        try { tick(); } catch (Exception e) {
            cfg.setLastSignal("Engine warning: " + e.getMessage());
            db.event("ERROR", e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private void tick() throws Exception {
        if (!running || cfg.panic()) return;
        double daily = db.dailyPnl();
        if (daily <= -Math.abs(cfg.dailyLossLimit())) {
            cfg.setLastSignal("Daily loss guard hit. Nanu stopped.");
            stopBot("Daily loss guard hit. PnL=" + fmt(daily));
            return;
        }

        manageOpenTrades();
        if (db.openCount() >= cfg.maxOpenTrades()) return;

        for (String symbol : cfg.symbols()) {
            if (symbol.length() < 5) continue;
            if (db.openCount() >= cfg.maxOpenTrades()) break;
            List<Candle> candles;
            try {
                candles = binance.getKlines(symbol, "1m", 80);
            } catch (Exception networkIssue) {
                candles = syntheticCandles(symbol);
                db.event("NETWORK", "Using offline synthetic candles for " + symbol + ": " + networkIssue.getMessage());
            }
            Strategy.Decision d = strategy.decide(symbol, candles);
            cfg.setLastPrice(symbol, d.lastPrice);
            cfg.setLastSignal(d.reason + " confidence=" + fmt(d.confidence));
            db.event("SIGNAL", d.reason + " conf=" + fmt(d.confidence));

            if ("BUY".equals(d.action) && d.confidence >= 60) {
                double qty = cfg.tradeUsdt() / Math.max(0.0000001, d.lastPrice);
                String mode = cfg.mode();
                if ("paper".equals(mode) || !cfg.realOrdersEnabled()) {
                    db.openTrade(symbol, "BUY", d.lastPrice, qty, d.reason + " | paper/internal trade.");
                    telegram.send("Paper BUY " + symbol + " @ " + fmt(d.lastPrice));
                } else {
                    try {
                        String result = binance.placeMarketOrder(symbol, "BUY", qty);
                        db.openTrade(symbol, "BUY", d.lastPrice, qty, d.reason + " | Binance order sent. " + shortText(result));
                        telegram.send("Binance BUY sent " + symbol + " @ approx " + fmt(d.lastPrice));
                    } catch (Exception ex) {
                        db.event("ORDER_ERROR", ex.getMessage());
                        cfg.setLastSignal("Order blocked/error: " + ex.getMessage());
                    }
                }
            }
        }
    }

    private void manageOpenTrades() {
        List<TradeRow> open = db.openTrades();
        long now = System.currentTimeMillis();
        for (TradeRow t : open) {
            double price;
            try { price = binance.getPrice(t.symbol); } catch (Exception e) { price = syntheticPrice(t.symbol, t.entry); }
            cfg.setLastPrice(t.symbol, price);
            double peak = Math.max(t.peak, price);
            if (peak > t.peak) db.updatePeak(t.id, peak);
            double change = IndicatorUtils.pct(t.entry, price);
            double trailDrop = IndicatorUtils.pct(peak, price);
            long ageMin = (now - t.openedAt) / 60000L;

            if (change <= -Math.abs(cfg.stopLossPct())) db.closeTrade(t.id, price, "Stop loss " + fmt(change) + "%");
            else if (change >= Math.abs(cfg.takeProfitPct())) db.closeTrade(t.id, price, "Take profit " + fmt(change) + "%");
            else if (trailDrop <= -Math.abs(cfg.trailingPct()) && peak > t.entry) db.closeTrade(t.id, price, "Trailing stop from peak " + fmt(trailDrop) + "%");
            else if (ageMin >= cfg.maxHoldMinutes()) db.closeTrade(t.id, price, "Max hold time reached: " + ageMin + "m");
        }
    }

    private List<Candle> syntheticCandles(String symbol) {
        java.util.ArrayList<Candle> list = new java.util.ArrayList<>();
        double base = cfg.getLastPrice(symbol);
        if (base <= 0) base = symbol.startsWith("BTC") ? 65000 : symbol.startsWith("ETH") ? 3200 : symbol.startsWith("SOL") ? 150 : 600;
        long now = System.currentTimeMillis() - 80L*60000L;
        for (int i=0;i<80;i++) {
            double wave = Math.sin(i/5.0) * base * 0.001 + (random.nextDouble()-0.48) * base * 0.0018;
            double close = Math.max(0.0001, base + wave + i * base * 0.00003);
            list.add(new Candle(now + i*60000L, base, Math.max(base, close), Math.min(base, close), close, 1000 + random.nextInt(500)));
            base = close;
        }
        return list;
    }

    private double syntheticPrice(String symbol, double anchor) {
        double p = cfg.getLastPrice(symbol);
        if (p <= 0) p = anchor;
        return Math.max(0.0001, p + (random.nextDouble() - 0.5) * p * 0.002);
    }

    private Notification notification(String title, String msg) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setContentTitle(title).setContentText(msg).setSmallIcon(android.R.drawable.ic_dialog_info).setContentIntent(pi).setOngoing(true).build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Nanu Bot Engine", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private String fmt(double d) { return String.format(Locale.US, "%.4f", d); }
    private String shortText(String s) { return s == null ? "" : s.substring(0, Math.min(120, s.length())); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
