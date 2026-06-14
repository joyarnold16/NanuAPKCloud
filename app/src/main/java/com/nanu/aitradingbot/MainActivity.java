package com.nanu.aitradingbot;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    AppStore store;
    LinearLayout root;
    ScrollView scroll;
    int activeTab = 0;
    final Handler handler = new Handler(Looper.getMainLooper());
    final Runnable refresh = new Runnable() { @Override public void run() { if (store != null) store.engine.tick(false); render(false); handler.postDelayed(this, 2000); } };

    final int BG = Color.rgb(2, 10, 18);
    final int CARD = Color.rgb(5, 22, 34);
    final int CARD2 = Color.rgb(7, 30, 45);
    final int CYAN = Color.rgb(0, 229, 255);
    final int GREEN = Color.rgb(79, 255, 141);
    final int RED = Color.rgb(255, 73, 73);
    final int AMBER = Color.rgb(255, 190, 70);
    final int WHITE = Color.rgb(238, 248, 255);
    final int MUTED = Color.rgb(143, 164, 180);

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        store = AppStore.get(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 88);
        }
        render(true);
    }

    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }

    int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    int statusBarHeight() { int id = getResources().getIdentifier("status_bar_height", "dimen", "android"); return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24); }

    void render(boolean newScroll) {
        int oldY = scroll == null ? 0 : scroll.getScrollY();
        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), statusBarHeight() + dp(12), dp(16), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        buildHeader();
        buildMoodHero();
        buildTabs();
        if (activeTab == 0) buildBridge();
        else if (activeTab == 1) buildScanner();
        else if (activeTab == 2) buildBrain();
        else if (activeTab == 3) buildJournal();
        else buildSecurity();
        footer();
        setContentView(scroll);
        if (!newScroll) scroll.post(() -> scroll.scrollTo(0, oldY));
    }

    void buildHeader() {
        LinearLayout row = row(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, 0, 0, dp(12));
        LinearLayout titles = col();
        TextView title = tv("NANU", 34, WHITE, true); title.setLetterSpacing(.04f); titles.addView(title);
        TextView sub = tv("AI TRADING BOT", 15, CYAN, true); sub.setLetterSpacing(.22f); titles.addView(sub);
        row.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView status = pill(store.engine.running ? "ACTIVE" : "IDLE", store.engine.running ? GREEN : CYAN, 12); status.setMinWidth(dp(78)); row.addView(status);
        TextView settings = pill("Settings", CYAN, 13); settings.setPadding(dp(14), dp(10), dp(14), dp(10)); settings.setOnClickListener(v -> { activeTab = 4; render(true); });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2); sp.leftMargin = dp(8); row.addView(settings, sp);
        root.addView(row);
    }

    void buildMoodHero() {
        FrameLayout logoWrap = new FrameLayout(this);
        FaceLogoView face = new FaceLogoView(this); face.bind(store.engine);
        logoWrap.addView(face, new FrameLayout.LayoutParams(dp(136), dp(136), Gravity.CENTER));
        root.addView(logoWrap, new LinearLayout.LayoutParams(-1, dp(148)));
        addGap(8);

        LinearLayout hero = row(); hero.setGravity(Gravity.CENTER_VERTICAL); hero.setBaselineAligned(false);
        LinearLayout mood = cardBox(); mood.setMinimumHeight(dp(132));
        mood.addView(label("MARKET MOOD"));
        mood.addView(big(store.engine.marketMood, pnlColor(), 22));
        mood.addView(tv("Confidence " + store.engine.moodConfidence + "%", 13, MUTED, false));
        hero.addView(mood, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout pnl = cardBox(); pnl.setMinimumHeight(dp(132));
        pnl.addView(label("TODAY'S P&L"));
        pnl.addView(big(formatMoney(store.engine.todayPnl), pnlColor(), 24));
        pnl.addView(tv("USDT  " + percent(store.engine.todayPnl / 1000 * 100), 17, pnlColor(), true));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, -2, 1); pp.leftMargin = dp(10);
        hero.addView(pnl, pp);
        root.addView(hero);
        addGap(12);
    }

    void buildTabs() {
        LinearLayout tabs = row(); tabs.setGravity(Gravity.CENTER); tabs.setBaselineAligned(false);
        String[] names = {"Bridge", "Scanner", "Brain", "Journal", "Security"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView t = pill(names[i], idx == activeTab ? BG : MUTED, 13);
            t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            t.setTextColor(idx == activeTab ? BG : MUTED);
            t.setBackground(bg(idx == activeTab ? CYAN : CARD, idx == activeTab ? CYAN : Color.rgb(32, 72, 91), 14));
            t.setMinHeight(dp(48)); t.setGravity(Gravity.CENTER);
            t.setOnClickListener(v -> { activeTab = idx; render(true); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1); lp.setMargins(dp(3), 0, dp(3), 0); tabs.addView(t, lp);
        }
        root.addView(tabs); addGap(14);
    }

    void buildBridge() {
        LinearLayout metrics1 = row(); metrics1.setBaselineAligned(false);
        addMetric(metrics1, "EQUITY", String.format(Locale.US, "%.2f", store.engine.equity), "USDT", store.engine.equity >= 1000 ? GREEN : RED);
        addMetric(metrics1, "24H P&L", formatMoney(store.engine.todayPnl), percent(store.engine.todayPnl / 1000 * 100), pnlColor());
        root.addView(metrics1); addGap(10);
        LinearLayout metrics2 = row(); metrics2.setBaselineAligned(false);
        addMetric(metrics2, "OPEN P&L", formatMoney(store.engine.openPnl), "USDT", store.engine.openPnl >= 0 ? GREEN : RED);
        addMetric(metrics2, "WIN RATE", String.format(Locale.US, "%.1f%%", store.engine.winRate), "(" + (int)(store.engine.winRate / 100 * 70) + " / 70)", store.engine.winRate > 50 ? GREEN : RED);
        root.addView(metrics2); addGap(12);

        LinearLayout control = cardBox();
        LinearLayout line = row(); line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(section("TRADING BOT"), new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(tv(store.engine.running ? "RUNNING" : (store.engine.panic ? "PANIC" : "STOPPED"), 15, store.engine.running ? GREEN : (store.engine.panic ? RED : MUTED), true));
        control.addView(line);
        addGap(control, 8);
        LinearLayout buttons = row();
        buttons.addView(actionButton("Start", GREEN, v -> startBot()), new LinearLayout.LayoutParams(0, dp(56), 1));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(56), 1); blp.leftMargin = dp(10); buttons.addView(actionButton("Stop", CYAN, v -> stopBot()), blp);
        control.addView(buttons); addGap(control, 10);
        control.addView(actionButton("Panic Close", RED, v -> panic()), new LinearLayout.LayoutParams(-1, dp(56)));
        addGap(control, 10);
        TextView mode = tv("Mode: " + store.mode.toUpperCase(Locale.US) + "  •  Exchange: Binance Spot  •  Coin mode: " + (store.autoCoinMode ? "Auto" : "Manual"), 13, MUTED, false); control.addView(mode);
        TextView hint = tv("Change Paper / Demo / Testnet / Live inside Security.", 12, AMBER, false); control.addView(hint);
        root.addView(control); addGap(12);

        buildSignalCards(false);
        addBottomPanels();
    }

    void buildScanner() {
        LinearLayout box = cardBox();
        box.addView(screenTitle("SCANNER"));
        box.addView(tv("Manual watchlist or Auto Scalping coin selection. Nanu ranks coins by liquidity, volatility, spread, trend, and indicator alignment.", 13, MUTED, false));
        addGap(box, 12);
        LinearLayout modeRow = row(); modeRow.addView(actionButton("Manual Coin Mode", CYAN, v -> { store.autoCoinMode = false; store.save(); toast("Manual coin mode"); render(true); }), new LinearLayout.LayoutParams(0, dp(54), 1));
        LinearLayout.LayoutParams ar = new LinearLayout.LayoutParams(0, dp(54), 1); ar.leftMargin = dp(10); modeRow.addView(actionButton("Auto Scalping Mode", GREEN, v -> { store.autoCoinMode = true; store.autoSelectCoins(); toast("Auto selector active"); render(true); }), ar); box.addView(modeRow);
        addGap(box, 12);
        box.addView(actionButton("Add Coin / Token", CYAN, v -> input("Add Coin", "Example: XRP or XRPUSDT", "", false, s -> { store.addCoin(s); store.engine.addJournal("Coin added: " + store.normalizeCoin(s)); render(true); })), new LinearLayout.LayoutParams(-1, dp(54)));
        addGap(box, 10);
        for (String coin : store.watchlist) {
            LinearLayout row = cardMini(); row.setGravity(Gravity.CENTER_VERTICAL); row.addView(tv(coin, 17, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(tv(store.autoCoinMode ? "AUTO" : "MANUAL", 12, store.autoCoinMode ? GREEN : CYAN, true));
            TextView remove = pill("Remove", RED, 12); remove.setOnClickListener(v -> { store.removeCoin(coin); render(true); });
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2); rp.leftMargin = dp(8); row.addView(remove, rp); box.addView(row); addGap(box, 8);
        }
        root.addView(box); addGap(12); buildSignalCards(true);
    }

    void buildBrain() {
        LinearLayout box = cardBox(); box.addView(screenTitle("BRAIN / ML DECISION ROOM"));
        box.addView(tv("Professional reasoning layer for scalping decisions. This is not profit guarantee; it is trade explanation, risk review, and learning memory.", 13, MUTED, false)); addGap(box, 12);
        for (String note : store.engine.brain) { LinearLayout n = cardMini(); n.addView(tv(note, 14, WHITE, false)); box.addView(n); addGap(box, 8); }
        LinearLayout matrix = cardMini(); matrix.setOrientation(LinearLayout.VERTICAL);
        matrix.addView(section("Indicator Matrix"));
        matrix.addView(tv("EMA: aligned  •  RSI: healthy  •  MACD: confirmation watch  •  Volume: acceptable", 13, MUTED, false));
        matrix.addView(tv("Risk approval: max trades, daily loss, panic state, and live lock all checked before action.", 13, MUTED, false));
        box.addView(matrix); root.addView(box);
    }

    void buildJournal() {
        LinearLayout box = cardBox(); box.addView(screenTitle("JOURNAL")); box.addView(tv("Events, entries, exits, risk decisions, API checks, and learning notes.", 13, MUTED, false)); addGap(box, 12);
        for (String j : store.engine.journal) { LinearLayout item = cardMini(); item.addView(tv(j, 13, j.contains("PANIC") ? RED : WHITE, false)); box.addView(item); addGap(box, 8); }
        box.addView(actionButton("Export Developer Report", CYAN, v -> alert("Developer Report", developerReport())), new LinearLayout.LayoutParams(-1, dp(54)));
        root.addView(box);
    }

    void buildSecurity() {
        LinearLayout box = cardBox();
        box.addView(screenTitle("SECURITY / SETTINGS"));
        box.addView(tv("Trading mode, API health, keys, risk shield, coin selection and live unlock.", 13, MUTED, false)); addGap(box, 14);
        box.addView(section("TRADING MODE")); addGap(box, 8);
        LinearLayout m1 = row(); m1.addView(modeButton("PAPER", "Safe simulation", "paper"), new LinearLayout.LayoutParams(0, dp(76), 1)); LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, dp(76), 1); mlp.leftMargin = dp(10); m1.addView(modeButton("DEMO", "Exchange practice", "demo"), mlp); box.addView(m1); addGap(box, 10);
        LinearLayout m2 = row(); m2.addView(modeButton("TESTNET", "API order test", "testnet"), new LinearLayout.LayoutParams(0, dp(76), 1)); LinearLayout.LayoutParams mlp2 = new LinearLayout.LayoutParams(0, dp(76), 1); mlp2.leftMargin = dp(10); m2.addView(modeButton("LIVE", "Locked checklist", "live"), mlp2); box.addView(m2);
        box.addView(tv("Live requires API key + API health check + risk limits + typing UNLOCK LIVE.", 12, AMBER, false)); addGap(box, 18);

        box.addView(section("BINANCE API")); addGap(box, 8);
        box.addView(actionButton(store.apiKey.isEmpty() ? "Add Binance API Key" : "API Key Saved • Tap to Update", CYAN, v -> input("Binance API Key", "Paste key", store.apiKey, false, s -> { store.apiKey = s.trim(); store.save(); render(true); })), new LinearLayout.LayoutParams(-1, dp(54))); addGap(box, 8);
        box.addView(actionButton(store.apiSecret.isEmpty() ? "Add API Secret" : "API Secret Saved • Tap to Update", CYAN, v -> input("Binance API Secret", "Paste secret", store.apiSecret, true, s -> { store.apiSecret = s.trim(); store.save(); render(true); })), new LinearLayout.LayoutParams(-1, dp(54))); addGap(box, 8);
        box.addView(actionButton("Test Binance API Health", GREEN, v -> testApi()), new LinearLayout.LayoutParams(-1, dp(56))); addGap(box, 18);

        box.addView(section("RISK SHIELD")); addGap(box, 8);
        box.addView(tv("Risk/trade: " + store.riskPerTrade + "%  •  Daily loss limit: " + store.dailyLossLimit + "%  •  Max open trades: " + store.maxOpenTrades, 13, MUTED, false));
        box.addView(tv("Stop-loss: " + store.stopLoss + "%  •  Take-profit: " + store.takeProfit + "%  •  Trailing: " + store.trailingStop + "%", 13, MUTED, false)); addGap(box, 8);
        box.addView(actionButton("Reset Paper Wallet", AMBER, v -> { store.engine.resetPaper(); render(true); }), new LinearLayout.LayoutParams(-1, dp(52))); addGap(box, 18);

        box.addView(section("TELEGRAM / ALERTS")); addGap(box, 8);
        LinearLayout tele = row(); tele.addView(actionButton("Bot Token", CYAN, v -> input("Telegram Bot Token", "Optional", store.telegramToken, false, s -> { store.telegramToken=s.trim(); store.save(); })), new LinearLayout.LayoutParams(0, dp(52), 1)); LinearLayout.LayoutParams chat = new LinearLayout.LayoutParams(0, dp(52), 1); chat.leftMargin=dp(10); tele.addView(actionButton("Chat ID", CYAN, v -> input("Telegram Chat ID", "Optional", store.telegramChatId, false, s -> { store.telegramChatId=s.trim(); store.save(); })), chat); box.addView(tele);
        root.addView(box);
    }

    void buildSignalCards(boolean showReason) {
        LinearLayout box = cardBox();
        LinearLayout head = row(); head.setGravity(Gravity.CENTER_VERTICAL); head.addView(section("SIGNAL BRIDGE"), new LinearLayout.LayoutParams(0, -2, 1)); head.addView(tv(store.engine.trades.size() + " Active", 13, GREEN, true)); box.addView(head); addGap(box, 8);
        for (NanuEngine.Trade t : store.engine.trades) {
            LinearLayout item = cardMini(); item.setOrientation(LinearLayout.VERTICAL);
            LinearLayout top = row(); top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(coinBadge(t.symbol), new LinearLayout.LayoutParams(dp(48), dp(48)));
            LinearLayout info = col(); info.addView(tv(t.symbol + "   " + t.side, 17, WHITE, true)); info.addView(tv("Isolated • " + t.leverage + "x", 12, MUTED, false)); top.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout pl = col(); pl.setGravity(Gravity.RIGHT); pl.addView(tv("P&L", 11, MUTED, true)); pl.addView(tv(formatMoney(t.pnl) + " USDT", 15, t.pnl >= 0 ? GREEN : RED, true)); pl.addView(tv(percent(t.pct), 13, t.pnl >= 0 ? GREEN : RED, true)); top.addView(pl); item.addView(top);
            item.addView(tv(String.format(Locale.US, "Entry %.2f   Mark %.2f   TP/SL %.2f / %.2f", t.entry, t.mark, t.tp, t.sl), 12, MUTED, false));
            if (showReason) item.addView(tv(t.reason, 12, MUTED, false));
            box.addView(item); addGap(box, 10);
        }
        root.addView(box); addGap(12);
    }

    void addBottomPanels() {
        LinearLayout row = row(); row.setBaselineAligned(false);
        LinearLayout api = cardBox(); api.addView(section("API & SECURITY")); api.addView(tv("Mode: " + store.mode.toUpperCase(Locale.US), 13, MUTED, false)); api.addView(tv("API Key: " + (store.apiKey.isEmpty()?"Not set":"Saved"), 13, MUTED, false)); api.addView(tv("Live Lock: " + (store.liveUnlocked?"Unlocked":"Enabled"), 13, store.liveUnlocked?AMBER:GREEN, false)); row.addView(api, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams jp = new LinearLayout.LayoutParams(0, -2, 1); jp.leftMargin=dp(10); LinearLayout j = cardBox(); j.addView(section("RECENT EVENTS")); for (int i=0;i<Math.min(4,store.engine.journal.size());i++) j.addView(tv(store.engine.journal.get(i), 12, MUTED, false)); row.addView(j, jp);
        root.addView(row);
    }

    void startBot() {
        if ("live".equals(store.mode) && !store.liveUnlocked) { toast("Live locked. Open Security checklist first."); activeTab=4; render(true); return; }
        store.engine.start();
        try { Intent i = new Intent(this, NanuBotService.class); if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); } catch (Exception ignored) {}
        toast("Nanu started"); render(false);
    }
    void stopBot() { store.engine.stop(); stopService(new Intent(this, NanuBotService.class)); toast("Nanu stopped"); render(false); }
    void panic() { store.engine.panicClose(); stopService(new Intent(this, NanuBotService.class)); toast("Panic close activated"); render(false); }

    void setMode(String m) {
        if ("live".equals(m)) { unlockLive(); return; }
        store.mode = m; store.liveUnlocked = false; store.save(); toast("Mode: " + m.toUpperCase(Locale.US)); render(true);
    }
    void unlockLive() {
        if (store.apiKey.isEmpty() || store.apiSecret.isEmpty()) { toast("Add Binance API key and secret first."); return; }
        input("Unlock Live Mode", "Type UNLOCK LIVE", "", false, s -> { if ("UNLOCK LIVE".equals(s.trim())) { store.liveUnlocked=true; store.mode="live"; store.save(); toast("Live unlocked"); } else toast("Live not unlocked"); render(true); });
    }
    void testApi() { alert("Binance API", "Testing API health. Please wait..."); BinanceClient.testApi(store, result -> runOnUiThread(() -> alert("Binance API Health Check", result))); }

    String developerReport() { return "Nanu AI Trading Bot v5\nMode: " + store.mode + "\nRunning: " + store.engine.running + "\nPanic: " + store.engine.panic + "\nCoin mode: " + (store.autoCoinMode?"Auto":"Manual") + "\nWatchlist: " + store.watchlist + "\nOpen trades: " + store.engine.trades.size() + "\nLive unlocked: " + store.liveUnlocked; }

    TextView tv(String s, int sp, int color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setIncludeFontPadding(true); t.setLineSpacing(dp(2), 1.0f); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    TextView label(String s) { return tv(s, 12, MUTED, true); }
    TextView section(String s) { return tv(s, 17, WHITE, true); }
    TextView screenTitle(String s) { return tv(s, 24, WHITE, true); }
    TextView big(String s, int color, int sp) { return tv(s, sp, color, true); }
    LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    LinearLayout col() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    void addGap(int h) { SpaceView sp = new SpaceView(this); root.addView(sp, new LinearLayout.LayoutParams(1, dp(h))); }
    void addGap(LinearLayout parent, int h) { SpaceView sp = new SpaceView(this); parent.addView(sp, new LinearLayout.LayoutParams(1, dp(h))); }

    LinearLayout cardBox() { LinearLayout l = col(); l.setPadding(dp(16), dp(14), dp(16), dp(14)); l.setBackground(bg(CARD, Color.rgb(16, 116, 139), 18)); return l; }
    LinearLayout cardMini() { LinearLayout l = row(); l.setPadding(dp(12), dp(10), dp(12), dp(10)); l.setBackground(bg(CARD2, Color.rgb(18, 86, 108), 16)); return l; }
    GradientDrawable bg(int fill, int stroke, int rad) { GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(rad)); g.setStroke(dp(1.2f), stroke); return g; }

    TextView pill(String s, int color, int sp) { TextView t = tv(s, sp, color, true); t.setGravity(Gravity.CENTER); t.setPadding(dp(10), dp(7), dp(10), dp(7)); t.setBackground(bg(Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)), color, 14)); return t; }
    Button actionButton(String s, int color, View.OnClickListener l) { Button b = new Button(this); b.setText(s); b.setTextSize(15); b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setTextColor(color == GREEN ? BG : WHITE); b.setBackground(bg(color == GREEN ? GREEN : (color == RED ? Color.rgb(104, 26, 34) : CARD2), color, 16)); b.setOnClickListener(l); return b; }
    TextView modeButton(String title, String sub, String mode) { boolean selected = mode.equals(store.mode); TextView t = tv(title + "\n" + sub, 15, selected ? BG : WHITE, true); t.setGravity(Gravity.CENTER); t.setTextAlignment(View.TEXT_ALIGNMENT_CENTER); t.setBackground(bg(selected ? GREEN : CARD2, selected ? GREEN : CYAN, 18)); t.setOnClickListener(v -> setMode(mode)); return t; }

    void addMetric(LinearLayout parent, String title, String v, String sub, int color) { LinearLayout m = cardBox(); m.addView(label(title)); m.addView(tv(v, 19, color, true)); m.addView(tv(sub, 13, MUTED, true)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(dp(3), 0, dp(3), 0); parent.addView(m, lp); }
    TextView coinBadge(String symbol) { String c = symbol.length() >= 3 ? symbol.substring(0, Math.min(3, symbol.length())) : symbol; TextView t = tv(c, 15, Color.WHITE, true); t.setGravity(Gravity.CENTER); int fill = symbol.startsWith("BTC") ? Color.rgb(247,147,26) : symbol.startsWith("ETH") ? Color.rgb(86,112,255) : symbol.startsWith("SOL") ? Color.rgb(126,70,255) : symbol.startsWith("BNB") ? Color.rgb(240,190,20) : CYAN; t.setBackground(bg(fill, fill, 100)); return t; }

    int pnlColor() { return store.engine.todayPnl < -15 ? RED : GREEN; }
    String formatMoney(double v) { return String.format(Locale.US, "%+.2f", v); }
    String percent(double v) { return String.format(Locale.US, "%+.2f%%", v); }
    void input(String title, String hint, String old, boolean secret, InputCb cb) { EditText e = new EditText(this); e.setHint(hint); e.setText(old); e.setSingleLine(false); e.setInputType(secret ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD) : InputType.TYPE_CLASS_TEXT); new AlertDialog.Builder(this).setTitle(title).setView(e).setNegativeButton("Cancel", null).setPositiveButton("Save", (d,w) -> cb.ok(e.getText().toString())).show(); }
    interface InputCb { void ok(String s); }
    void alert(String title, String msg) { new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show(); }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    void footer() { addGap(14); TextView f = tv("Nanu AI Trading Bot v5 • Paper first • Live locked • Panic always available", 11, MUTED, false); f.setGravity(Gravity.CENTER); root.addView(f); }
    public static class SpaceView extends View { public SpaceView(android.content.Context c) { super(c); } }
}
