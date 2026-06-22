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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/** DEX-only launch surface. Binance and Telegram controls are not part of this Activity. */
public final class DexActivity extends Activity {
    private static final int BG = Color.rgb(3, 11, 18);
    private static final int CARD = Color.rgb(7, 25, 37);
    private static final int CARD2 = Color.rgb(9, 34, 49);
    private static final int CYAN = Color.rgb(0, 221, 243);
    private static final int GREEN = Color.rgb(87, 246, 136);
    private static final int RED = Color.rgb(255, 82, 92);
    private static final int AMBER = Color.rgb(255, 192, 79);
    private static final int WHITE = Color.rgb(239, 247, 252);
    private static final int MUTED = Color.rgb(147, 166, 180);

    private DexAppStore store;
    private ScrollView scroll;
    private LinearLayout root;
    private int tab;
    private boolean refreshing;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!refreshing) return;
            if (store != null) { store.engine.tick(false); render(false); }
            handler.postDelayed(this, 4_000L);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        store = DexAppStore.get(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 71);
        }
        render(true);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!refreshing) { refreshing = true; handler.post(refresh); }
    }
    @Override protected void onPause() { refreshing = false; handler.removeCallbacks(refresh); super.onPause(); }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    private void render(boolean top) {
        int oldY = scroll == null ? 0 : scroll.getScrollY();
        if (scroll == null) {
            scroll = new ScrollView(this);
            scroll.setBackgroundColor(BG);
            scroll.setFillViewport(false);
            scroll.setVerticalScrollBarEnabled(false);
            root = col();
            root.setPadding(dp(14), dp(18), dp(14), dp(26));
            scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
            setContentView(scroll);
        } else root.removeAllViews();
        header();
        tabs();
        if (tab == 0) home();
        else if (tab == 1) discover();
        else if (tab == 2) position();
        else if (tab == 3) wallet();
        else control();
        footer();
        if (!top) scroll.post(() -> scroll.scrollTo(0, oldY));
    }

    private void header() {
        LinearLayout row = row(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, 0, 0, dp(10));
        LinearLayout titleBox = col();
        titleBox.addView(text("NANU AI TRADING BOT", 20, WHITE, true));
        LinearLayout stateRow = row(); stateRow.setGravity(Gravity.CENTER_VERTICAL);
        stateRow.addView(text("DEX AUTO", 11, CYAN, true), new LinearLayout.LayoutParams(0, -2, 1));
        String state = store.state();
        int color = "SCANNING".equals(state) || "POSITION OPEN".equals(state) ? GREEN : ("HALTED".equals(state) ? RED : MUTED);
        stateRow.addView(pill(state, color));
        titleBox.addView(stateRow);
        row.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chain = pill("solana".equals(store.activeChain) ? "SOL" : "BNB", CYAN);
        chain.setOnClickListener(v -> { store.activeChain = "solana".equals(store.activeChain) ? "bsc" : "solana"; store.save(); render(false); });
        row.addView(chain);
        root.addView(row);
    }

    private void tabs() {
        LinearLayout bar = row(); bar.setPadding(0, 0, 0, dp(12));
        String[] names = {"Home", "Discover", "Position", "Wallet", "Control"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            TextView item = button(names[i], i == tab ? CYAN : CARD2, i == tab ? BG : WHITE, 11);
            item.setOnClickListener(v -> { tab = index; render(true); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(39), 1);
            if (i > 0) lp.leftMargin = dp(5);
            bar.addView(item, lp);
        }
        root.addView(bar);
    }

    private void home() {
        LinearLayout hero = card();
        hero.addView(text(store.activeChainLabel().toUpperCase(Locale.US) + " BOT WALLET", 11, CYAN, true));
        hero.addView(text(store.hasWallet() ? shortAddress(store.activeAddress()) : "Wallet not created", 20, WHITE, true));
        hero.addView(text(store.hasWallet() ? "Paper scanner ready. Mainnet swaps are security-blocked." : "Create a separate bot wallet before funding or scanning.", 12, MUTED, false));
        gap(hero, 10);
        LinearLayout metrics = row();
        metrics.addView(metric("MODE", "PAPER", "no real swaps", AMBER), new LinearLayout.LayoutParams(0, -2, 1));
        space(metrics, 7);
        metrics.addView(metric("TRADES", store.tradesToday + " / " + store.maxTradesPerDay, "daily limit", GREEN), new LinearLayout.LayoutParams(0, -2, 1));
        hero.addView(metrics);
        root.addView(hero); gap(10);

        LinearLayout bot = card();
        LinearLayout line = row();
        line.addView(text("MARKET SCANNER", 17, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(text(store.engine.isScanning() ? "CHECKING" : store.state(), 11, "HALTED".equals(store.state()) ? RED : AMBER, true));
        bot.addView(line);
        bot.addView(text(store.lastStatus, 13, MUTED, false));
        if (!store.lastCritical.isEmpty()) bot.addView(text(store.lastCritical, 12, RED, true));
        gap(bot, 10);
        LinearLayout controls = row();
        controls.addView(command("Start", GREEN, v -> startScanner()), new LinearLayout.LayoutParams(0, dp(50), 1));
        space(controls, 7);
        controls.addView(command("Pause", AMBER, v -> pauseScanner()), new LinearLayout.LayoutParams(0, dp(50), 1));
        space(controls, 7);
        controls.addView(command("Panic", RED, v -> panic()), new LinearLayout.LayoutParams(0, dp(50), 1));
        bot.addView(controls);
        root.addView(bot); gap(10);

        LinearLayout pos = card();
        pos.addView(text("CURRENT POSITION", 16, WHITE, true));
        DexEngine.Position p = store.engine.position();
        if (p == null) pos.addView(text("No paper position open. The scanner never places a real swap in this build.", 13, MUTED, false));
        else positionSummary(pos, p, true);
        root.addView(pos);
    }

    private void discover() {
        LinearLayout top = card();
        top.addView(text("DEX DISCOVERY", 18, WHITE, true));
        top.addView(text("DEX Screener candidates are screened locally. A QUALIFIED label is not a scam guarantee.", 12, MUTED, false));
        gap(top, 10);
        top.addView(command("Refresh " + store.activeChainLabel(), CYAN, v -> { store.engine.tick(true); render(false); }), new LinearLayout.LayoutParams(-1, dp(50)));
        root.addView(top); gap(10);
        List<DexCandidate> items = store.engine.candidates();
        if (items.isEmpty()) root.addView(empty("No candidates yet. Start the scanner or refresh discovery."));
        for (DexCandidate c : items) {
            LinearLayout box = card();
            LinearLayout title = row();
            title.addView(text(c.symbol + "  " + c.priceLabel(), 16, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
            int color = "QUALIFIED".equals(c.decision) ? GREEN : ("BLOCKED".equals(c.decision) ? RED : AMBER);
            title.addView(pill(c.decision + " " + c.riskScore, color));
            box.addView(title);
            box.addView(text(c.liquidityLabel() + "  |  24h volume $" + compact(c.volume24hUsd), 12, MUTED, false));
            box.addView(text("1h " + signed(c.change1h) + "%  |  24h " + signed(c.change24h) + "%", 12, CYAN, true));
            box.addView(text(c.reason, 12, color, false));
            root.addView(box); gap(8);
        }
    }

    private void position() {
        LinearLayout box = card();
        box.addView(text("POSITION MONITOR", 18, WHITE, true));
        DexEngine.Position p = store.engine.position();
        if (p == null) {
            box.addView(text("No position is open. Paper automation is the only execution available until direct swap signing has passed device tests.", 13, MUTED, false));
        } else {
            positionSummary(box, p, false);
            gap(box, 10);
            box.addView(command("Close Paper Position", AMBER, v -> { store.engine.closePaperNow(); render(false); }), new LinearLayout.LayoutParams(-1, dp(50)));
        }
        root.addView(box); gap(10);
        LinearLayout exit = card();
        exit.addView(text("DEX EXIT REALITY", 16, AMBER, true));
        exit.addView(text("DEXs do not offer guaranteed OCO exits. A stop is a monitored sell instruction; it can fail if liquidity, network, gas, or the token contract fails.", 13, MUTED, false));
        root.addView(exit);
    }

    private void wallet() {
        LinearLayout box = card();
        box.addView(text("BOT WALLET", 18, WHITE, true));
        if (!store.hasWallet()) {
            box.addView(text("Create a separate wallet for Nanu. Never paste your existing Trust Wallet recovery phrase into this app.", 13, MUTED, false));
            gap(box, 12);
            box.addView(command("Create Bot Wallet", GREEN, v -> createWallet()), new LinearLayout.LayoutParams(-1, dp(52)));
        } else {
            box.addView(text("BNB Chain", 11, CYAN, true));
            box.addView(text(store.bscAddress, 13, WHITE, false));
            gap(box, 8);
            box.addView(text("Solana", 11, CYAN, true));
            box.addView(text(store.solanaAddress, 13, WHITE, false));
            gap(box, 12);
            box.addView(command("Funding Instructions", CYAN, v -> funding()), new LinearLayout.LayoutParams(-1, dp(50)));
            gap(box, 7);
            box.addView(command("View Backup Phrase", AMBER, v -> showBackup()), new LinearLayout.LayoutParams(-1, dp(50)));
        }
        root.addView(box); gap(10);
        LinearLayout info = card();
        info.addView(text("FUNDING SAFETY", 16, AMBER, true));
        info.addView(text("Use the exact displayed network. BNB Chain needs BNB for fees; Solana needs SOL for fees. Do not fund this wallet until you have written the backup phrase offline.", 13, MUTED, false));
        root.addView(info);
    }

    private void control() {
        LinearLayout box = card();
        box.addView(text("RISK CONTROLS", 18, WHITE, true));
        box.addView(text("These limits control paper decisions now and will remain hard limits in a future signed-swap engine.", 12, MUTED, false));
        gap(box, 8);
        setting(box, "Trade amount", money(store.maxTradeUsd) + " USD", v -> editNumber("Maximum paper trade", store.maxTradeUsd, 1, 10_000, x -> store.maxTradeUsd = x));
        setting(box, "Daily loss limit", money(store.maxDailyLossUsd) + " USD", v -> editNumber("Maximum daily loss", store.maxDailyLossUsd, 1, 10_000, x -> store.maxDailyLossUsd = x));
        setting(box, "Daily trade limit", String.valueOf(store.maxTradesPerDay), v -> editNumber("Maximum daily trades", store.maxTradesPerDay, 1, 20, x -> store.maxTradesPerDay = (int) x));
        setting(box, "Minimum liquidity", money(store.minLiquidityUsd), v -> editNumber("Minimum liquidity", store.minLiquidityUsd, 1_000, 10_000_000, x -> store.minLiquidityUsd = x));
        setting(box, "Minimum pair age", store.minPairAgeHours + " hours", v -> editNumber("Minimum pair age", store.minPairAgeHours, 1, 720, x -> store.minPairAgeHours = (int) x));
        setting(box, "Stop / target", store.stopLossPercent + "% / " + store.takeProfitPercent + "%", v -> editStopTarget());
        root.addView(box); gap(10);

        LinearLayout live = card();
        live.addView(text("LIVE DEX EXECUTION", 16, RED, true));
        live.addView(text("Blocked in this build. Real wallet signing and PancakeSwap/Solana swap routing need complete device-level integration tests before a real balance can be exposed.", 13, MUTED, false));
        root.addView(live); gap(10);
        LinearLayout log = card();
        log.addView(text("RECENT EVENTS", 16, WHITE, true));
        List<String> events = store.engine.events();
        if (events.isEmpty()) log.addView(text("No events yet.", 13, MUTED, false));
        for (int i = 0; i < Math.min(9, events.size()); i++) log.addView(text(events.get(i), 12, MUTED, false));
        root.addView(log);
    }

    private void positionSummary(LinearLayout box, DexEngine.Position p, boolean compact) {
        box.addView(text(p.symbol + " / " + ("bsc".equals(p.chain) ? "BNB Chain" : "Solana"), 16, CYAN, true));
        box.addView(text("Entry " + dollar(p.entryPrice) + "  |  Mark " + dollar(p.markPrice), 13, WHITE, false));
        box.addView(text("P/L " + money(p.pnlUsd) + "  |  Target " + dollar(p.targetPrice) + "  |  Stop " + dollar(p.stopPrice), 13, p.pnlUsd >= 0 ? GREEN : RED, true));
        if (!compact) box.addView(text("Paper-only position. No token was purchased and no blockchain transaction exists.", 12, AMBER, false));
    }

    private void startScanner() {
        if (store.panic) { toast("Clear Panic in Control before starting."); return; }
        store.engine.start();
        Intent service = new Intent(this, DexBotService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        render(false);
    }
    private void pauseScanner() { store.engine.stop("Scanner paused by operator."); stopService(new Intent(this, DexBotService.class)); render(false); }
    private void panic() { store.engine.panic(); stopService(new Intent(this, DexBotService.class)); render(false); }

    private void createWallet() {
        new AlertDialog.Builder(this).setTitle("Create Nanu Bot Wallet")
                .setMessage("This creates a new separate wallet. It does not connect to your existing Trust Wallet. You must back up the recovery phrase offline before funding it.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (dialog, which) -> {
                    try {
                        BotWallet.Addresses addresses = BotWallet.create();
                        store.putMnemonic(addresses.mnemonic);
                        store.bscAddress = addresses.bsc;
                        store.solanaAddress = addresses.solana;
                        store.walletCreatedAt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new java.util.Date());
                        store.lastStatus = "Bot wallet created. Back up the recovery phrase before funding it.";
                        store.save();
                        showBackup();
                        render(false);
                    } catch (Throwable error) {
                        alert("Wallet Creation Failed", "Nanu could not initialize the local wallet library: " + readable(error));
                    }
                }).show();
    }

    private void showBackup() {
        String words = store.getMnemonic();
        if (words.isEmpty()) { toast("No wallet backup is available."); return; }
        alert("Bot Wallet Recovery Phrase", "Write these words offline in order. Do not screenshot, share, or paste them into any website.\n\n" + words);
    }
    private void funding() {
        String body = "BNB Chain address:\n" + store.bscAddress + "\n\nSend only BNB Chain assets to this address. Keep BNB for network fees.\n\nSolana address:\n" + store.solanaAddress + "\n\nSend only Solana assets to this address. Keep SOL for transaction fees.\n\nStart with a tiny test transfer after the backup phrase is stored offline.";
        alert("Fund Nanu Bot Wallet", body);
    }

    private interface NumberSave { void save(double value); }
    private void editNumber(String title, double old, double min, double max, NumberSave callback) {
        EditText input = new EditText(this); input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); input.setText(String.format(Locale.US, "%.2f", old)); input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save", (d, w) -> {
            try {
                double value = Double.parseDouble(input.getText().toString().trim());
                if (!DexSafetyPolicy.validAmount(value, min, max)) { toast("Use a value from " + min + " to " + max); return; }
                callback.save(value); store.save(); render(false);
            } catch (Exception ignored) { toast("Enter a valid number."); }
        }).show();
    }
    private void editStopTarget() {
        LinearLayout form = col(); form.setPadding(dp(20), dp(4), dp(20), dp(4));
        EditText stop = new EditText(this); stop.setHint("Stop loss percent"); stop.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); stop.setText(String.valueOf(store.stopLossPercent)); form.addView(stop);
        EditText target = new EditText(this); target.setHint("Take profit percent"); target.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); target.setText(String.valueOf(store.takeProfitPercent)); form.addView(target);
        new AlertDialog.Builder(this).setTitle("Stop and Target").setView(form).setNegativeButton("Cancel", null).setPositiveButton("Save", (d, w) -> {
            try { store.stopLossPercent = Double.parseDouble(stop.getText().toString()); store.takeProfitPercent = Double.parseDouble(target.getText().toString()); store.save(); render(false); }
            catch (Exception ignored) { toast("Enter valid percentages."); }
        }).show();
    }

    private LinearLayout metric(String label, String value, String note, int color) {
        LinearLayout box = col(); box.setPadding(dp(10), dp(9), dp(10), dp(9)); box.setBackground(background(CARD2, CYAN, 8));
        box.addView(text(label, 10, MUTED, true)); box.addView(text(value, 17, color, true)); box.addView(text(note, 10, MUTED, false)); return box;
    }
    private void setting(LinearLayout parent, String label, String value, View.OnClickListener listener) { TextView view = command(label + "\n" + value, CARD2, listener); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56)); lp.topMargin = dp(7); parent.addView(view, lp); }
    private LinearLayout card() { LinearLayout box = col(); box.setPadding(dp(14), dp(14), dp(14), dp(14)); box.setBackground(background(CARD, CYAN, 8)); return box; }
    private LinearLayout empty(String message) { LinearLayout box = card(); box.addView(text(message, 13, MUTED, false)); return box; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); return view; }
    private LinearLayout col() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private void gap(int value) { View view = new View(this); root.addView(view, new LinearLayout.LayoutParams(1, dp(value))); }
    private void gap(LinearLayout parent, int value) { View view = new View(this); parent.addView(view, new LinearLayout.LayoutParams(1, dp(value))); }
    private void space(LinearLayout row, int value) { View view = new View(this); row.addView(view, new LinearLayout.LayoutParams(dp(value), 1)); }
    private TextView text(String value, int size, int color, boolean bold) { TextView view = new TextView(this); view.setText(value == null ? "" : value); view.setTextColor(color); view.setTextSize(size); view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL); view.setLineSpacing(dp(2), 1f); return view; }
    private TextView pill(String value, int color) { TextView view = text(value, 10, BG, true); view.setGravity(Gravity.CENTER); view.setPadding(dp(9), dp(5), dp(9), dp(5)); view.setBackground(background(color, color, 12)); return view; }
    private TextView button(String label, int fill, int ink, int size) { TextView view = text(label, size, ink, true); view.setGravity(Gravity.CENTER); view.setSingleLine(true); view.setBackground(background(fill, fill, 8)); return view; }
    private TextView command(String label, int color, View.OnClickListener listener) { TextView view = text(label, 13, color == CARD2 ? WHITE : BG, true); view.setGravity(Gravity.CENTER); view.setBackground(background(color, color, 8)); view.setOnClickListener(listener); return view; }
    private GradientDrawable background(int fill, int stroke, int radius) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(fill); drawable.setCornerRadius(dp(radius)); drawable.setStroke(dp(1), stroke); return drawable; }
    private void footer() { gap(16); TextView note = text("Nanu DEX Safety Paper - local market scanner", 10, MUTED, false); note.setGravity(Gravity.CENTER); root.addView(note); }
    private void alert(String title, String body) { new AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("OK", null).show(); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private static String readable(Throwable e) { String value = e.getMessage(); return value == null || value.trim().isEmpty() ? e.getClass().getSimpleName() : value; }
    private static String shortAddress(String value) { return value == null || value.length() < 14 ? value : value.substring(0, 7) + "..." + value.substring(value.length() - 5); }
    private static String compact(double value) { return value >= 1000d ? String.format(Locale.US, "%,.0f", value) : String.format(Locale.US, "%.2f", value); }
    private static String signed(double value) { return String.format(Locale.US, "%+.2f", value); }
    private static String money(double value) { return String.format(Locale.US, "$%,.2f", value); }
    private static String dollar(double value) { return value >= 1d ? String.format(Locale.US, "$%.4f", value) : String.format(Locale.US, "$%.8f", value); }
}
