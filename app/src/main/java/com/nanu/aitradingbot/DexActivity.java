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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public final class DexActivity extends Activity {
    private static final int BG     = Color.rgb(3, 11, 18);
    private static final int CARD   = Color.rgb(7, 25, 37);
    private static final int CARD2  = Color.rgb(9, 34, 49);
    private static final int CYAN   = Color.rgb(0, 221, 243);
    private static final int GREEN  = Color.rgb(87, 246, 136);
    private static final int RED    = Color.rgb(255, 82, 92);
    private static final int AMBER  = Color.rgb(255, 192, 79);
    private static final int PURPLE = Color.rgb(138, 60, 220);
    private static final int WHITE  = Color.rgb(239, 247, 252);
    private static final int MUTED  = Color.rgb(147, 166, 180);

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
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 71);
        render(true);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!refreshing) { refreshing = true; handler.post(refresh); }
    }

    @Override protected void onPause() {
        refreshing = false; handler.removeCallbacks(refresh); super.onPause();
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    private void render(boolean top) {
        int oldY = scroll == null ? 0 : scroll.getScrollY();
        if (scroll == null) {
            scroll = new ScrollView(this);
            scroll.setBackgroundColor(BG);
            scroll.setFillViewport(false);
            scroll.setVerticalScrollBarEnabled(false);
            root = col();
            root.setPadding(dp(12), statusBarHeight() + dp(4), dp(12), dp(28));
            scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
            setContentView(scroll);
        } else root.removeAllViews();
        header();
        tabs();
        if (tab == 0) home();
        else if (tab == 1) discover();
        else if (tab == 2) history();
        else if (tab == 3) position();
        else if (tab == 4) wallet();
        else control();
        footer();
        if (!top) scroll.post(() -> scroll.scrollTo(0, oldY));
    }

    private void header() {
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(8));

        int mascotId = getResources().getIdentifier("nanu_mascot", "drawable", getPackageName());
        if (mascotId != 0) {
            ImageView logo = new ImageView(this);
            logo.setImageResource(mascotId);
            logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable oval = new GradientDrawable();
            oval.setShape(GradientDrawable.OVAL);
            oval.setColor(CARD2);
            oval.setStroke(dp(2), CYAN);
            logo.setBackground(oval);
            if (Build.VERSION.SDK_INT >= 21) logo.setClipToOutline(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
            lp.rightMargin = dp(10);
            row.addView(logo, lp);
        }

        LinearLayout titleBox = col();
        titleBox.addView(text("NANU AI TRADING BOT", 17, WHITE, true));
        LinearLayout stateRow = row();
        stateRow.setGravity(Gravity.CENTER_VERTICAL);
        stateRow.addView(text("BNB + SOL  \u2022  PAPER", 10, CYAN, true),
            new LinearLayout.LayoutParams(0, -2, 1));
        String state = store.state();
        int sc = "SCANNING".equals(state)||"POSITION OPEN".equals(state) ? GREEN :
                 "HALTED".equals(state) ? RED : MUTED;
        stateRow.addView(pill(state, sc));
        titleBox.addView(stateRow);
        row.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(row);
    }

    private void tabs() {
        LinearLayout bar = row(); bar.setPadding(0, 0, 0, dp(10));
        String[] names = {"Home","Discover","History","Position","Wallet","Control"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView item = button(names[i], i == tab ? CYAN : CARD2, i == tab ? BG : WHITE, 9);
            item.setOnClickListener(v -> { tab = idx; render(true); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (i > 0) lp.leftMargin = dp(3);
            bar.addView(item, lp);
        }
        root.addView(bar);
    }

    private void home() {
        double totalPnl = 0;
        for (TradeRecord r : store.tradeHistory) totalPnl += r.pnlUsd;

        int mascotId = getResources().getIdentifier("nanu_mascot", "drawable", getPackageName());
        if (mascotId != 0) {
            ImageView banner = new ImageView(this);
            banner.setImageResource(mascotId);
            banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            banner.setBackground(background(CARD, PURPLE, 12));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(180));
            blp.bottomMargin = dp(10);
            root.addView(banner, blp);
        }

        LinearLayout hero = card();
        hero.addView(text("BOT WALLET", 11, CYAN, true));
        hero.addView(text(store.hasWallet() ?
            shortAddr(store.bscAddress) + "  (BNB)" : "Wallet not created", 16, WHITE, true));
        if (store.hasWallet())
            hero.addView(text(shortAddr(store.solanaAddress) + "  (SOL)", 13, MUTED, false));
        hero.addView(text(store.hasWallet() ?
            "Paper scanner active. Mainnet swaps are security-blocked." :
            "Go to Wallet tab to create a separate bot wallet.", 12, MUTED, false));
        gap(hero, 10);
        LinearLayout metrics = row();
        final double pnl = totalPnl;
        metrics.addView(metric("MODE", "PAPER", "no real swaps", AMBER),
            new LinearLayout.LayoutParams(0, -2, 1));
        space(metrics, 5);
        metrics.addView(metric("TRADES", store.tradesToday + "/" + store.maxTradesPerDay, "today", GREEN),
            new LinearLayout.LayoutParams(0, -2, 1));
        space(metrics, 5);
        metrics.addView(metric("PAPER P/L", String.format(Locale.US, "%+.2f", pnl), "USD all time",
            pnl >= 0 ? GREEN : RED), new LinearLayout.LayoutParams(0, -2, 1));
        hero.addView(metrics);
        root.addView(hero); gap(10);

        LinearLayout bot = card();
        LinearLayout hdr = row();
        hdr.addView(text("MARKET SCANNER", 15, WHITE, true),
            new LinearLayout.LayoutParams(0, -2, 1));
        hdr.addView(text(store.engine.isScanning() ? "CHECKING" : store.state(),
            11, "HALTED".equals(store.state()) ? RED : AMBER, true));
        bot.addView(hdr);
        bot.addView(text(store.lastStatus, 13, MUTED, false));
        if (!store.lastCritical.isEmpty()) bot.addView(text(store.lastCritical, 12, RED, true));
        if (!store.evolutionSummary.isEmpty()) {
            gap(bot, 5);
            bot.addView(text("\u2728 ML Gen " + store.evolutionGeneration +
                ": " + store.evolutionSummary, 11, PURPLE, false));
        }
        gap(bot, 10);
        LinearLayout ctrl = row();
        ctrl.addView(command("Start", GREEN, v -> startScanner()),
            new LinearLayout.LayoutParams(0, dp(50), 1));
        space(ctrl, 5);
        ctrl.addView(command("Pause", AMBER, v -> pauseScanner()),
            new LinearLayout.LayoutParams(0, dp(50), 1));
        space(ctrl, 5);
        ctrl.addView(command("Panic", RED, v -> panic()),
            new LinearLayout.LayoutParams(0, dp(50), 1));
        bot.addView(ctrl);
        root.addView(bot); gap(10);

        LinearLayout pos = card();
        pos.addView(text("CURRENT POSITION", 14, WHITE, true));
        DexEngine.Position p = store.engine.position();
        if (p == null)
            pos.addView(text("No paper position open. Scanner never places a real swap.", 13, MUTED, false));
        else positionSummary(pos, p, true);
        root.addView(pos);
    }

    private void discover() {
        LinearLayout top = card();
        top.addView(text("DEX DISCOVERY  \u2014  BNB + SOL", 17, WHITE, true));
        top.addView(text("Both chains scanned. Candlestick patterns applied. "
            + "QUALIFIED label is not a scam guarantee.", 12, MUTED, false));
        gap(top, 10);
        top.addView(command("Scan Now (BNB + SOL)", CYAN,
            v -> { store.engine.tick(true); render(false); }),
            new LinearLayout.LayoutParams(-1, dp(50)));
        root.addView(top); gap(10);

        List<DexCandidate> items = store.engine.candidates();
        if (items.isEmpty()) {
            root.addView(empty("No candidates yet. Tap Scan Now or start the scanner on Home."));
            return;
        }
        for (DexCandidate c : items) {
            List<String> patterns = CandlePatterns.detect(c);
            int adjScore = c.riskScore + CandlePatterns.scoreAdj(patterns);
            LinearLayout box = card();
            LinearLayout titleRow = row();
            // Chain badge
            TextView chainBadge = pill("bsc".equals(c.chain) ? "BNB" : "SOL",
                "bsc".equals(c.chain) ? AMBER : CYAN);
            titleRow.addView(chainBadge);
            space(titleRow, 6);
            titleRow.addView(text(c.symbol + "  " + c.priceLabel(), 15, WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1));
            int col = "QUALIFIED".equals(c.decision) ? GREEN :
                      "BLOCKED".equals(c.decision) ? RED : AMBER;
            titleRow.addView(pill(c.decision + " " + adjScore, col));
            box.addView(titleRow);
            box.addView(text(c.liquidityLabel() + "  |  Vol $" + compact(c.volume24hUsd),
                12, MUTED, false));
            box.addView(text("1h " + signed(c.change1h) + "%  |  24h " + signed(c.change24h) + "%",
                12, CYAN, true));
            if (!patterns.isEmpty()) {
                int patColor = CandlePatterns.bullish(patterns) ? GREEN :
                               CandlePatterns.bearish(patterns) ? RED : AMBER;
                box.addView(text("\u25cf " + CandlePatterns.summary(patterns),
                    11, patColor, true));
            }
            box.addView(text(c.reason, 12, col, false));
            root.addView(box); gap(8);
        }
    }

    private void history() {
        LinearLayout top = card();
        top.addView(text("TRADE HISTORY", 17, WHITE, true));
        List<TradeRecord> records = store.tradeHistory;
        if (records.isEmpty()) {
            top.addView(text("No closed trades yet. Paper positions appear here when they close.",
                13, MUTED, false));
            root.addView(top); return;
        }
        int wins = 0; double totalPnl = 0;
        for (TradeRecord r : records) { if (r.win) wins++; totalPnl += r.pnlUsd; }
        double wr = records.size() > 0 ? wins * 100.0 / records.size() : 0;
        gap(top, 8);
        LinearLayout summary = row();
        summary.addView(metric("TRADES", String.valueOf(records.size()), "closed", WHITE),
            new LinearLayout.LayoutParams(0, -2, 1));
        space(summary, 5);
        summary.addView(metric("WIN RATE", String.format(Locale.US, "%.0f%%", wr),
            wins + " wins", wr >= 55 ? GREEN : wr >= 40 ? AMBER : RED),
            new LinearLayout.LayoutParams(0, -2, 1));
        space(summary, 5);
        final double tp = totalPnl;
        summary.addView(metric("TOTAL P/L", String.format(Locale.US, "%+.2f", tp), "paper USD",
            tp >= 0 ? GREEN : RED), new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(summary);
        if (!store.evolutionSummary.isEmpty()) {
            gap(top, 8);
            top.addView(text("\u2728 ML Gen " + store.evolutionGeneration +
                ": " + store.evolutionSummary, 11, PURPLE, false));
        }
        gap(top, 10);
        top.addView(command("Export CSV to Downloads", CARD2, v -> exportCsv(records)),
            new LinearLayout.LayoutParams(-1, dp(44)));
        root.addView(top); gap(10);
        for (TradeRecord r : records) {
            LinearLayout box = card();
            LinearLayout titleRow = row();
            titleRow.addView(pill("bsc".equals(r.chain) ? "BNB" : "SOL",
                "bsc".equals(r.chain) ? AMBER : CYAN));
            space(titleRow, 6);
            titleRow.addView(text(r.symbol, 14, WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1));
            titleRow.addView(pill(r.win ? "WIN" : "LOSS", r.win ? GREEN : RED));
            box.addView(titleRow);
            box.addView(text(String.format(Locale.US,
                "Entry $%.6f  \u2192  Exit $%.6f", r.entryPrice, r.exitPrice), 12, MUTED, false));
            box.addView(text(String.format(Locale.US,
                "P/L  %+.4f USD  (%+.2f%%)", r.pnlUsd, r.pnlPct), 13, r.win ? GREEN : RED, true));
            box.addView(text("Exit: " + r.exitReason, 11, AMBER, false));
            root.addView(box); gap(7);
        }
    }

    private void exportCsv(List<TradeRecord> records) {
        try {
            StringBuilder sb = new StringBuilder(
                "symbol,chain,entry_price,exit_price,pnl_usd,pnl_pct,win,exit_reason,opened_ms\n");
            for (TradeRecord r : records)
                sb.append(String.format(Locale.US, "%s,%s,%.8f,%.8f,%.4f,%.2f,%s,%s,%d\n",
                    r.symbol, r.chain, r.entryPrice, r.exitPrice,
                    r.pnlUsd, r.pnlPct, r.win ? "WIN" : "LOSS", r.exitReason, r.openedAtMs));
            java.io.File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
            java.io.File file = new java.io.File(dir, "nanu_trades.csv");
            java.io.FileWriter fw = new java.io.FileWriter(file);
            fw.write(sb.toString()); fw.close();
            toast("Saved: " + file.getAbsolutePath());
        } catch (Exception e) { toast("Export failed: " + e.getMessage()); }
    }

    private void position() {
        LinearLayout box = card();
        box.addView(text("POSITION MONITOR", 17, WHITE, true));
        DexEngine.Position p = store.engine.position();
        if (p == null) {
            box.addView(text("No position open. Paper automation is the only execution available "
                + "until swap signing passes device tests.", 13, MUTED, false));
        } else {
            positionSummary(box, p, false);
            gap(box, 10);
            box.addView(command("Close Paper Position", AMBER,
                v -> { store.engine.closePaperNow(); render(false); }),
                new LinearLayout.LayoutParams(-1, dp(50)));
        }
        root.addView(box); gap(10);
        LinearLayout warn = card();
        warn.addView(text("DEX EXIT REALITY", 15, AMBER, true));
        warn.addView(text("DEXs do not guarantee OCO exits. A stop is a monitored sell; "
            + "it can fail if liquidity, network, gas, or the contract fails.", 13, MUTED, false));
        root.addView(warn);
    }

    private void wallet() {
        // ── Balances ──────────────────────────────────────────────────────
        LinearLayout box = card();
        box.addView(text("BOT WALLET", 17, WHITE, true));
        if (!store.hasWallet()) {
            box.addView(text("Create a separate wallet for Nanu. Never paste your existing "
                + "Trust Wallet recovery phrase into this app.", 13, MUTED, false));
            gap(box, 12);
            box.addView(command("Create Bot Wallet", GREEN, v -> createWallet()),
                new LinearLayout.LayoutParams(-1, dp(52)));
            root.addView(box);
        } else {
            LinearLayout balRow = row();
            balRow.addView(metric("BNB", balTxt(balances==null?null:balances.bnb, balances!=null&&balances.bnbOk), "BNB Chain", GREEN), new LinearLayout.LayoutParams(0, -2, 1));
            space(balRow, 5);
            balRow.addView(metric("ETH", balTxt(balances==null?null:balances.eth, balances!=null&&balances.ethOk), "Ethereum",  CYAN),  new LinearLayout.LayoutParams(0, -2, 1));
            space(balRow, 5);
            balRow.addView(metric("SOL", balTxt(balances==null?null:balances.sol, balances!=null&&balances.solOk), "Solana",    AMBER), new LinearLayout.LayoutParams(0, -2, 1));
            box.addView(balRow); gap(box, 10);
            box.addView(command(loadingBalances ? "Checking..." : "Refresh Balances",
                CYAN, v -> refreshBalances()),
                new LinearLayout.LayoutParams(-1, dp(48)));
            gap(box, 7);
            box.addView(command("View Backup Phrase", AMBER, v -> showBackup()),
                new LinearLayout.LayoutParams(-1, dp(48)));
            root.addView(box); gap(10);

            // ── QR Code — BNB deposit ─────────────────────────────────────
            LinearLayout qrCard = card();
            qrCard.addView(text("DEPOSIT — BNB CHAIN", 14, CYAN, true));
            qrCard.addView(text("Scan QR or copy address below. Send BNB for fees.", 12, MUTED, false));
            gap(qrCard, 8);
            Bitmap qrBnb = qrCode(store.bscAddress, dp(200));
            if (qrBnb != null) {
                LinearLayout qrWrap = col(); qrWrap.setGravity(Gravity.CENTER);
                ImageView qrView = new ImageView(this);
                qrView.setImageBitmap(qrBnb);
                qrView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                qrView.setPadding(dp(10), dp(10), dp(10), dp(10));
                qrView.setBackgroundColor(Color.WHITE);
                qrWrap.addView(qrView, new LinearLayout.LayoutParams(dp(200), dp(200)));
                qrCard.addView(qrWrap);
            }
            gap(qrCard, 6);
            qrCard.addView(text(store.bscAddress, 12, WHITE, false));
            gap(qrCard, 12);

            qrCard.addView(text("DEPOSIT — SOLANA", 14, CYAN, true));
            qrCard.addView(text("Scan QR or copy address below. Send SOL for fees.", 12, MUTED, false));
            gap(qrCard, 8);
            Bitmap qrSol = qrCode(store.solanaAddress, dp(200));
            if (qrSol != null) {
                LinearLayout qrWrap2 = col(); qrWrap2.setGravity(Gravity.CENTER);
                ImageView qrView2 = new ImageView(this);
                qrView2.setImageBitmap(qrSol);
                qrView2.setScaleType(ImageView.ScaleType.FIT_CENTER);
                qrView2.setPadding(dp(10), dp(10), dp(10), dp(10));
                qrView2.setBackgroundColor(Color.WHITE);
                qrWrap2.addView(qrView2, new LinearLayout.LayoutParams(dp(200), dp(200)));
                qrCard.addView(qrWrap2);
            }
            gap(qrCard, 6);
            qrCard.addView(text(store.solanaAddress, 12, WHITE, false));
            gap(qrCard, 10);
            qrCard.addView(command("Funding Instructions", CARD2, v -> funding()),
                new LinearLayout.LayoutParams(-1, dp(44)));
            root.addView(qrCard); gap(10);

            // ── Withdraw ─────────────────────────────────────────────────
            LinearLayout wdCard = card();
            wdCard.addView(text("WITHDRAW", 15, AMBER, true));
            wdCard.addView(text("Enter details below. Real signing is not yet enabled — "
                + "this will confirm once Trust Wallet Core signing passes device tests.",
                12, MUTED, false));
            gap(wdCard, 10);

            wdCard.addView(text("Destination Address", 11, CYAN, true));
            EditText destAddr = new EditText(this);
            destAddr.setHint("0x... or Sol address");
            destAddr.setTextColor(WHITE); destAddr.setHintTextColor(MUTED);
            destAddr.setBackground(background(CARD2, CYAN, 6));
            destAddr.setPadding(dp(10), dp(10), dp(10), dp(10));
            wdCard.addView(destAddr); gap(wdCard, 8);

            wdCard.addView(text("Amount", 11, CYAN, true));
            EditText amtField = new EditText(this);
            amtField.setHint("0.00");
            amtField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            amtField.setTextColor(WHITE); amtField.setHintTextColor(MUTED);
            amtField.setBackground(background(CARD2, CYAN, 6));
            amtField.setPadding(dp(10), dp(10), dp(10), dp(10));
            wdCard.addView(amtField); gap(wdCard, 12);

            wdCard.addView(command("Withdraw (Signing Not Yet Active)", RED, v ->
                alert("Withdrawal Not Yet Enabled",
                    "Live wallet signing is deliberately disabled pending Trust Wallet Core "
                    + "integration tests. This feature will be unlocked after paper trading "
                    + "is proven with real market data.\n\n"
                    + "Destination entered: " + destAddr.getText().toString() + "\n"
                    + "Amount entered: " + amtField.getText().toString())),
                new LinearLayout.LayoutParams(-1, dp(50)));
            root.addView(wdCard); gap(10);
        }

        LinearLayout safety = card();
        safety.addView(text("FUNDING SAFETY", 14, AMBER, true));
        safety.addView(text("Use the exact displayed network. BNB Chain needs BNB for gas; "
            + "Solana needs SOL for fees. Do not fund until recovery phrase is stored offline.",
            13, MUTED, false));
        root.addView(safety);
    }

    private void refreshBalances() {
        if (loadingBalances) return;
        if (!store.hasWallet()) { toast("Create the bot wallet first."); return; }
        loadingBalances = true; render(false);
        WalletClient.fetch(store.bscAddress, store.solanaAddress, (result, status) ->
            handler.post(() -> {
                balances = result; loadingBalances = false;
                store.lastStatus = status; store.save(); render(false);
            }));
    }

    private String balTxt(java.math.BigDecimal v, boolean ok) {
        if (!ok || v == null) return "--";
        return v.setScale(5, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString();
    }

    private void control() {
        LinearLayout box = card();
        box.addView(text("RISK CONTROLS", 17, WHITE, true));
        box.addView(text("These limits control paper decisions and will remain hard limits "
            + "if live execution is ever enabled.", 12, MUTED, false));
        gap(box, 8);
        setting(box, "Trade amount", money(store.maxTradeUsd) + " USD",
            v -> editNum("Max paper trade", store.maxTradeUsd, 1, 10_000, x -> store.maxTradeUsd = x));
        setting(box, "Daily loss limit", money(store.maxDailyLossUsd) + " USD",
            v -> editNum("Max daily loss", store.maxDailyLossUsd, 1, 10_000, x -> store.maxDailyLossUsd = x));
        setting(box, "Daily trade limit", String.valueOf(store.maxTradesPerDay),
            v -> editNum("Max daily trades", store.maxTradesPerDay, 1, 20, x -> store.maxTradesPerDay = (int)x));
        setting(box, "Minimum liquidity", money(store.minLiquidityUsd),
            v -> editNum("Min liquidity", store.minLiquidityUsd, 1_000, 10_000_000, x -> store.minLiquidityUsd = x));
        setting(box, "Minimum pair age", store.minPairAgeHours + " hours",
            v -> editNum("Min pair age hours", store.minPairAgeHours, 1, 720, x -> store.minPairAgeHours = (int)x));
        setting(box, "Stop / target", store.stopLossPercent + "% / " + store.takeProfitPercent + "%",
            v -> editStopTarget());
        root.addView(box); gap(10);

        if (!store.evolutionSummary.isEmpty()) {
            LinearLayout ml = card();
            ml.addView(text("ML EVOLUTION", 15, PURPLE, true));
            ml.addView(text("Gen " + store.evolutionGeneration + ": " + store.evolutionSummary, 12, WHITE, false));
            ml.addView(text("Strategies: BALANCED · MOMENTUM · CONSERVATIVE · VOLUME_SPIKE\n"
                + "ACCUMULATION · SNIPER · SAFE_LARGE · ANTI_DUMP", 11, MUTED, false));
            root.addView(ml); gap(10);
        }

        LinearLayout live = card();
        live.addView(text("LIVE DEX EXECUTION", 15, RED, true));
        live.addView(text("Blocked in this build. Real swap signing needs complete device "
            + "integration tests before any real balance is exposed.", 13, MUTED, false));
        root.addView(live); gap(10);

        LinearLayout log = card();
        log.addView(text("RECENT EVENTS", 15, WHITE, true));
        List<String> events = store.engine.events();
        if (events.isEmpty()) log.addView(text("No events yet.", 13, MUTED, false));
        for (int i = 0; i < Math.min(9, events.size()); i++)
            log.addView(text(events.get(i), 12, MUTED, false));
        root.addView(log);
    }

    private void positionSummary(LinearLayout box, DexEngine.Position p, boolean compact) {
        box.addView(text(p.symbol + " / " + ("bsc".equals(p.chain) ? "BNB Chain" : "Solana"),
            15, CYAN, true));
        box.addView(text("Entry " + dollar(p.entryPrice) + "  |  Mark " + dollar(p.markPrice),
            13, WHITE, false));
        box.addView(text("P/L " + money(p.pnlUsd) + "  |  Target " + dollar(p.targetPrice)
            + "  |  Stop " + dollar(p.stopPrice), 13, p.pnlUsd >= 0 ? GREEN : RED, true));
        if (!compact)
            box.addView(text("Paper-only. No token purchased; no blockchain transaction exists.",
                12, AMBER, false));
    }

    private Bitmap qrCode(String text, int size) {
        try {
            com.google.zxing.qrcode.QRCodeWriter w = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix m =
                w.encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size);
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++)
                for (int y = 0; y < size; y++)
                    bmp.setPixel(x, y, m.get(x, y) ? Color.BLACK : Color.WHITE);
            return bmp;
        } catch (Exception e) { return null; }
    }

    private void startScanner() {
        if (store.panic) { toast("Clear Panic in Control first."); return; }
        store.engine.start();
        Intent svc = new Intent(this, DexBotService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
        render(false);
    }

    private void pauseScanner() {
        store.engine.stop("Scanner paused."); stopService(new Intent(this, DexBotService.class)); render(false);
    }

    private void panic() {
        store.engine.panic(); stopService(new Intent(this, DexBotService.class)); render(false);
    }

    private void createWallet() {
        new AlertDialog.Builder(this).setTitle("Create Nanu Bot Wallet")
            .setMessage("This creates a NEW separate wallet. It does NOT connect to your existing "
                + "Trust Wallet. Back up the recovery phrase offline before funding.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create", (d, w) -> {
                try {
                    BotWallet.Addresses a = BotWallet.create();
                    store.putMnemonic(a.mnemonic);
                    store.bscAddress = a.bsc; store.solanaAddress = a.solana;
                    store.walletCreatedAt = new java.text.SimpleDateFormat(
                        "yyyy-MM-dd HH:mm", Locale.US).format(new java.util.Date());
                    store.lastStatus = "Bot wallet created. Back up phrase before funding.";
                    store.save(); showBackup(); render(false);
                } catch (Throwable e) {
                    alert("Wallet Creation Failed",
                        "Could not initialize wallet library: " + readable(e));
                }
            }).show();
    }

    private void showBackup() {
        String words = store.getMnemonic();
        if (words.isEmpty()) { toast("No wallet backup available."); return; }
        alert("Bot Wallet Recovery Phrase",
            "Write these words offline in order. Do NOT screenshot, share, or paste them "
            + "into any website or app.\n\n" + words);
    }

    private void funding() {
        alert("Fund Nanu Bot Wallet",
            "BNB Chain:\n" + store.bscAddress
            + "\n\nSend BNB Chain assets only. Keep BNB for gas fees.\n\n"
            + "Solana:\n" + store.solanaAddress
            + "\n\nSend Solana assets only. Keep SOL for transaction fees.\n\n"
            + "Always start with a tiny test transfer first.");
    }

    private interface NS { void save(double v); }
    private void editNum(String title, double old, double min, double max, NS cb) {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setText(String.format(Locale.US, "%.2f", old)); et.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle(title).setView(et)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", (d, w) -> {
                try {
                    double v = Double.parseDouble(et.getText().toString().trim());
                    if (!DexSafetyPolicy.validAmount(v, min, max)) { toast("Value must be " + min + "–" + max); return; }
                    cb.save(v); store.save(); render(false);
                } catch (Exception ignored) { toast("Enter a valid number."); }
            }).show();
    }

    private void editStopTarget() {
        LinearLayout form = col(); form.setPadding(dp(20), dp(4), dp(20), dp(4));
        EditText stop = new EditText(this); stop.setHint("Stop loss %");
        stop.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        stop.setText(String.valueOf(store.stopLossPercent)); form.addView(stop);
        EditText tgt = new EditText(this); tgt.setHint("Take profit %");
        tgt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        tgt.setText(String.valueOf(store.takeProfitPercent)); form.addView(tgt);
        new AlertDialog.Builder(this).setTitle("Stop & Target").setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", (d, w) -> {
                try {
                    store.stopLossPercent  = Double.parseDouble(stop.getText().toString());
                    store.takeProfitPercent = Double.parseDouble(tgt.getText().toString());
                    store.save(); render(false);
                } catch (Exception ignored) { toast("Enter valid percentages."); }
            }).show();
    }

    private LinearLayout metric(String label, String value, String note, int color) {
        LinearLayout b = col(); b.setPadding(dp(10), dp(9), dp(10), dp(9));
        b.setBackground(background(CARD2, CYAN, 8));
        b.addView(text(label, 10, MUTED, true)); b.addView(text(value, 16, color, true));
        b.addView(text(note, 10, MUTED, false)); return b;
    }
    private void setting(LinearLayout p, String label, String value, View.OnClickListener l) {
        TextView v = command(label + "\n" + value, CARD2, l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56));
        lp.topMargin = dp(7); p.addView(v, lp);
    }
    private LinearLayout card() {
        LinearLayout b = col(); b.setPadding(dp(14), dp(14), dp(14), dp(14));
        b.setBackground(background(CARD, CYAN, 8)); return b;
    }
    private LinearLayout empty(String msg) {
        LinearLayout b = card(); b.addView(text(msg, 13, MUTED, false)); return b;
    }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private LinearLayout col() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private void gap(int value) { View v = new View(this); root.addView(v, new LinearLayout.LayoutParams(1, dp(value))); }
    private void gap(LinearLayout p, int value) { View v = new View(this); p.addView(v, new LinearLayout.LayoutParams(1, dp(value))); }
    private void space(LinearLayout row, int value) { View v = new View(this); row.addView(v, new LinearLayout.LayoutParams(dp(value), 1)); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value == null ? "" : value);
        v.setTextColor(color); v.setTextSize(size);
        v.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        v.setLineSpacing(dp(2), 1f); return v;
    }
    private TextView pill(String value, int color) {
        TextView v = text(value, 10, BG, true); v.setGravity(Gravity.CENTER);
        v.setPadding(dp(9), dp(5), dp(9), dp(5)); v.setBackground(background(color, color, 12)); return v;
    }
    private TextView button(String label, int fill, int ink, int size) {
        TextView v = text(label, size, ink, true); v.setGravity(Gravity.CENTER);
        v.setSingleLine(true); v.setBackground(background(fill, fill, 8)); return v;
    }
    private TextView command(String label, int color, View.OnClickListener l) {
        TextView v = text(label, 13, color == CARD2 ? WHITE : BG, true);
        v.setGravity(Gravity.CENTER); v.setBackground(background(color, color, 8));
        v.setOnClickListener(l); return v;
    }
    private GradientDrawable background(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill);
        d.setCornerRadius(dp(radius)); d.setStroke(dp(1), stroke); return d;
    }
    private void footer() {
        gap(16);
        TextView note = text("NANU AI  \u2022  BNB + SOL  \u2022  ML Evolution  \u2022  Paper Mode  \u2022  v10.0",
            10, MUTED, false);
        note.setGravity(Gravity.CENTER); root.addView(note);
    }
    private void alert(String title, String body) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("OK", null).show();
    }
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
    private static String readable(Throwable e) { String v = e.getMessage(); return v == null || v.trim().isEmpty() ? e.getClass().getSimpleName() : v; }
    private static String shortAddr(String v) { return v == null || v.length() < 14 ? v : v.substring(0, 7) + "..." + v.substring(v.length()-5); }
    private static String compact(double v) { return v >= 1_000d ? String.format(Locale.US, "%,.0f", v) : String.format(Locale.US, "%.2f", v); }
    private static String signed(double v)  { return String.format(Locale.US, "%+.2f", v); }
    private static String money(double v)   { return String.format(Locale.US, "$%,.2f", v); }
    private static String dollar(double v)  { return v >= 1d ? String.format(Locale.US, "$%.4f", v) : String.format(Locale.US, "$%.8f", v); }
}
