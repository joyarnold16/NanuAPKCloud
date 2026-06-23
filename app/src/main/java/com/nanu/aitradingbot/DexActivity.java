package com.nanu.aitradingbot;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public final class DexActivity extends Activity {
    private static final int BG    = Color.rgb(3, 11, 18);
    private static final int CARD  = Color.rgb(7, 25, 37);
    private static final int CARD2 = Color.rgb(9, 34, 49);
    private static final int CYAN  = Color.rgb(0, 221, 243);
    private static final int GREEN = Color.rgb(87, 246, 136);
    private static final int RED   = Color.rgb(255, 82, 92);
    private static final int AMBER = Color.rgb(255, 192, 79);
    private static final int WHITE = Color.rgb(239, 247, 252);
    private static final int MUTED = Color.rgb(147, 166, 180);

    private DexAppStore store;
    private ScrollView scroll;
    private LinearLayout root;
    private int tab;
    private boolean refreshing;
    private WalletClient.Balances balances;
    private boolean loadingBalances;
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
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 71);
        render(true);
    }

    @Override protected void onResume() { super.onResume(); if (!refreshing) { refreshing = true; handler.post(refresh); } }
    @Override protected void onPause() { refreshing = false; handler.removeCallbacks(refresh); super.onPause(); }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    private void render(boolean top) {
        int oldY = scroll == null ? 0 : scroll.getScrollY();
        if (scroll == null) {
            scroll = new ScrollView(this); scroll.setBackgroundColor(BG);
            scroll.setFillViewport(false); scroll.setVerticalScrollBarEnabled(false);
            root = col(); root.setPadding(dp(14), dp(18), dp(14), dp(26));
            scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
            setContentView(scroll);
        } else root.removeAllViews();
        header(); tabs();
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
        titleBox.addView(text("NANU AI TRADING BOT", 19, WHITE, true));
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
            TextView item = text(names[i], 10, i == tab ? BG : WHITE, true);
            item.setGravity(Gravity.CENTER); item.setSingleLine(true);
            item.setBackground(background(i == tab ? CYAN : CARD2, i == tab ? CYAN : CARD2, 8));
            item.setOnClickListener(v -> { tab = index; render(true); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1);
            if (i > 0) lp.leftMargin = dp(5);
            bar.addView(item, lp);
        }
        root.addView(bar);
    }

    private void home() {
        LinearLayout hero = card();
        hero.addView(text(store.activeChainLabel().toUpperCase(Locale.US) + " BOT WALLET", 11, CYAN, true));
        TextView addrView = text(store.hasWallet() ? shortAddress(store.activeAddress()) : "Wallet not created", 18, WHITE, true);
        addrView.setSingleLine(true); hero.addView(addrView);
        hero.addView(text(store.hasWallet() ? "Paper scanner ready. Mainnet swaps are security-blocked." : "Create a separate bot wallet before funding or scanning.", 12, MUTED, false));
        gap(hero, 10);
        LinearLayout metrics = row();
        metrics.addView(metric("MODE", "PAPER", "no real swaps", AMBER), new LinearLayout.LayoutParams(0, -2, 1));
        space(metrics, 7);
        metrics.addView(metric("TRADES", store.tradesToday + "/" + store.maxTradesPerDay, "today", GREEN), new LinearLayout.LayoutParams(0, -2, 1));
        hero.addView(metrics);
        root.addView(hero); gap(10);

        LinearLayout bot = card();
        LinearLayout line = row();
        line.addView(text("MARKET SCANNER", 16, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(text(store.engine.isScanning() ? "CHECKING" : store.state(), 11, "HALTED".equals(store.state()) ? RED : AMBER, true));
        bot.addView(line);
        bot.addView(text(store.lastStatus, 12, MUTED, false));
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

        // ML Evolution card
        LinearLayout evol = card();
        LinearLayout evolHdr = row(); evolHdr.setGravity(Gravity.CENTER_VERTICAL);
        evolHdr.addView(text("ML EVOLUTION", 16, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        evolHdr.addView(pill("GEN " + store.evolutionGeneration, CYAN));
        evol.addView(evolHdr);
        List<TradeRecord> hist = store.tradeHistory;
        if (!hist.isEmpty()) {
            int w = BotEvolution.countWins(hist);
            int wr = w * 100 / hist.size();
            evol.addView(text(w + "W / " + (hist.size()-w) + "L  |  " + wr + "% win rate  |  " + hist.size() + " trades", 13, wr >= 50 ? GREEN : AMBER, true));
        } else {
            evol.addView(text("No paper trades yet. Start the scanner to collect evolution data.", 12, MUTED, false));
        }
        if (!store.evolutionSummary.isEmpty()) { gap(evol, 4); evol.addView(text(store.evolutionSummary, 11, MUTED, false)); }
        if (!hist.isEmpty()) {
            gap(evol, 8); evol.addView(text("RECENT PAPER TRADES", 10, CYAN, true));
            int shown = 0;
            for (TradeRecord r : hist) {
                if (shown++ >= 5) break;
                LinearLayout tr = row(); tr.setGravity(Gravity.CENTER_VERTICAL); tr.setPadding(0, dp(3), 0, dp(3));
                TextView sym = text(r.symbol, 12, WHITE, true); sym.setSingleLine(true);
                tr.addView(sym, new LinearLayout.LayoutParams(0, -2, 1));
                tr.addView(text(String.format(Locale.US, "%+.2f USD", r.pnlUsd), 12, r.win ? GREEN : RED, true));
                space(tr, 8);
                tr.addView(pill(r.win ? "WIN" : "LOSS", r.win ? GREEN : RED));
                evol.addView(tr);
            }
        }
        root.addView(evol); gap(10);

        LinearLayout pos = card();
        pos.addView(text("CURRENT POSITION", 16, WHITE, true));
        DexEngine.Position p = store.engine.position();
        if (p == null) pos.addView(text("No paper position open. The scanner never places a real swap in this build.", 12, MUTED, false));
        else positionSummary(pos, p, true);
        root.addView(pos);
    }

    private void discover() {
        LinearLayout top = card();
        top.addView(text("DEX DISCOVERY", 18, WHITE, true));
        top.addView(text("Candidates are screened locally. QUALIFIED is not a safety guarantee.", 12, MUTED, false));
        gap(top, 10);
        top.addView(command("Refresh " + store.activeChainLabel(), CYAN, v -> { store.engine.tick(true); render(false); }), new LinearLayout.LayoutParams(-1, dp(50)));
        root.addView(top); gap(10);
        List<DexCandidate> items = store.engine.candidates();
        if (items.isEmpty()) root.addView(empty("No candidates yet. Start the scanner or tap Refresh."));
        for (DexCandidate c : items) {
            LinearLayout box = card();
            LinearLayout title = row(); title.setGravity(Gravity.CENTER_VERTICAL);
            TextView sym = text(c.symbol + "  " + c.priceLabel(), 15, WHITE, true); sym.setSingleLine(true);
            title.addView(sym, new LinearLayout.LayoutParams(0, -2, 1));
            int col = "QUALIFIED".equals(c.decision) ? GREEN : ("BLOCKED".equals(c.decision) ? RED : AMBER);
            title.addView(pill(c.decision + " " + c.riskScore, col));
            box.addView(title);
            box.addView(text(c.liquidityLabel() + "  |  24h vol $" + compact(c.volume24hUsd), 12, MUTED, false));
            box.addView(text("1h " + signed(c.change1h) + "%  |  24h " + signed(c.change24h) + "%", 12, CYAN, true));
            box.addView(text(c.reason, 12, col, false));
            root.addView(box); gap(8);
        }
    }

    private void position() {
        LinearLayout box = card();
        box.addView(text("POSITION MONITOR", 18, WHITE, true));
        DexEngine.Position p = store.engine.position();
        if (p == null) {
            box.addView(text("No position open. Paper automation is the only execution in this build.", 12, MUTED, false));
        } else {
            positionSummary(box, p, false); gap(box, 10);
            box.addView(command("Close Paper Position", AMBER, v -> { store.engine.closePaperNow(); render(false); }), new LinearLayout.LayoutParams(-1, dp(50)));
        }
        root.addView(box); gap(10);
        LinearLayout exit = card();
        exit.addView(text("DEX EXIT REALITY", 16, AMBER, true));
        exit.addView(text("DEXs do not offer guaranteed OCO exits. A stop is a monitored sell instruction; it can fail if liquidity, network, gas, or the token contract fails.", 12, MUTED, false));
        root.addView(exit);
    }

    private void wallet() {
        LinearLayout box = card();
        box.addView(text("BOT WALLET", 18, WHITE, true));
        if (!store.hasWallet()) {
            box.addView(text("Create a separate wallet for Nanu. Never paste your existing Trust Wallet recovery phrase into this app.", 12, MUTED, false));
            gap(box, 12);
            box.addView(command("Create Bot Wallet", GREEN, v -> createWallet()), new LinearLayout.LayoutParams(-1, dp(52)));
        } else {
            LinearLayout balRow = row();
            balRow.addView(metric("BNB", balText(balances == null ? null : balances.bnb, balances != null && balances.bnbOk), "BNB Chain", GREEN), new LinearLayout.LayoutParams(0, -2, 1));
            space(balRow, 6);
            balRow.addView(metric("ETH", balText(balances == null ? null : balances.eth, balances != null && balances.ethOk), "Ethereum", CYAN), new LinearLayout.LayoutParams(0, -2, 1));
            space(balRow, 6);
            balRow.addView(metric("SOL", balText(balances == null ? null : balances.sol, balances != null && balances.solOk), "Solana", AMBER), new LinearLayout.LayoutParams(0, -2, 1));
            box.addView(balRow); gap(box, 10);
            box.addView(command(loadingBalances ? "Checking..." : "Refresh Balances", CYAN, v -> refreshBalances()), new LinearLayout.LayoutParams(-1, dp(48)));
            gap(box, 12);
            box.addView(text("BNB Chain  (also receives ETH / BTCB)", 11, CYAN, true));
            TextView bscAddr = text(store.bscAddress, 12, WHITE, false); bscAddr.setTextIsSelectable(true);
            box.addView(bscAddr); gap(box, 8);
            box.addView(text("Solana", 11, CYAN, true));
            TextView solAddr = text(store.solanaAddress, 12, WHITE, false); solAddr.setTextIsSelectable(true);
            box.addView(solAddr); gap(box, 12);
            LinearLayout btnRow = row();
            btnRow.addView(command("Deposit (QR)", CYAN, v -> depositQR()), new LinearLayout.LayoutParams(0, dp(48), 1));
            space(btnRow, 7);
            btnRow.addView(command("Withdraw", CARD2, v -> withdraw()), new LinearLayout.LayoutParams(0, dp(48), 1));
            box.addView(btnRow); gap(box, 7);
            box.addView(command("View Backup Phrase", AMBER, v -> showBackup()), new LinearLayout.LayoutParams(-1, dp(48)));
        }
        root.addView(box); gap(10);
        LinearLayout info = card();
        info.addView(text("FUNDING SAFETY", 16, AMBER, true));
        info.addView(text("Use the exact displayed network. BNB Chain needs BNB for fees; Solana needs SOL for fees. Do not fund this wallet until you have written the backup phrase offline.", 12, MUTED, false));
        root.addView(info);
    }

    private void depositQR() {
        LinearLayout layout = col(); layout.setPadding(dp(16), dp(8), dp(16), dp(8));
        int qrPx = dp(200);
        layout.addView(text("BNB Chain  (also receives ETH / BTCB)", 11, CYAN, true));
        Bitmap bscBmp = qrBitmap(store.bscAddress, qrPx);
        if (bscBmp != null) { ImageView iv = new ImageView(this); iv.setImageBitmap(bscBmp); layout.addView(iv, new LinearLayout.LayoutParams(qrPx, qrPx)); }
        layout.addView(text(store.bscAddress, 11, WHITE, false)); gap(layout, 14);
        layout.addView(text("Solana", 11, CYAN, true));
        Bitmap solBmp = qrBitmap(store.solanaAddress, qrPx);
        if (solBmp != null) { ImageView iv2 = new ImageView(this); iv2.setImageBitmap(solBmp); layout.addView(iv2, new LinearLayout.LayoutParams(qrPx, qrPx)); }
        layout.addView(text(store.solanaAddress, 11, WHITE, false));
        ScrollView sv = new ScrollView(this); sv.addView(layout);
        new AlertDialog.Builder(this).setTitle("Deposit to Bot Wallet").setView(sv).setPositiveButton("OK", null).show();
    }

    private void withdraw() {
        alert("Withdraw — Coming Soon",
            "Withdraw signing is being integrated with Trust Wallet Core signing and will be available in the next update.\n\n" +
            "To withdraw now: import your backup phrase into the Trust Wallet app and send from there.\n\n" +
            "Your BNB Chain address:\n" + store.bscAddress + "\n\nYour Solana address:\n" + store.solanaAddress);
    }

    private void refreshBalances() {
        if (loadingBalances) return;
        if (!store.hasWallet()) { toast("Create the bot wallet first."); return; }
        loadingBalances = true; render(false);
        WalletClient.fetch(store.bscAddress, store.solanaAddress, (result, status) -> handler.post(() -> {
            balances = result; loadingBalances = false; store.lastStatus = status; store.save(); render(false);
        }));
    }

    private String balText(java.math.BigDecimal value, boolean ok) {
        if (!ok || value == null) return "--";
        return value.setScale(4, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString();
    }

    private void control() {
        LinearLayout box = card();
        box.addView(text("RISK CONTROLS", 18, WHITE, true));
        box.addView(text("These limits control paper decisions and will remain hard limits in a future signed-swap engine.", 12, MUTED, false));
        gap(box, 8);
        setting(box, "Trade amount", money(store.maxTradeUsd) + " USD", v -> editNumber("Maximum paper trade", store.maxTradeUsd, 1, 10_000, x -> store.maxTradeUsd = x));
        setting(box, "Daily loss limit", money(store.maxDailyLossUsd) + " USD", v -> editNumber("Maximum daily loss", store.maxDailyLossUsd, 1, 10_000, x -> store.maxDailyLossUsd = x));
        setting(box, "Daily trade limit", String.valueOf(store.maxTradesPerDay), v -> editNumber("Maximum daily trades", store.maxTradesPerDay, 1, 20, x -> store.maxTradesPerDay = (int) x));
        setting(box, "Min liquidity", money(store.minLiquidityUsd), v -> editNumber("Minimum liquidity", store.minLiquidityUsd, 1_000, 10_000_000, x -> store.minLiquidityUsd = x));
        setting(box, "Min pair age", store.minPairAgeHours + " hours", v -> editNumber("Minimum pair age", store.minPairAgeHours, 1, 720, x -> store.minPairAgeHours = (int) x));
        setting(box, "Min momentum", String.format(Locale.US, "%.1f%%", store.minMomentumPercent), v -> editNumber("Minimum 1h momentum %", store.minMomentumPercent, 0.1, 20, x -> store.minMomentumPercent = x));
        setting(box, "Stop / target", store.stopLossPercent + "% / " + store.takeProfitPercent + "%", v -> editStopTarget());
        root.addView(box); gap(10);

        LinearLayout evolBox = card();
        evolBox.addView(text("EVOLUTION STATUS", 16, CYAN, true));
        evolBox.addView(text("Gen " + store.evolutionGeneration + " — " + store.tradeHistory.size() + " trades recorded. Evolution runs every " + BotEvolution.MIN_TRADES + " completed trades.", 12, WHITE, false));
        if (!store.evolutionSummary.isEmpty()) { gap(evolBox, 4); evolBox.addView(text(store.evolutionSummary, 11, MUTED, false)); }
        gap(evolBox, 8);
        evolBox.addView(command("Force Evolve Now", CYAN, v -> {
            if (store.tradeHistory.size() < BotEvolution.MIN_TRADES) { toast("Need " + BotEvolution.MIN_TRADES + " trades first."); return; }
            BotEvolution.Result r = BotEvolution.evolve(store.tradeHistory, store);
            store.evolutionSummary = r.summary; store.save();
            if (r.evolved) store.engine.event("Manual evolution: " + r.summary);
            toast(r.evolved ? "Parameters evolved!" : "No improvement found."); render(false);
        }), new LinearLayout.LayoutParams(-1, dp(48)));
        root.addView(evolBox); gap(10);

        LinearLayout live = card();
        live.addView(text("LIVE DEX EXECUTION", 16, RED, true));
        live.addView(text("Blocked in this build. Real wallet signing requires complete device integration tests before exposing a real balance.", 12, MUTED, false));
        root.addView(live); gap(10);

        LinearLayout log = card();
        log.addView(text("RECENT EVENTS", 16, WHITE, true));
        List<String> events = store.engine.events();
        if (events.isEmpty()) log.addView(text("No events yet.", 12, MUTED, false));
        for (int i = 0; i < Math.min(9, events.size()); i++) log.addView(text(events.get(i), 11, MUTED, false));
        root.addView(log);
    }

    private void positionSummary(LinearLayout box, DexEngine.Position p, boolean compact) {
        box.addView(text(p.symbol + " / " + ("bsc".equals(p.chain) ? "BNB Chain" : "Solana"), 15, CYAN, true));
        box.addView(text("Entry " + dollar(p.entryPrice) + "  |  Mark " + dollar(p.markPrice), 12, WHITE, false));
        box.addView(text("P/L " + money(p.pnlUsd) + "  |  Target " + dollar(p.targetPrice) + "  |  Stop " + dollar(p.stopPrice), 12, p.pnlUsd >= 0 ? GREEN : RED, true));
        if (!compact) box.addView(text("Paper-only position. No token was purchased and no blockchain transaction exists.", 11, AMBER, false));
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
            .setPositiveButton("Create", (d, w) -> {
                try {
                    BotWallet.Addresses a = BotWallet.create();
                    store.putMnemonic(a.mnemonic);
                    store.bscAddress = a.bsc; store.solanaAddress = a.solana;
                    store.walletCreatedAt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new java.util.Date());
                    store.lastStatus = "Bot wallet created. Back up the recovery phrase before funding it.";
                    store.save(); showBackup(); render(false);
                } catch (Throwable e) {
                    alert("Wallet Creation Failed", "Nanu could not initialize the local wallet library: " + readable(e));
                }
            }).show();
    }

    private void showBackup() {
        String words = store.getMnemonic();
        if (words.isEmpty()) { toast("No wallet backup is available."); return; }
        alert("Bot Wallet Recovery Phrase", "Write these words offline in order. Do not screenshot, share, or paste them into any website.\n\n" + words);
    }

    private Bitmap qrBitmap(String content, int px) {
        try {
            com.google.zxing.qrcode.QRCodeWriter w = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix m = w.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, px, px);
            Bitmap bmp = Bitmap.createBitmap(px, px, Bitmap.Config.RGB_565);
            for (int x = 0; x < px; x++) for (int y = 0; y < px; y++)
                bmp.setPixel(x, y, m.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            return bmp;
        } catch (Exception e) { return null; }
    }

    private interface NumberSave { void save(double value); }
    private void editNumber(String title, double old, double min, double max, NumberSave cb) {
        EditText input = new EditText(this); input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.US, "%.2f", old)); input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Cancel", null)
            .setPositiveButton("Save", (d, w) -> {
                try {
                    double v = Double.parseDouble(input.getText().toString().trim());
                    if (!DexSafetyPolicy.validAmount(v, min, max)) { toast("Use " + min + " to " + max); return; }
                    cb.save(v); store.save(); render(false);
                } catch (Exception ignored) { toast("Enter a valid number."); }
            }).show();
    }
    private void editStopTarget() {
        LinearLayout form = col(); form.setPadding(dp(20), dp(4), dp(20), dp(4));
        EditText stop = new EditText(this); stop.setHint("Stop loss %"); stop.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); stop.setText(String.valueOf(store.stopLossPercent)); form.addView(stop);
        EditText target = new EditText(this); target.setHint("Take profit %"); target.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); target.setText(String.valueOf(store.takeProfitPercent)); form.addView(target);
        new AlertDialog.Builder(this).setTitle("Stop and Target").setView(form).setNegativeButton("Cancel", null)
            .setPositiveButton("Save", (d, w) -> {
                try { store.stopLossPercent = Double.parseDouble(stop.getText().toString()); store.takeProfitPercent = Double.parseDouble(target.getText().toString()); store.save(); render(false); }
                catch (Exception ignored) { toast("Enter valid percentages."); }
            }).show();
    }

    private LinearLayout metric(String label, String value, String note, int color) {
        LinearLayout box = col(); box.setPadding(dp(8), dp(10), dp(8), dp(10)); box.setBackground(background(CARD2, CYAN, 8));
        box.setMinimumHeight(dp(72));
        TextView lbl = text(label, 10, MUTED, true); lbl.setSingleLine(true); box.addView(lbl);
        TextView val = text(value, 15, color, true); val.setSingleLine(true); box.addView(val);
        TextView nt = text(note, 10, MUTED, false); nt.setSingleLine(true); box.addView(nt);
        return box;
    }
    private void setting(LinearLayout parent, String label, String value, View.OnClickListener l) {
        TextView view = command(label + "\n" + value, CARD2, l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56)); lp.topMargin = dp(7); parent.addView(view, lp);
    }
    private LinearLayout card() { LinearLayout box = col(); box.setPadding(dp(14), dp(14), dp(14), dp(14)); box.setBackground(background(CARD, CYAN, 8)); return box; }
    private LinearLayout empty(String msg) { LinearLayout box = card(); box.addView(text(msg, 12, MUTED, false)); return box; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private LinearLayout col() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private void gap(int value) { View v = new View(this); root.addView(v, new LinearLayout.LayoutParams(1, dp(value))); }
    private void gap(LinearLayout p, int value) { View v = new View(this); p.addView(v, new LinearLayout.LayoutParams(1, dp(value))); }
    private void space(LinearLayout row, int value) { View v = new View(this); row.addView(v, new LinearLayout.LayoutParams(dp(value), 1)); }
    private TextView text(String value, int size, int color, boolean bold) { TextView v = new TextView(this); v.setText(value == null ? "" : value); v.setTextColor(color); v.setTextSize(size); v.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL); v.setLineSpacing(dp(2), 1f); return v; }
    private TextView pill(String value, int color) { TextView v = text(value, 10, BG, true); v.setGravity(Gravity.CENTER); v.setPadding(dp(9), dp(5), dp(9), dp(5)); v.setBackground(background(color, color, 12)); return v; }
    private TextView command(String label, int color, View.OnClickListener listener) { TextView v = text(label, 13, color == CARD2 ? WHITE : BG, true); v.setGravity(Gravity.CENTER); v.setBackground(background(color, color, 8)); v.setOnClickListener(listener); return v; }
    private GradientDrawable background(int fill, int stroke, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), stroke); return d; }
    private void footer() { gap(16); TextView note = text("Nanu DEX ML Evolution — paper scanner", 10, MUTED, false); note.setGravity(Gravity.CENTER); root.addView(note); }
    private void alert(String title, String body) { new AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("OK", null).show(); }
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
    private static String readable(Throwable e) { String v = e.getMessage(); return v == null || v.trim().isEmpty() ? e.getClass().getSimpleName() : v; }
    private static String shortAddress(String v) { return v == null || v.length() < 14 ? v : v.substring(0, 7) + "…" + v.substring(v.length() - 5); }
    private static String compact(double v) { return v >= 1000 ? String.format(Locale.US, "%,.0f", v) : String.format(Locale.US, "%.2f", v); }
    private static String signed(double v) { return String.format(Locale.US, "%+.2f", v); }
    private static String money(double v) { return String.format(Locale.US, "$%,.2f", v); }
    private static String dollar(double v) { return v >= 1d ? String.format(Locale.US, "$%.4f", v) : String.format(Locale.US, "$%.8f", v); }
}
