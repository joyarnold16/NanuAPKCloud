package com.nanu.aitradingbot;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    AppStore store;
    NanuView nanuView;
    Handler uiHandler = new Handler(Looper.getMainLooper());
    Runnable refresher = new Runnable() {
        @Override public void run() {
            if (store != null) store.engine.tick(false);
            if (nanuView != null) nanuView.invalidate();
            uiHandler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(2, 10, 18));
        getWindow().setNavigationBarColor(Color.rgb(2, 10, 18));
        store = AppStore.get(this);
        nanuView = new NanuView(this, store);
        setContentView(nanuView);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 19);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        uiHandler.post(refresher);
    }

    @Override protected void onPause() {
        uiHandler.removeCallbacks(refresher);
        super.onPause();
    }

    void doAction(String action) {
        if (action == null) return;
        if (action.equals("settings")) { nanuView.activeTab = 4; nanuView.scroll = 0; nanuView.invalidate(); return; }
        if (action.startsWith("tab")) { nanuView.activeTab = Integer.parseInt(action.substring(3)); nanuView.scroll = 0; nanuView.invalidate(); return; }
        if (action.equals("start")) {
            if (store.mode.equals("live") && !store.liveUnlocked) { toast("Live is locked. Open Security and unlock after checklist."); return; }
            store.engine.mode = store.mode;
            store.engine.start();
            startServiceCompat();
            toast("Nanu started in " + store.mode.toUpperCase(Locale.US));
        } else if (action.equals("stop")) {
            store.engine.stop();
            stopService(new Intent(this, NanuBotService.class));
            toast("Nanu stopped");
        } else if (action.equals("panic")) {
            store.engine.panicClose();
            stopService(new Intent(this, NanuBotService.class));
            toast("Panic close activated");
        } else if (action.equals("mode_paper") || action.equals("mode_demo") || action.equals("mode_testnet")) {
            store.mode = action.replace("mode_", "");
            store.liveUnlocked = false;
            store.save();
            toast("Mode changed to " + store.mode.toUpperCase(Locale.US));
        } else if (action.equals("mode_live")) {
            unlockLiveDialog();
        } else if (action.equals("coin_auto")) {
            store.coinMode = "auto"; autoSelectCoins(); store.save(); toast("Auto scalping coin selection enabled");
        } else if (action.equals("coin_manual")) {
            store.coinMode = "manual"; store.save(); toast("Manual coin mode enabled");
        } else if (action.equals("add_coin")) {
            inputDialog("Add coin", "Example: BTC or BTCUSDT", "", false, value -> { store.addCoin(value); store.engine.addJournal("Manual coin added: " + value.toUpperCase()); nanuView.invalidate(); });
        } else if (action.startsWith("remove_")) {
            String coin = action.substring(7); store.removeCoin(coin); toast(coin + " removed");
        } else if (action.equals("api_key")) {
            inputDialog("Binance API Key", "Paste API key", store.apiKey, false, value -> { store.apiKey = value.trim(); store.save(); });
        } else if (action.equals("api_secret")) {
            inputDialog("Binance API Secret", "Paste API secret", store.apiSecret, true, value -> { store.apiSecret = value.trim(); store.save(); });
        } else if (action.equals("telegram")) {
            inputDialog("Telegram Bot Token", "Optional", store.telegramToken, false, value -> { store.telegramToken = value.trim(); store.save(); });
        } else if (action.equals("chatid")) {
            inputDialog("Telegram Chat ID", "Optional", store.telegramChatId, false, value -> { store.telegramChatId = value.trim(); store.save(); });
        } else if (action.equals("api_test")) {
            toast("Testing Binance API...");
            BinanceClient.testApi(store, result -> runOnUiThread(() -> {
                store.engine.addJournal("API health check completed");
                new AlertDialog.Builder(this).setTitle("Binance API Health Check").setMessage(result).setPositiveButton("OK", null).show();
            }));
        } else if (action.equals("reset_paper")) {
            store.engine.equity = 1000; store.engine.todayPnl = 0; store.engine.openPnl = 0; store.engine.panic = false; store.engine.addJournal("Paper wallet reset"); toast("Paper wallet reset");
        } else if (action.equals("export")) {
            new AlertDialog.Builder(this).setTitle("Export / Developer Console").setMessage(developerReport()).setPositiveButton("OK", null).show();
        }
        nanuView.invalidate();
    }

    void startServiceCompat() {
        Intent i = new Intent(this, NanuBotService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    void autoSelectCoins() {
        store.watchlist.clear();
        store.watchlist.add("BTCUSDT");
        store.watchlist.add("ETHUSDT");
        store.watchlist.add("SOLUSDT");
        store.watchlist.add("BNBUSDT");
        store.engine.addJournal("Auto selected BTC, ETH, SOL, BNB for liquidity and spread quality");
    }

    String developerReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Nanu AI Trading Bot v4\n");
        sb.append("Mode: ").append(store.mode).append('\n');
        sb.append("Coin mode: ").append(store.coinMode).append('\n');
        sb.append("Running: ").append(store.engine.running).append('\n');
        sb.append("Panic: ").append(store.engine.panic).append('\n');
        sb.append("Open trades: ").append(store.engine.trades.size()).append('\n');
        sb.append("Watchlist: ").append(store.watchlist).append('\n');
        sb.append("API key: ").append(store.apiKey.isEmpty() ? "not set" : "set").append('\n');
        sb.append("Live unlocked: ").append(store.liveUnlocked).append('\n');
        return sb.toString();
    }

    interface InputCallback { void ok(String value); }

    void inputDialog(String title, String hint, String old, boolean password, InputCallback cb) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(old == null ? "" : old);
        input.setSingleLine(false);
        input.setInputType(password ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD) : InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save", (d, w) -> { cb.ok(input.getText().toString()); nanuView.invalidate(); }).show();
    }

    void unlockLiveDialog() {
        if (store.apiKey.isEmpty() || store.apiSecret.isEmpty()) {
            toast("Add Binance API key and secret before unlocking Live.");
            return;
        }
        EditText input = new EditText(this);
        input.setHint("Type UNLOCK LIVE");
        new AlertDialog.Builder(this)
                .setTitle("Unlock Live Mode")
                .setMessage("Live uses real Binance balance and real orders. Keep withdrawal permission OFF. Continue only after API test and risk checklist.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Unlock", (d, w) -> {
                    if ("UNLOCK LIVE".equals(input.getText().toString().trim())) {
                        store.liveUnlocked = true; store.mode = "live"; store.save(); toast("Live mode unlocked");
                    } else toast("Live not unlocked");
                    nanuView.invalidate();
                }).show();
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    public static class NanuView extends View {
        final MainActivity activity;
        final AppStore store;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        final Map<String, RectF> hits = new HashMap<>();
        int activeTab = 0;
        float scroll = 0, downY = 0, lastY = 0, totalH = 2000;
        boolean moved = false;
        final int bg = Color.rgb(2, 10, 18);
        final int card = Color.rgb(5, 22, 34);
        final int card2 = Color.rgb(7, 28, 42);
        final int cyan = Color.rgb(0, 229, 255);
        final int green = Color.rgb(79, 255, 141);
        final int red = Color.rgb(255, 73, 73);
        final int amber = Color.rgb(255, 185, 69);
        final int white = Color.rgb(235, 248, 255);
        final int muted = Color.rgb(138, 162, 178);

        public NanuView(Context c, AppStore s) { super(c); activity = (MainActivity)c; store = s; setBackgroundColor(bg); }
        float dp(float v) { return v * getResources().getDisplayMetrics().density; }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            store.engine.tick(false);
            hits.clear();
            canvas.drawColor(bg);
            canvas.save();
            canvas.translate(0, -scroll);
            float w = getWidth();
            float y = dp(18);
            y = drawHeader(canvas, y, w);
            if (activeTab == 0) y = drawBridge(canvas, y, w);
            else if (activeTab == 1) y = drawScanner(canvas, y, w);
            else if (activeTab == 2) y = drawBrain(canvas, y, w);
            else if (activeTab == 3) y = drawJournal(canvas, y, w);
            else y = drawSecurity(canvas, y, w);
            y = drawFooter(canvas, y, w);
            totalH = Math.max(y + dp(30), getHeight());
            canvas.restore();
        }

        float drawHeader(Canvas c, float y, float w) {
            text(c, "NANU", dp(24), y + dp(28), dp(34), white, true);
            text(c, "AI TRADING BOT", dp(24), y + dp(58), dp(15), cyan, true, 3f);
            button(c, "settings", w - dp(62), y + dp(12), dp(42), dp(42), "⚙", cyan, false, dp(22));
            button(c, "dot", w - dp(110), y + dp(17), dp(30), dp(30), "•", store.engine.running ? green : cyan, false, dp(34));
            y += dp(76);
            float topH = dp(170);
            card(c, dp(18), y, dp(135), topH, cyan, false);
            text(c, "MARKET MOOD", dp(36), y + dp(32), dp(13), muted, true);
            int moodColor = store.engine.todayPnl < -20 ? red : green;
            text(c, store.engine.marketMood, dp(36), y + dp(65), dp(25), moodColor, true);
            drawMoodIcon(c, dp(84), y + dp(105), moodColor);
            text(c, "Confidence", dp(36), y + dp(145), dp(13), muted, false);
            text(c, store.engine.moodConfidence + "%", dp(108), y + dp(145), dp(13), white, true);

            drawFaceLogo(c, w / 2f, y + dp(78), dp(74));

            card(c, w - dp(153), y, dp(135), topH, cyan, false);
            text(c, "TODAY'S P&L", w - dp(134), y + dp(32), dp(13), muted, true);
            int pnlColor = store.engine.todayPnl < 0 ? red : green;
            text(c, money(store.engine.todayPnl), w - dp(134), y + dp(76), dp(25), pnlColor, true);
            text(c, "USDT", w - dp(134), y + dp(105), dp(22), pnlColor, true);
            text(c, pct(store.engine.todayPnl / 1000.0 * 100.0), w - dp(134), y + dp(137), dp(20), pnlColor, true);
            return y + topH + dp(16);
        }

        float drawBridge(Canvas c, float y, float w) {
            float gap = dp(10), x = dp(18), cw = (w - dp(36) - gap * 3) / 4f;
            metric(c, x, y, cw, "EQUITY", String.format(Locale.US, "%.2f", store.engine.equity), "USDT", store.engine.equity >= 1000 ? green : red); x += cw + gap;
            metric(c, x, y, cw, "24H P&L", money(store.engine.todayPnl), pct(store.engine.todayPnl / 1000 * 100), store.engine.todayPnl >= 0 ? green : red); x += cw + gap;
            metric(c, x, y, cw, "OPEN P&L", money(store.engine.openPnl), "USDT", store.engine.openPnl >= 0 ? green : red); x += cw + gap;
            metric(c, x, y, cw, "WIN RATE", String.format(Locale.US, "%.1f%%", store.engine.winRate), "(" + (int)(store.engine.winRate / 100 * 70) + " / 70)", store.engine.winRate > 50 ? green : red);
            y += dp(116);

            card(c, dp(18), y, w - dp(36), dp(205), cyan, false);
            text(c, "TRADING BOT", dp(38), y + dp(34), dp(16), muted, true);
            text(c, store.engine.running ? "RUNNING" : (store.engine.panic ? "PANIC" : "STOPPED"), dp(160), y + dp(34), dp(16), store.engine.running ? green : (store.engine.panic ? red : muted), true);
            button(c, "start", dp(38), y + dp(58), dp(120), dp(70), "▶  Start", green, true, dp(16));
            button(c, "stop", dp(168), y + dp(58), dp(120), dp(70), "■  Stop", cyan, false, dp(16));
            button(c, "panic", dp(38), y + dp(139), dp(250), dp(48), "⚠  Panic Close", red, true, dp(15));
            drawRadar(c, w * .56f, y + dp(105), dp(52), store.engine.todayPnl < -20 ? red : cyan);
            text(c, "EXCHANGE", w - dp(145), y + dp(56), dp(12), muted, true);
            text(c, "Binance Spot •", w - dp(145), y + dp(81), dp(16), white, true);
            text(c, "Mode: " + cap(store.mode), w - dp(145), y + dp(108), dp(17), store.mode.equals("live") ? amber : green, true);
            text(c, "Change in Security", w - dp(145), y + dp(133), dp(12), cyan, false);
            text(c, "Internal APK engine active", w - dp(145), y + dp(158), dp(12), muted, false);
            y += dp(224);

            y = drawTabs(c, y, w);
            card(c, dp(18), y, w - dp(36), dp(78 + store.engine.trades.size() * 112), cyan, false);
            text(c, "SIGNAL BRIDGE", dp(38), y + dp(38), dp(22), white, true);
            text(c, store.engine.trades.size() + " Active", w - dp(105), y + dp(38), dp(13), green, true);
            float cy = y + dp(58);
            for (NanuEngine.Trade t : store.engine.trades) { signalCard(c, dp(38), cy, w - dp(76), t); cy += dp(112); }
            y += dp(92 + store.engine.trades.size() * 112);
            y = drawBottomPanels(c, y, w);
            return y;
        }

        float drawScanner(Canvas c, float y, float w) {
            y = drawTabs(c, y, w);
            card(c, dp(18), y, w - dp(36), dp(430), cyan, false);
            text(c, "SCANNER", dp(38), y + dp(42), dp(24), white, true);
            text(c, "Coin Selection", dp(38), y + dp(76), dp(14), muted, true);
            button(c, "coin_auto", dp(38), y + dp(92), (w - dp(86)) / 2, dp(56), "AUTO SCALPING", store.coinMode.equals("auto") ? green : cyan, store.coinMode.equals("auto"), dp(15));
            button(c, "coin_manual", w / 2 + dp(5), y + dp(92), (w - dp(86)) / 2, dp(56), "MANUAL COINS", store.coinMode.equals("manual") ? green : cyan, store.coinMode.equals("manual"), dp(15));
            text(c, store.coinMode.equals("auto") ? "Nanu selects high-liquidity, low-spread coins for scalping." : "You select coins manually. Tap Add Coin or remove a chip.", dp(38), y + dp(172), dp(13), muted, false);
            button(c, "add_coin", dp(38), y + dp(192), dp(130), dp(44), "+ Add Coin", cyan, false, dp(14));
            float chipX = dp(38), chipY = y + dp(252);
            for (String coin : store.watchlist) {
                float ww = Math.max(dp(96), coin.length() * dp(9));
                if (chipX + ww > w - dp(38)) { chipX = dp(38); chipY += dp(48); }
                button(c, "remove_" + coin, chipX, chipY, ww, dp(38), coin + " ×", green, false, dp(13));
                chipX += ww + dp(10);
            }
            y += dp(452);
            card(c, dp(18), y, w - dp(36), dp(345), cyan, false);
            text(c, "AUTO RANKING ENGINE", dp(38), y + dp(38), dp(21), white, true);
            String[] rows = {"Volume quality", "Spread check", "Volatility window", "EMA / MACD alignment", "Pump-dump danger filter", "Minimum order filter"};
            int[] scores = {92, 88, 76, 71, 84, 100};
            for (int i = 0; i < rows.length; i++) {
                float yy = y + dp(78 + i * 42);
                text(c, rows[i], dp(38), yy, dp(15), white, false);
                bar(c, w - dp(190), yy - dp(11), dp(120), dp(8), scores[i], scores[i] > 80 ? green : amber);
                text(c, scores[i] + "%", w - dp(60), yy, dp(13), scores[i] > 80 ? green : amber, true);
            }
            return y + dp(370);
        }

        float drawBrain(Canvas c, float y, float w) {
            y = drawTabs(c, y, w);
            card(c, dp(18), y, w - dp(36), dp(600), cyan, false);
            text(c, "BRAIN / ML DECISION ENGINE", dp(38), y + dp(42), dp(22), white, true);
            text(c, "Professional explainable scalping logic", dp(38), y + dp(68), dp(13), muted, false);
            brainBlock(c, y + dp(105), w, "Market Regime", store.engine.regime, cyan);
            brainBlock(c, y + dp(195), w, "Signal Reason", store.engine.lastBrain, green);
            brainBlock(c, y + dp(305), w, "Risk Thought", store.engine.todayPnl < -50 ? "Loss pressure detected. Nanu suggests cooldown, smaller risk, or stop." : "Risk shield is clear. Daily loss guard and max open trades are under limit.", store.engine.todayPnl < -50 ? red : green);
            text(c, "INDICATOR MATRIX", dp(38), y + dp(438), dp(17), white, true);
            String[] ind = {"EMA Trend", "RSI Zone", "MACD Signal", "Volume", "Spread", "Candle Pattern"};
            for (int i = 0; i < ind.length; i++) {
                float xx = dp(38) + (i % 2) * ((w - dp(96)) / 2);
                float yy = y + dp(470) + (i / 2) * dp(38);
                text(c, ind[i], xx, yy, dp(14), muted, false);
                text(c, i == 4 ? "LOW" : "OK", xx + dp(115), yy, dp(14), i == 4 ? green : cyan, true);
            }
            return y + dp(624);
        }

        float drawJournal(Canvas c, float y, float w) {
            y = drawTabs(c, y, w);
            card(c, dp(18), y, w - dp(36), dp(640), cyan, false);
            text(c, "JOURNAL", dp(38), y + dp(42), dp(24), white, true);
            text(c, "Recent events, trade memory, and safety notes", dp(38), y + dp(68), dp(13), muted, false);
            float yy = y + dp(105);
            int n = Math.min(12, store.engine.journal.size());
            for (int i = 0; i < n; i++) {
                text(c, store.engine.journal.get(i), dp(38), yy, dp(13), i == 0 ? green : white, false);
                yy += dp(38);
            }
            button(c, "export", dp(38), y + dp(565), w - dp(76), dp(52), "Developer Console / Export Status", cyan, false, dp(15));
            return y + dp(665);
        }

        float drawSecurity(Canvas c, float y, float w) {
            y = drawTabs(c, y, w);
            card(c, dp(18), y, w - dp(36), dp(780), cyan, false);
            text(c, "SECURITY / SETTINGS", dp(38), y + dp(42), dp(23), white, true);
            text(c, "Trading mode, API health, keys, and risk shield", dp(38), y + dp(70), dp(13), muted, false);
            text(c, "TRADING MODE", dp(38), y + dp(112), dp(15), white, true);
            float bw = (w - dp(96)) / 2f;
            modeButton(c, "mode_paper", dp(38), y + dp(128), bw, dp(60), "PAPER", "Safe internal simulation", store.mode.equals("paper"));
            modeButton(c, "mode_demo", dp(58) + bw, y + dp(128), bw, dp(60), "DEMO", "Binance-style practice", store.mode.equals("demo"));
            modeButton(c, "mode_testnet", dp(38), y + dp(198), bw, dp(60), "TESTNET", "API order testing", store.mode.equals("testnet"));
            modeButton(c, "mode_live", dp(58) + bw, y + dp(198), bw, dp(60), "LIVE", store.liveUnlocked ? "Unlocked" : "Locked by checklist", store.mode.equals("live"));
            text(c, "Live mode requires API key + API test + typing UNLOCK LIVE.", dp(38), y + dp(282), dp(12), amber, false);

            text(c, "BINANCE API", dp(38), y + dp(330), dp(15), white, true);
            button(c, "api_key", dp(38), y + dp(346), w - dp(76), dp(48), store.apiKey.isEmpty() ? "Add Binance API Key" : "API Key: saved ••••", cyan, false, dp(14));
            button(c, "api_secret", dp(38), y + dp(402), w - dp(76), dp(48), store.apiSecret.isEmpty() ? "Add API Secret" : "API Secret: saved ••••", cyan, false, dp(14));
            button(c, "api_test", dp(38), y + dp(460), w - dp(76), dp(52), "Test Binance API Health", green, true, dp(15));

            text(c, "TELEGRAM / ALERTS", dp(38), y + dp(538), dp(15), white, true);
            button(c, "telegram", dp(38), y + dp(554), bw, dp(48), store.telegramToken.isEmpty() ? "Bot Token" : "Token saved", cyan, false, dp(14));
            button(c, "chatid", dp(58) + bw, y + dp(554), bw, dp(48), store.telegramChatId.isEmpty() ? "Chat ID" : "Chat ID saved", cyan, false, dp(14));

            text(c, "RISK SHIELD", dp(38), y + dp(638), dp(15), white, true);
            text(c, "Risk/trade: " + store.riskPerTrade + "%   Daily loss: " + store.dailyLossLimit + "%", dp(38), y + dp(666), dp(13), muted, false);
            text(c, "SL: " + store.stopLoss + "%   TP: " + store.takeProfit + "%   Max open: " + store.maxOpenTrades, dp(38), y + dp(694), dp(13), muted, false);
            button(c, "reset_paper", dp(38), y + dp(716), bw, dp(46), "Reset Paper Wallet", red, false, dp(13));
            button(c, "export", dp(58) + bw, y + dp(716), bw, dp(46), "Developer Console", cyan, false, dp(13));
            return y + dp(805);
        }

        float drawFooter(Canvas c, float y, float w) {
            text(c, "Nanu AI Trading Bot • Paper first • Live locked • No guaranteed profit", dp(28), y + dp(24), dp(11), muted, false);
            return y + dp(50);
        }

        float drawTabs(Canvas c, float y, float w) {
            String[] names = {"⚓ Bridge", "◉ Scanner", "◈ Brain", "▤ Journal", "▣ Security"};
            float x = dp(18), gap = dp(7), tw = (w - dp(36) - gap * 4) / 5f;
            for (int i = 0; i < 5; i++) button(c, "tab" + i, x + i * (tw + gap), y, tw, dp(56), names[i], i == activeTab ? cyan : muted, i == activeTab, dp(11));
            return y + dp(74);
        }

        float drawBottomPanels(Canvas c, float y, float w) {
            float gap = dp(12), bw = (w - dp(48)) / 2f;
            card(c, dp(18), y, bw, dp(180), cyan, false);
            text(c, "API & SECURITY", dp(36), y + dp(34), dp(16), green, true);
            text(c, "Mode", dp(36), y + dp(66), dp(13), muted, false); text(c, cap(store.mode), dp(130), y + dp(66), dp(13), white, true);
            text(c, "API Status", dp(36), y + dp(96), dp(13), muted, false); text(c, store.apiKey.isEmpty() ? "Not set" : "Ready", dp(130), y + dp(96), dp(13), store.apiKey.isEmpty() ? amber : green, true);
            text(c, "Live Lock", dp(36), y + dp(126), dp(13), muted, false); text(c, store.liveUnlocked ? "Unlocked" : "Enabled", dp(130), y + dp(126), dp(13), store.liveUnlocked ? amber : green, true);
            card(c, dp(30) + bw, y, bw, dp(180), cyan, false);
            text(c, "JOURNAL — RECENT", dp(48) + bw, y + dp(34), dp(16), white, true);
            for (int i = 0; i < Math.min(4, store.engine.journal.size()); i++) text(c, store.engine.journal.get(i), dp(48) + bw, y + dp(66 + i * 28), dp(11), i == 0 ? green : muted, false);
            return y + dp(202);
        }

        void metric(Canvas c, float x, float y, float w, String label, String value, String sub, int col) {
            card(c, x, y, w, dp(96), cyan, false);
            text(c, label, x + dp(14), y + dp(26), dp(11), muted, true);
            text(c, value, x + dp(14), y + dp(55), dp(18), col, true);
            text(c, sub, x + dp(14), y + dp(78), dp(13), col, true);
        }

        void signalCard(Canvas c, float x, float y, float w, NanuEngine.Trade t) {
            card(c, x, y, w, dp(96), cyan, false);
            int col = t.pnl < 0 ? red : green;
            drawCoin(c, x + dp(36), y + dp(48), t.symbol);
            text(c, t.symbol, x + dp(78), y + dp(34), dp(18), white, true);
            text(c, t.side, x + dp(184), y + dp(34), dp(15), col, true);
            text(c, "Isolated • " + t.leverage + "x", x + dp(78), y + dp(58), dp(12), muted, false);
            text(c, "Entry " + String.format(Locale.US, "%.2f", t.entry) + "   Mark " + String.format(Locale.US, "%.2f", t.mark), x + dp(78), y + dp(77), dp(11), muted, false);
            text(c, "P&L", x + w - dp(118), y + dp(28), dp(11), muted, true);
            text(c, money(t.pnl) + " USDT", x + w - dp(118), y + dp(55), dp(15), col, true);
            text(c, pct(t.pnlPct), x + w - dp(118), y + dp(78), dp(14), col, true);
        }

        void brainBlock(Canvas c, float y, float w, String title, String body, int color) {
            card(c, dp(38), y, w - dp(76), dp(76), color, false);
            text(c, title, dp(56), y + dp(28), dp(14), color, true);
            textWrap(c, body, dp(56), y + dp(50), w - dp(112), dp(12), white);
        }

        void modeButton(Canvas c, String id, float x, float y, float w, float h, String title, String sub, boolean selected) {
            button(c, id, x, y, w, h, title, selected ? green : cyan, selected, dp(16));
            text(c, sub, x + dp(12), y + dp(45), dp(10), selected ? bg : muted, false);
        }

        void card(Canvas c, float x, float y, float w, float h, int stroke, boolean fillStrong) {
            r.set(x, y, x + w, y + h);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(x, y, x, y + h, fillStrong ? Color.rgb(5, 52, 64) : card, card2, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, dp(18), dp(18), p); p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.2f)); p.setColor(adjustAlpha(stroke, 150));
            c.drawRoundRect(r, dp(18), dp(18), p); p.setStyle(Paint.Style.FILL);
        }

        void button(Canvas c, String id, float x, float y, float w, float h, String label, int col, boolean filled, float size) {
            r.set(x, y, x + w, y + h); hits.put(id, new RectF(r));
            p.setStyle(Paint.Style.FILL); p.setColor(filled ? adjustAlpha(col, 210) : adjustAlpha(card2, 230));
            c.drawRoundRect(r, dp(15), dp(15), p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.4f)); p.setColor(col); c.drawRoundRect(r, dp(15), dp(15), p); p.setStyle(Paint.Style.FILL);
            text(c, label, x + w / 2, y + h / 2 + size / 3, size, filled ? bg : (col == muted ? muted : white), true, 0, Paint.Align.CENTER);
        }

        void bar(Canvas c, float x, float y, float w, float h, int pct, int col) {
            p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(24, 38, 48)); r.set(x, y, x + w, y + h); c.drawRoundRect(r, h/2, h/2, p);
            p.setColor(col); r.set(x, y, x + w * pct / 100f, y + h); c.drawRoundRect(r, h/2, h/2, p);
        }

        void drawFaceLogo(Canvas c, float cx, float cy, float rad) {
            int col = store.engine.todayPnl < -20 ? red : cyan;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(3)); p.setColor(adjustAlpha(col, 210));
            c.drawCircle(cx, cy, rad, p); p.setStrokeWidth(dp(1)); c.drawCircle(cx, cy, rad * .76f, p);
            p.setStyle(Paint.Style.FILL); p.setColor(white); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true); p.setTextSize(dp(26)); c.drawText("N", cx, cy - rad - dp(13), p); p.setFakeBoldText(false);
            // compass points
            p.setColor(white); Path path = new Path();
            path.moveTo(cx - rad - dp(8), cy); path.lineTo(cx - rad - dp(25), cy - dp(8)); path.lineTo(cx - rad - dp(25), cy + dp(8)); path.close(); c.drawPath(path, p);
            path.reset(); path.moveTo(cx + rad + dp(8), cy); path.lineTo(cx + rad + dp(25), cy - dp(8)); path.lineTo(cx + rad + dp(25), cy + dp(8)); path.close(); c.drawPath(path, p);
            p.setStyle(Paint.Style.FILL); p.setColor(store.engine.todayPnl < -20 ? red : green);
            c.drawOval(new RectF(cx - dp(32), cy - dp(13), cx - dp(18), cy - dp(6)), p);
            c.drawOval(new RectF(cx + dp(18), cy - dp(13), cx + dp(32), cy - dp(6)), p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(4)); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(white);
            if (store.engine.todayPnl < -100) {
                Path mouth = new Path(); mouth.moveTo(cx - dp(26), cy + dp(36)); mouth.quadTo(cx, cy + dp(16), cx + dp(26), cy + dp(36)); c.drawPath(mouth, p);
                p.setStrokeWidth(dp(3)); p.setColor(Color.rgb(160, 230, 255)); c.drawLine(cx - dp(25), cy + dp(2), cx - dp(30), cy + dp(34), p); c.drawLine(cx + dp(25), cy + dp(2), cx + dp(30), cy + dp(34), p);
            } else if (store.engine.todayPnl < -20) {
                Path mouth = new Path(); mouth.moveTo(cx - dp(24), cy + dp(30)); mouth.quadTo(cx, cy + dp(13), cx + dp(24), cy + dp(30)); c.drawPath(mouth, p);
            } else {
                Path mouth = new Path(); mouth.moveTo(cx - dp(32), cy + dp(20)); mouth.quadTo(cx, cy + (store.engine.todayPnl > 80 ? dp(48) : dp(35)), cx + dp(32), cy + dp(20)); c.drawPath(mouth, p);
                if (store.engine.todayPnl > 80) { p.setStrokeWidth(dp(6)); p.setColor(white); c.drawLine(cx - dp(19), cy + dp(33), cx + dp(19), cy + dp(33), p); }
            }
            p.setStrokeCap(Paint.Cap.BUTT); p.setStyle(Paint.Style.FILL);
        }

        void drawMoodIcon(Canvas c, float cx, float cy, int col) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(4)); p.setColor(col);
            c.drawCircle(cx, cy, dp(23), p);
            p.setStrokeWidth(dp(3));
            c.drawLine(cx - dp(13), cy - dp(18), cx - dp(22), cy - dp(30), p);
            c.drawLine(cx + dp(13), cy - dp(18), cx + dp(22), cy - dp(30), p);
            p.setStyle(Paint.Style.FILL);
        }

        void drawRadar(Canvas c, float cx, float cy, float rad, int col) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.2f)); p.setColor(adjustAlpha(col, 180));
            for (int i = 1; i <= 3; i++) c.drawCircle(cx, cy, rad * i / 3f, p);
            c.drawLine(cx - rad, cy, cx + rad, cy, p); c.drawLine(cx, cy - rad, cx, cy + rad, p);
            p.setStyle(Paint.Style.FILL); p.setColor(col); c.drawCircle(cx, cy, dp(6), p); c.drawCircle(cx + rad * .45f, cy - rad * .35f, dp(4), p); c.drawCircle(cx - rad * .3f, cy + rad * .42f, dp(3), p);
        }

        void drawCoin(Canvas c, float cx, float cy, String s) {
            int col = Color.rgb(247, 147, 26); String letter = s.substring(0, Math.min(1, s.length()));
            if (s.startsWith("ETH")) { col = Color.rgb(92, 112, 255); letter = "◇"; }
            else if (s.startsWith("SOL")) { col = Color.rgb(130, 67, 255); letter = "≈"; }
            else if (s.startsWith("BNB")) { col = Color.rgb(243, 186, 47); letter = "◆"; }
            p.setStyle(Paint.Style.FILL); p.setColor(col); c.drawCircle(cx, cy, dp(26), p);
            text(c, s.startsWith("BTC") ? "₿" : letter, cx, cy + dp(8), dp(22), Color.WHITE, true, 0, Paint.Align.CENTER);
        }

        void text(Canvas c, String s, float x, float y, float size, int color, boolean bold) { text(c, s, x, y, size, color, bold, 0, Paint.Align.LEFT); }
        void text(Canvas c, String s, float x, float y, float size, int color, boolean bold, float spacing) { text(c, s, x, y, size, color, bold, spacing, Paint.Align.LEFT); }
        void text(Canvas c, String s, float x, float y, float size, int color, boolean bold, float spacing, Paint.Align align) {
            p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTextSize(size); p.setFakeBoldText(bold); p.setTextAlign(align); p.setLetterSpacing(spacing / 100f); c.drawText(s, x, y, p); p.setFakeBoldText(false); p.setLetterSpacing(0); p.setTextAlign(Paint.Align.LEFT);
        }

        void textWrap(Canvas c, String s, float x, float y, float maxW, float size, int color) {
            p.setTextSize(size); p.setColor(color); p.setFakeBoldText(false);
            String[] words = s.split(" "); String line = ""; float yy = y;
            for (String word : words) {
                String test = line.length() == 0 ? word : line + " " + word;
                if (p.measureText(test) > maxW) { c.drawText(line, x, yy, p); yy += size * 1.35f; line = word; } else line = test;
            }
            if (line.length() > 0) c.drawText(line, x, yy, p);
        }

        String money(double v) { return String.format(Locale.US, "%+.2f", v); }
        String pct(double v) { return String.format(Locale.US, "%+.2f%%", v); }
        String cap(String s) { return s == null || s.length() == 0 ? "" : s.substring(0,1).toUpperCase(Locale.US) + s.substring(1); }
        int adjustAlpha(int color, int a) { return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color)); }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) { downY = lastY = e.getY(); moved = false; return true; }
            if (e.getAction() == MotionEvent.ACTION_MOVE) {
                float dy = e.getY() - lastY; lastY = e.getY(); if (Math.abs(e.getY() - downY) > dp(8)) moved = true;
                scroll = Math.max(0, Math.min(totalH - getHeight(), scroll - dy)); invalidate(); return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) {
                if (!moved) {
                    float x = e.getX(), y = e.getY() + scroll;
                    for (Map.Entry<String, RectF> entry : new ArrayList<>(hits.entrySet())) {
                        if (entry.getValue().contains(x, y)) { activity.doAction(entry.getKey()); break; }
                    }
                }
                return true;
            }
            return true;
        }
    }
}
