package com.nanu.scalpingbridge;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String BASE_URL = "http://127.0.0.1:8765";

    private final int BG = Color.rgb(3, 9, 14);
    private final int PANEL = Color.rgb(8, 23, 34);
    private final int PANEL2 = Color.rgb(12, 35, 50);
    private final int LINE = Color.rgb(22, 62, 78);
    private final int TEXT = Color.rgb(235, 255, 255);
    private final int MUTED = Color.rgb(141, 169, 180);
    private final int CYAN = Color.rgb(0, 229, 255);
    private final int GREEN = Color.rgb(61, 255, 145);
    private final int RED = Color.rgb(255, 95, 115);
    private final int AMBER = Color.rgb(255, 209, 102);

    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout tabBar;
    private TextView statusPill;
    private TextView subtitle;
    private TextView lastUpdate;
    private String activeTab = "Bridge";
    private JSONObject status = new JSONObject();
    private JSONObject config = new JSONObject();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<Double> pnlLine = new ArrayList<>();

    private final Runnable poller = new Runnable() {
        @Override public void run() {
            refreshAll(false);
            handler.postDelayed(this, 5000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildShell();
        renderActiveTab();
        refreshAll(true);
        handler.postDelayed(poller, 5000);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(14), dp(14), dp(10));
        header.setBackground(panelGradient());
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView logo = text("N", 20, Color.rgb(2, 18, 24), true);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setBackground(circleGradient());
        LinearLayout.LayoutParams lpLogo = new LinearLayout.LayoutParams(dp(50), dp(50));
        header.addView(logo, lpLogo);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(12), 0, dp(8), 0);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));

        TextView title = text("Nanu AI Trading Bot", 20, TEXT, true);
        titleBox.addView(title);
        subtitle = text("Binance scalping cockpit • paper first • local engine bridge", 12, MUTED, false);
        titleBox.addView(subtitle);

        statusPill = text("OFFLINE", 12, AMBER, true);
        statusPill.setGravity(Gravity.CENTER);
        statusPill.setPadding(dp(10), dp(8), dp(10), dp(8));
        statusPill.setBackground(roundBg(Color.rgb(18, 31, 37), AMBER, dp(20)));
        header.addView(statusPill, new LinearLayout.LayoutParams(-2, -2));

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(dp(10), dp(6), dp(10), dp(8));
        hsv.addView(tabBar);
        root.addView(hsv, new LinearLayout.LayoutParams(-1, -2));

        for (String tab : new String[]{"Bridge", "Scanner", "Brain", "Journal", "Security"}) {
            Button b = tabButton(tab);
            tabBar.addView(b);
        }

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(20));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private Button tabButton(final String name) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(name);
        b.setTextSize(13);
        b.setTextColor(TEXT);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setMinHeight(dp(42));
        b.setBackground(roundBg(name.equals(activeTab) ? Color.rgb(10, 76, 88) : Color.rgb(8, 28, 39), LINE, dp(16)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(42));
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> {
            activeTab = name;
            repaintTabs();
            renderActiveTab();
        });
        return b;
    }

    private void repaintTabs() {
        for (int i = 0; i < tabBar.getChildCount(); i++) {
            View v = tabBar.getChildAt(i);
            if (v instanceof Button) {
                Button b = (Button) v;
                boolean selected = b.getText().toString().equals(activeTab);
                b.setBackground(roundBg(selected ? Color.rgb(10, 76, 88) : Color.rgb(8, 28, 39), selected ? CYAN : LINE, dp(16)));
            }
        }
    }

    private void renderActiveTab() {
        content.removeAllViews();
        if ("Bridge".equals(activeTab)) renderBridge();
        else if ("Scanner".equals(activeTab)) renderScanner();
        else if ("Brain".equals(activeTab)) renderBrain();
        else if ("Journal".equals(activeTab)) renderJournal();
        else renderSecurity();
    }

    private void renderBridge() {
        content.addView(cardTitle("Bridge Control", "Start/stop Nanu, watch paper trades, and keep the panic door close."));
        LinearLayout controls = row();
        controls.addView(actionButton("START", GREEN, () -> control("start")), new LinearLayout.LayoutParams(0, dp(48), 1));
        controls.addView(actionButton("STOP", AMBER, () -> control("stop")), new LinearLayout.LayoutParams(0, dp(48), 1));
        controls.addView(actionButton("PANIC", RED, () -> control("panic")), new LinearLayout.LayoutParams(0, dp(48), 1));
        content.addView(wrapCard(controls));

        LinearLayout metrics1 = row();
        metrics1.addView(metric("Status", isEnabled() ? "RUNNING" : "STOPPED", isEnabled() ? GREEN : AMBER), new LinearLayout.LayoutParams(0, -2, 1));
        metrics1.addView(metric("Mode", opt(status, "mode", "paper"), "paper".equals(opt(status, "mode", "paper")) ? AMBER : CYAN), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(metrics1);

        LinearLayout metrics2 = row();
        metrics2.addView(metric("Open Trades", String.valueOf(arr(status, "open_trades").length()), CYAN), new LinearLayout.LayoutParams(0, -2, 1));
        metrics2.addView(metric("Daily PnL", money(status.optDouble("daily_pnl", 0)), status.optDouble("daily_pnl", 0) >= 0 ? GREEN : RED), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(metrics2);

        SparkView spark = new SparkView(this);
        spark.setValues(pnlLine);
        content.addView(wrapCardWithTitle("Equity Pulse", spark, "Paper PnL heartbeat from recent status refreshes."));

        content.addView(section("Open Trades"));
        addArrayCards(arr(status, "open_trades"), "No open paper trades yet.", true);

        lastUpdate = text("Last update: " + now(), 11, MUTED, false);
        content.addView(lastUpdate);
    }

    private void renderScanner() {
        content.addView(cardTitle("Scanner", "BTC, ETH, SOL, BNB watchlist with Nanu's recent EMA/RSI/MACD decisions."));
        content.addView(wrapCard(chips(symbolLine())));
        JSONObject last = status.optJSONObject("last_signal");
        if (last != null) {
            LinearLayout signal = new LinearLayout(this);
            signal.setOrientation(LinearLayout.VERTICAL);
            signal.addView(text(last.optString("symbol", "-") + "  •  " + last.optString("action", "WAIT"), 24, actionColor(last.optString("action")), true));
            signal.addView(text("Confidence: " + last.optInt("confidence", 0) + "/100", 15, TEXT, true));
            signal.addView(progress(last.optInt("confidence", 0), actionColor(last.optString("action"))));
            signal.addView(text(last.optString("reasons", "No reason stored yet."), 13, MUTED, false));
            content.addView(wrapCardWithTitle("Last Signal", signal, "The latest thought from the strategy brain."));
        }
        content.addView(section("Recent Signals"));
        addSignalCards(arr(status, "recent_signals"));
    }

    private void renderBrain() {
        content.addView(cardTitle("Nanu Brain", "Scalping only. Fast entries, strict exits, and patient paper learning."));
        LinearLayout grid1 = row();
        grid1.addView(metric("Strategy", "EMA + RSI + MACD", CYAN), new LinearLayout.LayoutParams(0, -2, 1));
        grid1.addView(metric("Style", "Scalping Only", GREEN), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(grid1);
        LinearLayout grid2 = row();
        grid2.addView(metric("Risk Door", "SL / TP / Trail", AMBER), new LinearLayout.LayoutParams(0, -2, 1));
        grid2.addView(metric("Learning", "Journal Memory", CYAN), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(grid2);

        LinearLayout thoughts = new LinearLayout(this);
        thoughts.setOrientation(LinearLayout.VERTICAL);
        thoughts.addView(text("Current Thought", 16, TEXT, true));
        JSONObject last = status.optJSONObject("last_signal");
        if (last == null) {
            thoughts.addView(text("No strategy thought yet. Start Nanu and let it scan candles.", 13, MUTED, false));
        } else {
            thoughts.addView(text(last.optString("symbol") + " → " + last.optString("action"), 20, actionColor(last.optString("action")), true));
            thoughts.addView(text("Reason: " + last.optString("reasons", "-"), 13, MUTED, false));
        }
        thoughts.addView(text("Guard note: live trading stays locked unless config explicitly enables it. Paper first, demo next, live last.", 13, AMBER, false));
        content.addView(wrapCard(thoughts));

        content.addView(section("Brain Rules Loaded"));
        content.addView(rule("EMA trend filter", "Nanu avoids blind entries when short/long EMA disagreement is weak."));
        content.addView(rule("RSI pulse", "Avoids overbought chase and watches for scalping momentum zones."));
        content.addView(rule("MACD confirmation", "Uses momentum confirmation before BUY decisions."));
        content.addView(rule("Exit shield", "Stop-loss, take-profit, trailing-stop, max hold time, and daily loss guard."));
    }

    private void renderJournal() {
        content.addView(cardTitle("Journal", "Every signal, open trade, close trade, and warning is written like a ship log."));
        content.addView(section("Recent Trades"));
        addArrayCards(arr(status, "recent_trades"), "No trades recorded yet.", false);
        content.addView(section("Recent Events"));
        JSONArray events = arr(status, "recent_events");
        if (events.length() == 0) content.addView(empty("No engine events yet."));
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.addView(text(e.optString("level", "INFO") + "  •  " + time(e.optDouble("ts", 0)), 12, levelColor(e.optString("level")), true));
            c.addView(text(e.optString("message", "-"), 13, TEXT, false));
            content.addView(wrapCard(c));
        }
    }

    private void renderSecurity() {
        content.addView(cardTitle("Settings / Security", "Change API keys, Telegram keys, mode, symbols, and risk. Empty secret boxes preserve old values."));
        content.addView(wrapCard(text("For safety, restart Termux command `python main.py run` after changing exchange mode, keys, symbols, or risk values.", 13, AMBER, false)));

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        final EditText mode = input("exchange.mode", configValue("exchange", "mode", "paper"));
        final EditText live = input("exchange.live_trading_enabled", configValue("exchange", "live_trading_enabled", "false"));
        final EditText apiKey = input("exchange.api_key", "");
        apiKey.setHint("Paste Binance API key only when changing");
        final EditText apiSecret = input("exchange.api_secret", "");
        apiSecret.setHint("Paste Binance API secret only when changing");
        final EditText symbols = input("strategy.symbols", configValue("strategy", "symbols", symbolLine()));
        final EditText quote = input("risk.quote_per_trade", configValue("risk", "quote_per_trade", "10"));
        final EditText maxOpen = input("risk.max_open_trades", configValue("risk", "max_open_trades", "2"));
        final EditText sl = input("risk.stop_loss_pct", configValue("risk", "stop_loss_pct", "0.40"));
        final EditText tp = input("risk.take_profit_pct", configValue("risk", "take_profit_pct", "0.65"));
        final EditText trail = input("risk.trailing_stop_pct", configValue("risk", "trailing_stop_pct", "0.30"));
        final EditText tgEnabled = input("telegram.enabled", configValue("telegram", "enabled", "false"));
        final EditText tgToken = input("telegram.bot_token", "");
        tgToken.setHint("Paste Telegram token only when changing");
        final EditText tgChat = input("telegram.chat_id", configValue("telegram", "chat_id", ""));
        EditText[] fields = {mode, live, apiKey, apiSecret, symbols, quote, maxOpen, sl, tp, trail, tgEnabled, tgToken, tgChat};
        for (EditText f : fields) form.addView(f);

        Button save = actionButton("SAVE SETTINGS", CYAN, () -> {
            try {
                JSONObject body = new JSONObject();
                for (EditText f : fields) body.put(String.valueOf(f.getTag()), f.getText().toString());
                post("/api/config", body, () -> {
                    toast("Settings saved. Restart engine for full reload.");
                    refreshAll(true);
                });
            } catch (Exception e) { toast("Save failed: " + e.getMessage()); }
        });
        form.addView(save, new LinearLayout.LayoutParams(-1, dp(50)));

        Button web = actionButton("OPEN WEB DASHBOARD", AMBER, () -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BASE_URL))); }
            catch (Exception e) { toast("Open browser failed"); }
        });
        form.addView(web, new LinearLayout.LayoutParams(-1, dp(50)));
        content.addView(wrapCard(form));
    }

    private void refreshAll(boolean toastOnFail) {
        executor.execute(() -> {
            try {
                final JSONObject s = new JSONObject(request("GET", "/api/status", null));
                final JSONObject c = new JSONObject(request("GET", "/api/config", null));
                runOnUiThread(() -> {
                    status = s;
                    config = c;
                    double pnl = status.optDouble("daily_pnl", 0);
                    pnlLine.add(pnl);
                    while (pnlLine.size() > 30) pnlLine.remove(0);
                    updateHeader();
                    renderActiveTab();
                });
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    statusPill.setText("ENGINE OFFLINE");
                    statusPill.setTextColor(RED);
                    statusPill.setBackground(roundBg(Color.rgb(46, 13, 20), RED, dp(20)));
                    subtitle.setText("Start Termux engine: python main.py run");
                    if (toastOnFail) toast("Nanu engine offline. Run: python main.py run");
                });
            }
        });
    }

    private void control(String action) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", action);
            post("/api/control", body, () -> {
                toast("Nanu command: " + action);
                refreshAll(false);
            });
        } catch (Exception e) { toast("Command failed: " + e.getMessage()); }
    }

    private void post(final String path, final JSONObject body, final Runnable onOk) {
        executor.execute(() -> {
            try {
                request("POST", path, body.toString());
                runOnUiThread(onOk);
            } catch (final Exception e) {
                runOnUiThread(() -> toast("Request failed: " + e.getMessage()));
            }
        });
    }

    private String request(String method, String path, String body) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod(method);
        con.setConnectTimeout(2500);
        con.setReadTimeout(3500);
        con.setRequestProperty("Accept", "application/json");
        if (body != null) {
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            con.setFixedLengthStreamingMode(bytes.length);
            OutputStream os = con.getOutputStream();
            os.write(bytes);
            os.flush();
            os.close();
        }
        int code = con.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + " " + sb);
        return sb.toString();
    }

    private void updateHeader() {
        boolean run = isEnabled();
        String mode = opt(status, "mode", "paper");
        statusPill.setText((run ? "RUNNING" : "STOPPED") + " • " + mode.toUpperCase(Locale.US));
        statusPill.setTextColor(run ? GREEN : AMBER);
        statusPill.setBackground(roundBg(run ? Color.rgb(5, 45, 34) : Color.rgb(43, 33, 12), run ? GREEN : AMBER, dp(20)));
        subtitle.setText("Symbols: " + symbolLine() + " • Open: " + arr(status, "open_trades").length() + " • PnL: " + money(status.optDouble("daily_pnl", 0)));
    }

    private boolean isEnabled() { return status.optBoolean("enabled", false); }
    private String symbolLine() {
        JSONArray a = arr(status, "symbols");
        if (a.length() == 0) return "BTCUSDT, ETHUSDT, SOLUSDT, BNBUSDT";
        ArrayList<String> parts = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) parts.add(a.optString(i));
        return join(parts, ", ");
    }

    private void addArrayCards(JSONArray a, String emptyText, boolean open) {
        if (a.length() == 0) { content.addView(empty(emptyText)); return; }
        for (int i = 0; i < a.length(); i++) {
            JSONObject t = a.optJSONObject(i);
            if (t == null) continue;
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            String title = t.optString("symbol", "-") + "  •  " + t.optString("status", open ? "OPEN" : "-");
            c.addView(text(title, 17, open ? CYAN : TEXT, true));
            c.addView(text("Qty " + dec(t.optDouble("qty", 0), 8) + "  Entry " + dec(t.optDouble("entry_price", 0), 6), 13, MUTED, false));
            if (open) {
                c.addView(text("SL " + dec(t.optDouble("stop_loss", 0), 6) + "  TP " + dec(t.optDouble("take_profit", 0), 6), 13, AMBER, false));
                c.addView(text("Opened: " + time(t.optDouble("entry_time", 0)), 12, MUTED, false));
            } else {
                double pnl = t.optDouble("pnl_quote", 0);
                c.addView(text("Exit " + dec(t.optDouble("exit_price", 0), 6) + "  PnL " + money(pnl), 13, pnl >= 0 ? GREEN : RED, true));
                c.addView(text("Reason: " + t.optString("reason", "-"), 12, MUTED, false));
            }
            content.addView(wrapCard(c));
        }
    }

    private void addSignalCards(JSONArray a) {
        if (a.length() == 0) { content.addView(empty("No recent signals yet.")); return; }
        for (int i = 0; i < a.length(); i++) {
            JSONObject s = a.optJSONObject(i);
            if (s == null) continue;
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            String action = s.optString("action", "WAIT");
            c.addView(text(s.optString("symbol", "-") + "  •  " + action + "  •  " + s.optInt("confidence", 0) + "/100", 16, actionColor(action), true));
            c.addView(text("Price " + dec(s.optDouble("price", 0), 6) + "  •  " + time(s.optDouble("ts", 0)), 12, MUTED, false));
            c.addView(text(s.optString("reasons", "-"), 12, MUTED, false));
            content.addView(wrapCard(c));
        }
    }

    private LinearLayout rule(String title, String body) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.addView(text(title, 15, CYAN, true));
        c.addView(text(body, 12, MUTED, false));
        return wrapCard(c);
    }

    private LinearLayout cardTitle(String title, String sub) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.addView(text(title, 22, TEXT, true));
        c.addView(text(sub, 13, MUTED, false));
        return wrapCard(c);
    }

    private TextView section(String s) {
        TextView t = text(s, 17, TEXT, true);
        t.setPadding(dp(4), dp(12), dp(4), dp(4));
        return t;
    }

    private LinearLayout wrapCard(View child) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(13), dp(13), dp(13), dp(13));
        box.setBackground(roundBg(PANEL, LINE, dp(18)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(8));
        box.setLayoutParams(lp);
        box.addView(child);
        return box;
    }

    private LinearLayout wrapCardWithTitle(String title, View child, String sub) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(text(title, 16, TEXT, true));
        if (sub != null && sub.length() > 0) box.addView(text(sub, 12, MUTED, false));
        if (child instanceof SparkView) {
            box.addView(child, new LinearLayout.LayoutParams(-1, dp(80)));
        } else {
            box.addView(child, new LinearLayout.LayoutParams(-1, -2));
        }
        return wrapCard(box);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, 0, 0, 0);
        return r;
    }

    private LinearLayout metric(String label, String value, int color) {
        LinearLayout m = new LinearLayout(this);
        m.setOrientation(LinearLayout.VERTICAL);
        m.setPadding(dp(12), dp(12), dp(12), dp(12));
        m.setBackground(roundBg(PANEL2, LINE, dp(16)));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.setMargins(dp(4), dp(4), dp(4), dp(8));
        m.setLayoutParams(mlp);
        m.addView(text(value, 20, color, true));
        m.addView(text(label, 11, MUTED, false));
        return m;
    }

    private LinearLayout chips(String csv) {
        LinearLayout out = new LinearLayout(this);
        out.setOrientation(LinearLayout.VERTICAL);
        String[] parts = csv.split(",");
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        for (String p : parts) {
            TextView chip = text(p.trim(), 13, CYAN, true);
            chip.setPadding(dp(10), dp(8), dp(10), dp(8));
            chip.setBackground(roundBg(Color.rgb(6, 31, 41), CYAN, dp(18)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            line.addView(chip, lp);
        }
        out.addView(line);
        return out;
    }

    private TextView empty(String s) {
        TextView t = text(s, 13, MUTED, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(18), dp(10), dp(18));
        t.setBackground(roundBg(PANEL, LINE, dp(14)));
        return t;
    }

    private View progress(int pct, int color) {
        LinearLayout outer = new LinearLayout(this);
        outer.setBackground(roundBg(Color.rgb(3, 15, 22), LINE, dp(10)));
        outer.setPadding(0, 0, 0, 0);
        LinearLayout inner = new LinearLayout(this);
        inner.setBackground(roundBg(color, color, dp(10)));
        int width = Math.max(4, Math.min(100, pct));
        outer.addView(inner, new LinearLayout.LayoutParams(0, dp(10), width));
        TextView filler = new TextView(this);
        outer.addView(filler, new LinearLayout.LayoutParams(0, dp(10), 100 - width));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(10));
        lp.setMargins(0, dp(8), 0, dp(10));
        outer.setLayoutParams(lp);
        return outer;
    }

    private EditText input(String tag, String value) {
        EditText e = new EditText(this);
        e.setTag(tag);
        e.setText(value == null ? "" : value);
        e.setHint(tag);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setTextSize(14);
        e.setSingleLine(true);
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(roundBg(Color.rgb(4, 18, 27), LINE, dp(12)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.setMargins(0, dp(6), 0, dp(6));
        e.setLayoutParams(lp);
        return e;
    }

    private Button actionButton(String label, int color, final Runnable run) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.rgb(2, 16, 20));
        b.setBackground(roundBg(color, color, dp(14)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> run.run());
        return b;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.12f);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable roundBg(int fill, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        g.setStroke(dp(1), stroke);
        return g;
    }

    private GradientDrawable panelGradient() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.rgb(4, 18, 27), Color.rgb(8, 31, 43)});
        g.setStroke(dp(1), LINE);
        return g;
    }

    private GradientDrawable circleGradient() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{CYAN, GREEN});
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(18));
        return g;
    }

    private int actionColor(String action) {
        if ("BUY".equalsIgnoreCase(action)) return GREEN;
        if ("SELL".equalsIgnoreCase(action)) return RED;
        return AMBER;
    }

    private int levelColor(String level) {
        if ("ERROR".equalsIgnoreCase(level)) return RED;
        if ("WARN".equalsIgnoreCase(level)) return AMBER;
        if ("TRADE".equalsIgnoreCase(level)) return GREEN;
        return CYAN;
    }

    private JSONArray arr(JSONObject o, String k) { return o.optJSONArray(k) == null ? new JSONArray() : o.optJSONArray(k); }
    private String opt(JSONObject o, String k, String f) { return o == null ? f : o.optString(k, f); }

    private String configValue(String section, String key, String fallback) {
        JSONObject sec = config.optJSONObject(section);
        return sec == null ? fallback : sec.optString(key, fallback);
    }

    private String money(double x) { return String.format(Locale.US, "%.4f", x); }
    private String dec(double x, int places) { return String.format(Locale.US, "%." + places + "f", x); }
    private String join(ArrayList<String> a, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.size(); i++) { if (i > 0) sb.append(sep); sb.append(a.get(i)); }
        return sb.toString();
    }
    private String now() { return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()); }
    private String time(double ts) {
        if (ts <= 0) return "-";
        return new SimpleDateFormat("dd MMM HH:mm", Locale.US).format(new Date((long) (ts * 1000)));
    }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    public class SparkView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private ArrayList<Double> values = new ArrayList<>();
        public SparkView(Activity ctx) { super(ctx); setMinimumHeight(dp(80)); }
        public void setValues(ArrayList<Double> v) { values = new ArrayList<>(v); invalidate(); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w = getWidth(); int h = getHeight();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(5, 18, 27));
            c.drawRoundRect(0, 0, w, h, dp(12), dp(12), paint);
            if (values.size() < 2) {
                paint.setColor(MUTED); paint.setTextSize(dp(13)); c.drawText("Waiting for PnL samples...", dp(12), h/2, paint); return;
            }
            double min = values.get(0), max = values.get(0);
            for (double v : values) { if (v < min) min = v; if (v > max) max = v; }
            if (Math.abs(max - min) < 0.0001) { max += 1; min -= 1; }
            path.reset();
            for (int i = 0; i < values.size(); i++) {
                float x = (float) i / (values.size() - 1) * (w - dp(20)) + dp(10);
                float y = (float) (h - dp(10) - ((values.get(i) - min) / (max - min)) * (h - dp(20)));
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(CYAN);
            c.drawPath(path, paint);
        }
    }
}
