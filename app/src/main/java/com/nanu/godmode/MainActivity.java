package com.nanu.godmode;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private AppConfig cfg;
    private JournalDb db;
    private LinearLayout root;
    private LinearLayout content;
    private TextView statusLine;
    private final Handler handler = new Handler();
    private String tab = "Bridge";

    private final int BG = Color.rgb(7,17,31);
    private final int CARD = Color.rgb(13,31,52);
    private final int CYAN = Color.rgb(0,229,255);
    private final int GOLD = Color.rgb(255,198,74);
    private final int WHITE = Color.rgb(236,246,255);
    private final int MUTED = Color.rgb(145,170,190);
    private final int RED = Color.rgb(255,83,112);
    private final int GREEN = Color.rgb(78,255,164);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = new AppConfig(this);
        db = new JournalDb(this);
        askNotifications();
        build();
        showBridge();
        handler.postDelayed(refreshLoop, 1500);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(refreshLoop);
    }

    private final Runnable refreshLoop = new Runnable() {
        @Override public void run() {
            refreshHeader();
            if ("Bridge".equals(tab)) showBridge();
            handler.postDelayed(this, 4000);
        }
    };

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        }
    }

    private void build() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(8));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView title = txt("⚡ NANU GOD MODE", 24, CYAN, true);
        header.addView(title);
        TextView sub = txt("All-in-one APK Safe Edition • Paper first • Scalping only", 13, MUTED, false);
        header.addView(sub);
        statusLine = txt("Loading...", 14, WHITE, true);
        statusLine.setPadding(0, dp(8),0,0);
        header.addView(statusLine);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(dp(12), dp(6), dp(12), dp(6));
        root.addView(controls);
        controls.addView(btn("START", GREEN, v -> service(NanuBotService.ACTION_START)), new LinearLayout.LayoutParams(0, dp(44), 1));
        controls.addView(btn("STOP", GOLD, v -> service(NanuBotService.ACTION_STOP)), new LinearLayout.LayoutParams(0, dp(44), 1));
        controls.addView(btn("PANIC", RED, v -> service(NanuBotService.ACTION_PANIC)), new LinearLayout.LayoutParams(0, dp(44), 1));

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(8), dp(4), dp(8), dp(4));
        hsv.addView(nav);
        root.addView(hsv, new LinearLayout.LayoutParams(-1, -2));
        String[] tabs = {"Bridge","Scanner","Brain","Journal","Security"};
        for (String t : tabs) nav.addView(btn(t, CYAN, v -> { tab = ((Button)v).getText().toString(); renderTab(); }), new LinearLayout.LayoutParams(dp(118), dp(42)));

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(22));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        refreshHeader();
    }

    private void renderTab() {
        if ("Bridge".equals(tab)) showBridge();
        else if ("Scanner".equals(tab)) showScanner();
        else if ("Brain".equals(tab)) showBrain();
        else if ("Journal".equals(tab)) showJournal();
        else showSecurity();
    }

    private void refreshHeader() {
        String s = cfg.status();
        int c = "RUNNING".equals(s) ? GREEN : "PANIC".equals(s) ? RED : GOLD;
        statusLine.setTextColor(c);
        statusLine.setText("Status: " + s + "  •  Mode: " + cfg.mode().toUpperCase(Locale.US) + "  •  Open: " + db.openCount() + "  •  PnL: " + fmt(db.dailyPnl()));
    }

    private void showBridge() {
        tab = "Bridge"; content.removeAllViews(); refreshHeader();
        content.addView(card("Bridge Dashboard", "Nanu is now running inside the APK. No Termux engine is required. Keep paper mode until you have tested signals, risk, logs, and exchange behavior."));
        content.addView(metricRow("Bot", cfg.status(), "Mode", cfg.mode().toUpperCase(Locale.US), "Panic", String.valueOf(cfg.panic())));
        content.addView(metricRow("Open trades", String.valueOf(db.openCount()), "Daily PnL", fmt(db.dailyPnl()), "Max open", String.valueOf(cfg.maxOpenTrades())));
        content.addView(card("Last Signal", cfg.lastSignal()));
        content.addView(card("Open / Recent Trades", db.recentTradesText(6)));
        content.addView(card("Recent Events", db.recentEventsText(5)));
    }

    private void showScanner() {
        tab = "Scanner"; content.removeAllViews(); refreshHeader();
        content.addView(card("Scanner", "Symbols: " + join(cfg.symbols()) + "\nInterval: 1m scalping loop every " + cfg.intervalSeconds() + " seconds.\nStrategy: EMA9/EMA21 + RSI + MACD trend filter."));
        StringBuilder sb = new StringBuilder();
        for (String s : cfg.symbols()) sb.append(s).append(" price≈ ").append(fmt(cfg.getLastPrice(s))).append("\n");
        content.addView(card("Market Pulse", sb.toString()));
        content.addView(card("Signal Rule", "BUY only when trend is clean: EMA9 above EMA21, RSI healthy, MACD positive, green candle confirmation. Otherwise Nanu waits."));
    }

    private void showBrain() {
        tab = "Brain"; content.removeAllViews(); refreshHeader();
        content.addView(card("Nanu Brain", "Safe God Mode means discipline, not guaranteed profit. Nanu watches trend, momentum, risk, max hold time, and daily loss guard."));
        content.addView(card("Learning Memory v1", "The current APK records trades, events, reasons, PnL, and signal notes in SQLite. Next upgrade can add model scoring from this journal."));
        content.addView(card("Current Thought", cfg.lastSignal()));
    }

    private void showJournal() {
        tab = "Journal"; content.removeAllViews(); refreshHeader();
        content.addView(card("Trade Journal", db.recentTradesText(25)));
        content.addView(card("Event Log", db.recentEventsText(25)));
    }

    private void showSecurity() {
        tab = "Security"; content.removeAllViews(); refreshHeader();
        content.addView(card("Security Gate", "Live mode is locked by two switches. Use Binance API without withdrawal permission. Start with paper, then testnet/demo, then tiny live balance only after long testing."));

        Spinner mode = new Spinner(this);
        String[] modes = {"paper", "demo", "testnet", "live"};
        mode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes));
        for (int i=0;i<modes.length;i++) if (modes[i].equals(cfg.mode())) mode.setSelection(i);
        content.addView(label("Mode")); content.addView(mode);

        EditText symbols = edit("Symbols comma separated", cfg.prefs().getString("symbols", "BTCUSDT,ETHUSDT,SOLUSDT,BNBUSDT"), false);
        EditText api = edit("Binance API Key", cfg.apiKey(), false);
        EditText sec = edit("Binance API Secret", cfg.apiSecret(), true);
        EditText tg = edit("Telegram Bot Token", cfg.telegramToken(), false);
        EditText chat = edit("Telegram Chat ID", cfg.telegramChatId(), false);
        EditText trade = edit("Trade USDT", String.valueOf(cfg.tradeUsdt()), false);
        EditText sl = edit("Stop loss %", String.valueOf(cfg.stopLossPct()), false);
        EditText tp = edit("Take profit %", String.valueOf(cfg.takeProfitPct()), false);
        EditText trail = edit("Trailing stop %", String.valueOf(cfg.trailingPct()), false);
        EditText daily = edit("Daily loss limit USDT", String.valueOf(cfg.dailyLossLimit()), false);
        EditText max = edit("Max open trades", String.valueOf(cfg.maxOpenTrades()), false);
        EditText interval = edit("Loop seconds", String.valueOf(cfg.intervalSeconds()), false);
        EditText hold = edit("Max hold minutes", String.valueOf(cfg.maxHoldMinutes()), false);
        CheckBox liveUnlock = check("I understand live trading risk", cfg.liveUnlocked());
        CheckBox realOrders = check("Enable real Binance orders", cfg.realOrdersEnabled());

        addField("Symbols", symbols); addField("API Key", api); addField("API Secret", sec); addField("Telegram Token", tg); addField("Telegram Chat ID", chat);
        addField("Trade USDT", trade); addField("Stop Loss %", sl); addField("Take Profit %", tp); addField("Trailing %", trail); addField("Daily Loss Limit", daily); addField("Max Open", max); addField("Loop Seconds", interval); addField("Max Hold Minutes", hold);
        content.addView(liveUnlock); content.addView(realOrders);
        content.addView(btn("SAVE SETTINGS", CYAN, v -> {
            SharedPreferences.Editor e = cfg.prefs().edit();
            e.putString("mode", mode.getSelectedItem().toString());
            e.putString("symbols", symbols.getText().toString());
            e.putString("api_key", api.getText().toString());
            e.putString("api_secret", sec.getText().toString());
            e.putString("telegram_token", tg.getText().toString());
            e.putString("telegram_chat_id", chat.getText().toString());
            e.putFloat("trade_usdt", parseF(trade, 15));
            e.putFloat("stop_loss_pct", parseF(sl, 0.45f));
            e.putFloat("take_profit_pct", parseF(tp, 0.75f));
            e.putFloat("trailing_pct", parseF(trail, 0.30f));
            e.putFloat("daily_loss_limit", parseF(daily, 4));
            e.putInt("max_open_trades", parseI(max, 2));
            e.putInt("interval_seconds", parseI(interval, 20));
            e.putInt("max_hold_minutes", parseI(hold, 8));
            e.putBoolean("live_unlocked", liveUnlock.isChecked());
            e.putBoolean("real_orders_enabled", realOrders.isChecked());
            e.apply();
            db.event("CONFIG", "Settings saved from APK. Mode=" + cfg.mode());
            Toast.makeText(this, "Nanu settings saved", Toast.LENGTH_SHORT).show();
            refreshHeader();
        }), new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void service(String action) {
        Intent i = new Intent(this, NanuBotService.class);
        i.setAction(action);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        Toast.makeText(this, action.substring(action.lastIndexOf('.')+1), Toast.LENGTH_SHORT).show();
        handler.postDelayed(this::showBridge, 800);
    }

    private TextView card(String title, String body) {
        TextView v = txt(title + "\n" + body, 14, WHITE, false);
        v.setBackgroundColor(CARD);
        v.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8),0,dp(8));
        v.setLayoutParams(lp);
        return v;
    }

    private LinearLayout metricRow(String a, String av, String b, String bv, String c, String cv) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(metric(a, av), new LinearLayout.LayoutParams(0, dp(82), 1));
        row.addView(metric(b, bv), new LinearLayout.LayoutParams(0, dp(82), 1));
        row.addView(metric(c, cv), new LinearLayout.LayoutParams(0, dp(82), 1));
        return row;
    }

    private TextView metric(String k, String v) {
        TextView t = txt(k + "\n" + v, 13, WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(CARD);
        t.setPadding(dp(5),dp(5),dp(5),dp(5));
        return t;
    }

    private Button btn(String s, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s); b.setTextColor(Color.BLACK); b.setTextSize(12); b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackgroundColor(color); b.setOnClickListener(l); return b;
    }

    private TextView txt(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); t.setLineSpacing(4, 1.0f); return t;
    }
    private TextView label(String s) { TextView t = txt(s, 12, CYAN, true); t.setPadding(0, dp(10),0,dp(2)); return t; }
    private EditText edit(String hint, String val, boolean pass) { EditText e = new EditText(this); e.setHint(hint); e.setText(val); e.setTextColor(WHITE); e.setHintTextColor(MUTED); e.setSingleLine(true); e.setBackgroundColor(CARD); if (pass) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); return e; }
    private CheckBox check(String s, boolean val) { CheckBox c = new CheckBox(this); c.setText(s); c.setTextColor(WHITE); c.setChecked(val); return c; }
    private void addField(String name, EditText e) { content.addView(label(name)); content.addView(e, new LinearLayout.LayoutParams(-1, dp(48))); }
    private float parseF(EditText e, float d) { try { return Float.parseFloat(e.getText().toString().trim()); } catch(Exception x) { return d; } }
    private int parseI(EditText e, int d) { try { return Integer.parseInt(e.getText().toString().trim()); } catch(Exception x) { return d; } }
    private String fmt(double d) { return String.format(Locale.US, "%.4f", d); }
    private String join(String[] a) { StringBuilder sb = new StringBuilder(); for (String x:a) { if (sb.length()>0) sb.append(", "); sb.append(x); } return sb.toString(); }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
