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
import android.widget.ImageView;
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
    boolean refreshLoopActive = false;
    boolean apiDoctorRunning = false;
    boolean telegramDoctorRunning = false;
    boolean pinSessionUnlocked = false;
    final Handler handler = new Handler(Looper.getMainLooper());
    final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!refreshLoopActive) return;
            if (store != null) store.engine.tick(false);
            render(false);
            handler.postDelayed(this, 3000);
        }
    };

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

    @Override protected void onResume() {
        super.onResume();
        if (!refreshLoopActive) {
            refreshLoopActive = true;
            handler.post(refresh);
        }
    }

    @Override protected void onPause() {
        refreshLoopActive = false;
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    int statusBarHeight() { int id = getResources().getIdentifier("status_bar_height", "dimen", "android"); return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24); }

    void render(boolean newScroll) {
        int oldY = scroll == null ? 0 : scroll.getScrollY();

        // Flicker fix: do not recreate the Activity content view on every timer tick.
        // The previous version called setContentView() every refresh, which made the
        // whole screen flash on some Android devices. Now the ScrollView is created
        // once and only its inner content is rebuilt.
        if (scroll == null || root == null) {
            scroll = new ScrollView(this);
            scroll.setFillViewport(false);
            scroll.setBackgroundColor(BG);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroll.setVerticalScrollBarEnabled(false);

            root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(16), statusBarHeight() + dp(14), dp(16), dp(24));
            scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
            setContentView(scroll);
        } else {
            root.removeAllViews();
        }

        buildHeader();
        buildMoodHero();
        buildTabs();
        if (activeTab == 0) buildBridge();
        else if (activeTab == 1) buildScanner();
        else if (activeTab == 2) buildBrain();
        else if (activeTab == 3) buildJournal();
        else buildSecurity();
        footer();
        if (!newScroll) scroll.post(() -> scroll.scrollTo(0, oldY));
    }

    void buildHeader() {
        LinearLayout row = row(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, 0, 0, dp(12));
        ImageView avatar = nanuAvatar(dp(58));
        LinearLayout.LayoutParams avp = new LinearLayout.LayoutParams(dp(58), dp(58)); avp.rightMargin = dp(12); row.addView(avatar, avp);
        LinearLayout titles = col();
        TextView title = tv("NANU", 34, WHITE, true); title.setLetterSpacing(.04f); titles.addView(title);
        TextView sub = tv("AI TRADING BOT", 15, CYAN, true); sub.setLetterSpacing(.22f); titles.addView(sub);
        row.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView status = pill(store.engine.running ? "ACTIVE" : "IDLE", store.engine.running ? GREEN : CYAN, 12); status.setMinWidth(dp(78)); row.addView(status);
        TextView settings = pill("⚙", CYAN, 21); settings.setPadding(dp(12), dp(9), dp(12), dp(9)); settings.setOnClickListener(v -> openSecurity());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(52), dp(52)); sp.leftMargin = dp(8); row.addView(settings, sp);
        root.addView(row);
    }

    ImageView nanuAvatar(int size) {
        ImageView img = new ImageView(this);
        img.setImageResource(getResources().getIdentifier("nanu_avatar", "drawable", getPackageName()));
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setPadding(dp(3), dp(3), dp(3), dp(3));
        img.setBackground(bg(Color.rgb(8, 18, 36), Color.rgb(130, 64, 255), 100));
        return img;
    }

    void openSecurity() {
        if (store.appPinEnabled && !pinSessionUnlocked) { promptPin(() -> { activeTab = 4; render(true); }); return; }
        activeTab = 4; render(true);
    }

    interface AfterPin { void ok(); }
    void promptPin(AfterPin cb) {
        input("Nanu App PIN", "Enter PIN", "", true, s -> {
            if (store.appPin == null || store.appPin.isEmpty() || store.appPin.equals(s.trim())) { pinSessionUnlocked = true; cb.ok(); }
            else toast("Wrong PIN");
        });
    }

    void buildSafetyDashboardCard() {
        LinearLayout box = cardBox();
        box.addView(section("SAFETY DASHBOARD"));
        box.addView(tv("API: " + (store.apiDoctorOkForCurrentMode() ? "PASS ✅" : "Not ready") + "  •  Telegram: " + (store.telegramAlertsEnabled ? "ON" : "OFF") + "  •  Profit Guard: " + (store.profitGuardEnabled ? "ON" : "OFF"), 12, store.apiDoctorOkForCurrentMode() ? GREEN : AMBER, true));
        box.addView(tv("Dry-run: " + (store.liveDryRunEnabled ? "ON" : "OFF") + "  •  Live Orders: MANUAL MICRO ONLY  •  Mode: " + store.mode.toUpperCase(Locale.US), 12, CYAN, true));
        box.addView(tv("Balance: " + store.lastBalanceSnapshot, 12, MUTED, false));
        box.addView(tv("Today: trades " + store.liveTradesToday + " / " + store.maxLiveTradesPerDay + "  •  dry-run previews " + store.dryRunPreviewsToday + "  •  profit target " + String.format(Locale.US, "%.2f", store.profitTargetUsdt) + " USDT", 12, MUTED, false));
        root.addView(box); addGap(12);
    }

    void buildMoodHero() {
        LinearLayout faceCard = cardBox();
        faceCard.setGravity(Gravity.CENTER_HORIZONTAL);
        faceCard.addView(label("NANU LIVE FACE ENGINE"));

        FrameLayout logoWrap = new FrameLayout(this);
        FaceLogoView face = new FaceLogoView(this);
        face.bind(store.engine);
        logoWrap.addView(face, new FrameLayout.LayoutParams(dp(174), dp(174), Gravity.CENTER));
        faceCard.addView(logoWrap, new LinearLayout.LayoutParams(-1, dp(188)));

        TextView moodText = tv("Expression: " + faceMoodLabel(), 16, faceMoodColor(), true);
        moodText.setGravity(Gravity.CENTER);
        faceCard.addView(moodText);
        TextView hint = tv("Face changes with P&L: calm, smile, big profit, sad, crying, panic.", 11, MUTED, false);
        hint.setGravity(Gravity.CENTER);
        faceCard.addView(hint);
        root.addView(faceCard);
        addGap(12);

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

    String faceMoodLabel() {
        if (store.engine.panic) return "PANIC / DANGER 😠";
        double pnl = store.engine.todayPnl;
        if (pnl > 120) return "BIG PROFIT / BIG SMILE 😄";
        if (pnl > 15) return "PROFIT / SMILE 🙂";
        if (pnl < -100) return "HEAVY LOSS / CRYING 😢";
        if (pnl < -15) return "LOSS / SAD 🙁";
        return "CALM / NEUTRAL 😐";
    }

    int faceMoodColor() {
        if (store.engine.panic) return RED;
        double pnl = store.engine.todayPnl;
        if (pnl > 15) return GREEN;
        if (pnl < -100) return RED;
        if (pnl < -15) return AMBER;
        return CYAN;
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
            t.setOnClickListener(v -> { if (idx == 4) openSecurity(); else { activeTab = idx; render(true); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1); lp.setMargins(dp(3), 0, dp(3), 0); tabs.addView(t, lp);
        }
        root.addView(tabs); addGap(14);
    }

    void buildBridge() {
        buildSafetyDashboardCard();
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

        buildOrderSafetyCard();
        buildV60LiveScalpingCard();
        buildProfitGuardCard();
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
        box.addView(tv("Trading mode, API Doctor, trusted IP helper, key permissions, Telegram Doctor, final live checklist and safety lock.", 13, MUTED, false)); addGap(box, 14);
        box.addView(section("TRADING MODE")); addGap(box, 8);
        LinearLayout m1 = row(); m1.addView(modeButton("PAPER", "Safe simulation", "paper"), new LinearLayout.LayoutParams(0, dp(76), 1)); LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, dp(76), 1); mlp.leftMargin = dp(10); m1.addView(modeButton("DEMO", "Exchange practice", "demo"), mlp); box.addView(m1); addGap(box, 10);
        LinearLayout m2 = row(); m2.addView(modeButton("TESTNET", "API order test", "testnet"), new LinearLayout.LayoutParams(0, dp(76), 1)); LinearLayout.LayoutParams mlp2 = new LinearLayout.LayoutParams(0, dp(76), 1); mlp2.leftMargin = dp(10); m2.addView(modeButton("LIVE", "Locked checklist", "live"), mlp2); box.addView(m2);
        box.addView(tv("Live requires API key + API health check + risk limits + typing UNLOCK LIVE.", 12, AMBER, false)); addGap(box, 18);

        box.addView(section("BINANCE API")); addGap(box, 8);
        box.addView(actionButton(store.apiKey.isEmpty() ? "Add Binance API Key" : "API Key Saved • Tap to Update", CYAN, v -> input("Binance API Key", "Paste key", store.apiKey, false, s -> { store.apiKey = s.trim(); store.liveUnlocked=false; store.clearApiDoctorStatus("API key changed. Run API Doctor again for the selected mode."); render(true); })), new LinearLayout.LayoutParams(-1, dp(54))); addGap(box, 8);
        box.addView(actionButton(store.apiSecret.isEmpty() ? "Add API Secret" : "API Secret Saved • Tap to Update", CYAN, v -> input("Binance API Secret", "Paste secret", store.apiSecret, true, s -> { store.apiSecret = s.trim(); store.liveUnlocked=false; store.clearApiDoctorStatus("API secret changed. Run API Doctor again for the selected mode."); render(true); })), new LinearLayout.LayoutParams(-1, dp(54))); addGap(box, 8);
        box.addView(actionButton(apiDoctorRunning ? "API Doctor Running..." : "Run API Doctor", apiDoctorRunning ? AMBER : GREEN, v -> testApi()), new LinearLayout.LayoutParams(-1, dp(56))); addGap(box, 8);
        LinearLayout apiTools = row();
        apiTools.addView(actionButton("Show My Public IP", CYAN, v -> showPublicIp()), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams apiToolsLp = new LinearLayout.LayoutParams(0, dp(52), 1); apiToolsLp.leftMargin = dp(10);
        apiTools.addView(actionButton("API Mode Guide", AMBER, v -> alert("API Mode Guide", apiModeGuide())), apiToolsLp);
        box.addView(apiTools); addGap(box, 10);
        buildApiDoctorStatus(box);
        addGap(box, 8);
        buildLiveChecklist(box);
        addGap(box, 8);
        box.addView(actionButton("Confirm API Withdrawals OFF", store.withdrawalPermissionConfirmedOff ? GREEN : AMBER, v -> { store.withdrawalPermissionConfirmedOff = !store.withdrawalPermissionConfirmedOff; store.liveUnlocked = false; store.save(); toast(store.withdrawalPermissionConfirmedOff ? "Withdrawal permission confirmed OFF" : "Withdrawal confirmation removed"); render(false); }), new LinearLayout.LayoutParams(-1, dp(52)));
        box.addView(tv("Use this only after checking Binance API Management and confirming Enable Withdrawals is OFF for this API key.", 11, AMBER, false));
        addGap(box, 8);
        box.addView(actionButton("Unlock LIVE Control Gate", RED, v -> unlockLive()), new LinearLayout.LayoutParams(-1, dp(54)));
        box.addView(tv("LIVE can be selected for API Doctor. v6.0 allows manual confirmed micro orders only after all gates pass.", 12, AMBER, false));
        addGap(box, 18);

        box.addView(section("RISK SHIELD")); addGap(box, 8);
        box.addView(tv("Risk/trade: " + store.riskPerTrade + "%  •  Daily loss limit: " + store.dailyLossLimit + "%  •  Max open trades: " + store.maxOpenTrades, 13, MUTED, false));
        box.addView(tv("Stop-loss: " + store.stopLoss + "%  •  Take-profit: " + store.takeProfit + "%  •  Trailing: " + store.trailingStop + "%", 13, MUTED, false)); addGap(box, 8);
        buildOrderSafetySettings(box);
        buildV60Settings(box);
        box.addView(actionButton("Reset Paper Wallet", AMBER, v -> { store.engine.resetPaper(); render(true); }), new LinearLayout.LayoutParams(-1, dp(52))); addGap(box, 18);

        buildProfitGuardSettings(box);
        addGap(box, 10);
        buildFaceTestControls(box);

        box.addView(section("TELEGRAM / ALERTS")); addGap(box, 8);
        box.addView(tv("Paste Telegram bot token + chat ID, then run Telegram Doctor. Nanu sends alerts on Start, Stop, Panic, Profit Guard and API warnings.", 12, MUTED, false)); addGap(box, 8);
        LinearLayout tele = row();
        tele.addView(actionButton(store.telegramToken.isEmpty() ? "Bot Token" : "Bot Token Saved", CYAN, v -> input("Telegram Bot Token", "Paste token from BotFather", store.telegramToken, false, t -> { store.telegramToken=t.trim(); store.telegramDoctorOk=false; store.save(); render(true); })), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams chat = new LinearLayout.LayoutParams(0, dp(52), 1); chat.leftMargin=dp(10);
        tele.addView(actionButton(store.telegramChatId.isEmpty() ? "Chat ID" : "Chat ID Saved", CYAN, v -> input("Telegram Chat ID", "Paste your chat id", store.telegramChatId, false, cid -> { store.telegramChatId=cid.trim(); store.telegramDoctorOk=false; store.save(); render(true); })), chat);
        box.addView(tele); addGap(box, 8);
        box.addView(actionButton(telegramDoctorRunning ? "Telegram Doctor Running..." : "Run Telegram Doctor / Test Message", telegramDoctorRunning ? AMBER : GREEN, v -> testTelegram()), new LinearLayout.LayoutParams(-1, dp(54)));
        box.addView(tv("Telegram Doctor status: " + (store.telegramDoctorOk ? "PASS ✅" : "Not passed / not tested"), 12, store.telegramDoctorOk ? GREEN : AMBER, true));
        addGap(box, 8);
        buildTelegramControls(box);
        box.addView(actionButton("Test Phone Alert / Long Sound", AMBER, v -> { store.triggerAlert("Nanu Phone Alert Test", "Phone notification, sound and vibration test from Nanu.", true, "general"); toast("Phone alert test sent"); }), new LinearLayout.LayoutParams(-1, dp(52)));
        addGap(box, 10);
        buildAppLockControls(box);
        addGap(box, 10);
        box.addView(actionButton("Export Safety Report", CYAN, v -> alert("Nanu Safety Report", safetyReport())), new LinearLayout.LayoutParams(-1, dp(52)));
        root.addView(box);
    }


    void buildFaceTestControls(LinearLayout box) {
        box.addView(section("NANU FACE TEST")); addGap(box, 8);
        box.addView(tv("Use this to check the live face expression on the Bridge screen. It changes by P&L and Panic state.", 11, MUTED, false)); addGap(box, 8);
        LinearLayout r1 = row();
        r1.addView(actionButton("Calm Face", CYAN, v -> { store.engine.panic=false; store.engine.todayPnl=0; store.engine.marketMood="CALM"; store.engine.moodConfidence=51; activeTab=0; render(true); }), new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, dp(48), 1); lp1.leftMargin=dp(8);
        r1.addView(actionButton("Profit Smile", GREEN, v -> { store.engine.panic=false; store.engine.todayPnl=55; store.engine.marketMood="PROFIT"; store.engine.moodConfidence=72; activeTab=0; render(true); }), lp1);
        box.addView(r1); addGap(box, 8);
        LinearLayout r2 = row();
        r2.addView(actionButton("Big Smile", GREEN, v -> { store.engine.panic=false; store.engine.todayPnl=155; store.engine.marketMood="BIG PROFIT"; store.engine.moodConfidence=92; activeTab=0; render(true); }), new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, dp(48), 1); lp2.leftMargin=dp(8);
        r2.addView(actionButton("Sad / Loss", AMBER, v -> { store.engine.panic=false; store.engine.todayPnl=-45; store.engine.marketMood="LOSS"; store.engine.moodConfidence=28; activeTab=0; render(true); }), lp2);
        box.addView(r2); addGap(box, 8);
        box.addView(actionButton("Panic / Danger Face", RED, v -> { store.engine.panic=true; store.engine.todayPnl=-120; store.engine.marketMood="PANIC"; store.engine.moodConfidence=0; activeTab=0; render(true); }), new LinearLayout.LayoutParams(-1, dp(48)));
        addGap(box, 14);
    }

    void buildTelegramControls(LinearLayout box) {
        LinearLayout master = row();
        master.addView(actionButton("Telegram Alerts: " + (store.telegramAlertsEnabled ? "ON" : "OFF"), store.telegramAlertsEnabled ? GREEN : AMBER, v -> { store.telegramAlertsEnabled = !store.telegramAlertsEnabled; store.save(); render(false); }), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(0, dp(52), 1); qlp.leftMargin = dp(10);
        master.addView(actionButton("Quiet Mode: " + (store.telegramQuietMode ? "ON" : "OFF"), store.telegramQuietMode ? GREEN : CYAN, v -> { store.telegramQuietMode = !store.telegramQuietMode; store.save(); render(false); }), qlp);
        box.addView(master); addGap(box, 8);
        box.addView(tv("When Telegram is OFF, phone sound/vibration still works and token/chat ID stay saved.", 11, MUTED, false));
        LinearLayout c1 = row();
        c1.addView(toggleSmall("Start/Stop", store.telegramAlertStartStop, v -> { store.telegramAlertStartStop=!store.telegramAlertStartStop; store.save(); render(false); }), new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams c1lp = new LinearLayout.LayoutParams(0, dp(48), 1); c1lp.leftMargin = dp(8);
        c1.addView(toggleSmall("Panic", store.telegramAlertPanic, v -> { store.telegramAlertPanic=!store.telegramAlertPanic; store.save(); render(false); }), c1lp);
        box.addView(c1); addGap(box, 8);
        LinearLayout c2 = row();
        c2.addView(toggleSmall("Profit", store.telegramAlertProfit, v -> { store.telegramAlertProfit=!store.telegramAlertProfit; store.save(); render(false); }), new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams c2lp = new LinearLayout.LayoutParams(0, dp(48), 1); c2lp.leftMargin = dp(8);
        c2.addView(toggleSmall("API", store.telegramAlertApi, v -> { store.telegramAlertApi=!store.telegramAlertApi; store.save(); render(false); }), c2lp);
        box.addView(c2); addGap(box, 8);
        LinearLayout c3 = row();
        c3.addView(toggleSmall("Dry-run", store.telegramAlertDryRun, v -> { store.telegramAlertDryRun=!store.telegramAlertDryRun; store.save(); render(false); }), new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams c3lp = new LinearLayout.LayoutParams(0, dp(48), 1); c3lp.leftMargin = dp(8);
        c3.addView(toggleSmall("Live", store.telegramAlertLive, v -> { store.telegramAlertLive=!store.telegramAlertLive; store.save(); render(false); }), c3lp);
        box.addView(c3); addGap(box, 8);
    }

    TextView toggleSmall(String name, boolean on, View.OnClickListener l) {
        TextView t = tv(name + ": " + (on ? "ON" : "OFF"), 12, on ? GREEN : MUTED, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(bg(on ? Color.rgb(8, 52, 34) : CARD2, on ? GREEN : Color.rgb(32,72,91), 14));
        t.setOnClickListener(l);
        return t;
    }

    void buildAppLockControls(LinearLayout box) {
        box.addView(section("APP LOCK / PRIVACY")); addGap(box, 8);
        LinearLayout r = row();
        r.addView(actionButton("PIN Lock: " + (store.appPinEnabled ? "ON" : "OFF"), store.appPinEnabled ? GREEN : CYAN, v -> {
            if (!store.appPinEnabled && (store.appPin == null || store.appPin.isEmpty())) { input("Set Nanu PIN", "Enter new PIN", "", true, s -> { if (s.trim().length() < 4) toast("Use at least 4 digits/characters"); else { store.appPin=s.trim(); store.appPinEnabled=true; pinSessionUnlocked=true; store.save(); render(false); } }); }
            else { store.appPinEnabled = !store.appPinEnabled; if (!store.appPinEnabled) pinSessionUnlocked=false; store.save(); render(false); }
        }), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1); lp.leftMargin = dp(10);
        r.addView(actionButton("Change PIN", AMBER, v -> input("Change Nanu PIN", "Enter new PIN", "", true, s -> { if (s.trim().length() < 4) toast("Use at least 4 digits/characters"); else { store.appPin=s.trim(); store.appPinEnabled=true; pinSessionUnlocked=true; store.save(); toast("PIN saved"); render(false); } })), lp);
        box.addView(r);
        box.addView(tv("API key, API secret and Telegram token are never printed in Safety Report.", 11, MUTED, false));
    }


    void buildApiDoctorStatus(LinearLayout box) {
        LinearLayout status = cardMini();
        status.setOrientation(LinearLayout.VERTICAL);
        status.addView(section("API DOCTOR STATUS"));
        boolean sameMode = store.mode.equals(store.lastApiMode);
        status.addView(tv("Last tested mode: " + (store.lastApiMode.isEmpty() ? "Not tested" : store.lastApiMode.toUpperCase(Locale.US)), 12, MUTED, false));
        status.addView(tv("Last HTTP: " + (store.lastApiHttpCode == 0 ? "Not tested" : String.valueOf(store.lastApiHttpCode)), 12, MUTED, false));
        status.addView(tv("Private API: " + (sameMode && store.lastApiPrivateOk ? "OK" : "Not OK / Run Doctor for current mode"), 12, sameMode && store.lastApiPrivateOk ? GREEN : AMBER, true));
        status.addView(tv("Spot trading permission: " + (sameMode && store.lastApiCanTrade ? "OK" : "Blocked / Read-only / Not checked"), 12, sameMode && store.lastApiCanTrade ? GREEN : RED, true));
        status.addView(tv("Current public IP: " + (store.lastPublicIp.isEmpty() ? "Not checked" : store.lastPublicIp), 12, MUTED, false));
        status.addView(tv("Account withdraw ability: " + (sameMode && store.lastApiAccountCanWithdraw ? "ON / account-level flag" : "OFF or not checked"), 12, sameMode && store.lastApiAccountCanWithdraw ? AMBER : MUTED, false));
        status.addView(tv("Manual API-key withdrawal confirmation: " + (store.withdrawalPermissionConfirmedOff ? "OFF confirmed" : "Required before live unlock"), 12, store.withdrawalPermissionConfirmedOff ? GREEN : RED, true));
        status.addView(tv(store.lastApiDiagnosis, 12, store.lastApiCanTrade ? GREEN : AMBER, false));
        status.addView(tv("Full auto stays locked. Manual micro orders require API Doctor + Telegram Doctor + Profit Guard + Panic test + withdrawal OFF confirmation.", 12, AMBER, false));
        box.addView(status);
    }

    void buildLiveChecklist(LinearLayout box) {
        LinearLayout list = cardMini();
        list.setOrientation(LinearLayout.VERTICAL);
        list.addView(section("FINAL LIVE SAFETY CHECKLIST"));
        boolean live = "live".equals(store.mode);
        boolean api = live && store.apiDoctorOkForCurrentMode();
        boolean trade = live && store.apiTradingOkForCurrentMode();
        boolean notif = notificationReady();
        list.addView(checkLine("LIVE mode selected", live));
        list.addView(checkLine("Private API OK", api));
        list.addView(checkLine("Spot trading permission OK", trade));
        list.addView(checkLine("API-key withdrawals manually confirmed OFF", store.withdrawalPermissionConfirmedOff));
        list.addView(checkLine("Telegram Doctor PASS", store.telegramDoctorOk));
        list.addView(checkLine("Phone notification/sound ready", notif));
        list.addView(checkLine("Profit Guard ON", store.profitGuardEnabled));
        list.addView(checkLine("Daily loss limit set", store.dailyLossLimit > 0));
        list.addView(checkLine("Panic button tested", store.panicButtonTested));
        list.addView(checkLine("Controlled live dry-run ON", store.liveDryRunEnabled));
        list.addView(checkLine("Order amount >= min notional", store.liveDryRunOrderUsdt >= store.minOrderNotionalUsdt));
        list.addView(checkLine("Max trades/day guard set", store.maxLiveTradesPerDay > 0));
        list.addView(checkLine("Order cooldown set", store.orderCooldownSeconds >= 10));
        list.addView(tv("v6.0 allows dry-run plus manual confirmed micro orders. Full auto remains locked behind proof testing.", 11, AMBER, false));
        box.addView(list);
    }

    TextView checkLine(String label, boolean ok) {
        return tv((ok ? "✅ " : "❌ ") + label, 12, ok ? GREEN : AMBER, true);
    }

    boolean notificationReady() {
        return !store.phoneNotifications || Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    String apiModeGuide() {
        return "Paper:\nNo API key. Safe internal simulation.\n\n" +
                "Demo:\nUse Binance Demo Trading API key only. Do not paste live key.\n\n" +
                "Testnet:\nUse Binance Spot Testnet API key only. Do not paste live key.\n\n" +
                "Live:\nUse real Binance API key. Add trusted IP first, enable Spot & Margin & Stock Trading only, keep Withdrawals OFF.\n\n" +
                "Trusted IP meaning:\nThe IP must be the public IP of the device/server that sends API requests. If your mobile internet IP changes, update Binance trusted IP again.\n\n" +
                "Professional setup later:\nPhone app as control panel + VPS/static IP as trading engine.\n\nLive unlock checklist:\nAPI Doctor PASS + Telegram Doctor PASS + Profit Guard ON + Panic tested + manual withdrawal OFF confirmation.\n\nv6.0 adds controlled live scalping: dry-run preview, manual confirmed micro orders, compliance guard, rate-limit lock, journal and backup tools.";
    }


    void buildOrderSafetyCard() {
        LinearLayout box = cardBox();
        LinearLayout head = row(); head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(section("ORDER SAFETY ENGINE"), new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(tv(store.liveDryRunEnabled ? "DRY-RUN" : "OFF", 13, store.liveDryRunEnabled ? GREEN : AMBER, true));
        box.addView(head); addGap(box, 8);
        box.addView(tv("v6.0 previews live orders, checks safety rules, and can send manual confirmed micro Binance orders only when armed.", 12, MUTED, false));
        box.addView(tv("Preview amount: " + String.format(Locale.US, "%.2f", store.liveDryRunOrderUsdt) + " USDT  •  Min notional: " + String.format(Locale.US, "%.2f", store.minOrderNotionalUsdt) + " USDT", 12, MUTED, false));
        box.addView(tv("Cooldown: " + store.orderCooldownSeconds + " sec  •  Max live trades/day: " + store.maxLiveTradesPerDay + "  •  Dry-run passes: " + store.liveDryRunPassCount, 12, MUTED, false));
        box.addView(tv("Open dry-run trades: " + store.liveDryRunOpenTrades + " / " + Math.max(1, store.maxOpenTrades) + "  •  Preview state can be reset safely", 12, store.liveDryRunOpenTrades == 0 ? GREEN : AMBER, true));
        if (!store.lastOrderSymbol.isEmpty()) {
            box.addView(tv("Last preview: " + store.lastOrderSymbol + "  •  " + (store.lastOrderSafetyPass ? "PASS" : "BLOCKED"), 12, store.lastOrderSafetyPass ? GREEN : AMBER, true));
            String preview = store.lastOrderPreview == null ? "" : store.lastOrderPreview;
            if (preview.length() > 220) preview = preview.substring(0, 220) + "...";
            box.addView(tv(preview, 11, MUTED, false));
        }
        addGap(box, 10);
        LinearLayout buttons = row();
        buttons.addView(actionButton("Run Dry-Run Preview", GREEN, v -> runDryRunPreview()), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1); lp.leftMargin = dp(10);
        buttons.addView(actionButton("Order Safety Guide", CYAN, v -> alert("Order Safety Guide", orderSafetyGuide())), lp);
        box.addView(buttons);
        root.addView(box); addGap(12);
    }

    void buildOrderSafetySettings(LinearLayout box) {
        box.addView(section("ORDER SAFETY ENGINE")); addGap(box, 8);
        box.addView(tv("Controlled live dry-run, manual confirmed micro order, minimum order checks, cooldown and max trades/day guard.", 12, MUTED, false)); addGap(box, 8);
        LinearLayout r1 = row();
        r1.addView(actionButton("Dry-Run: " + (store.liveDryRunEnabled ? "ON" : "OFF"), store.liveDryRunEnabled ? GREEN : AMBER, v -> { store.liveDryRunEnabled = !store.liveDryRunEnabled; store.liveUnlocked = false; store.save(); render(true); }), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, dp(52), 1); rlp.leftMargin = dp(10);
        r1.addView(actionButton("Preview: " + String.format(Locale.US, "%.2f", store.liveDryRunOrderUsdt) + " USDT", CYAN, v -> input("Dry-Run Order Amount", "Example: 5 or 10", String.format(Locale.US, "%.2f", store.liveDryRunOrderUsdt), false, val -> { try { store.liveDryRunOrderUsdt = Math.max(1.0, Double.parseDouble(val.trim())); store.liveUnlocked=false; store.save(); render(true); } catch(Exception e) { toast("Enter valid amount"); } })), rlp);
        box.addView(r1); addGap(box, 8);
        LinearLayout r2 = row();
        r2.addView(actionButton("Cooldown: " + store.orderCooldownSeconds + "s", CYAN, v -> input("Order Cooldown Seconds", "Minimum 10", String.valueOf(store.orderCooldownSeconds), false, val -> { try { store.orderCooldownSeconds = Math.max(10, Integer.parseInt(val.trim())); store.liveUnlocked=false; store.save(); render(true); } catch(Exception e) { toast("Enter valid seconds"); } })), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(0, dp(52), 1); r2lp.leftMargin = dp(10);
        r2.addView(actionButton("Max/day: " + store.maxLiveTradesPerDay, CYAN, v -> input("Max Live Trades Per Day", "Example: 1 or 3", String.valueOf(store.maxLiveTradesPerDay), false, val -> { try { store.maxLiveTradesPerDay = Math.max(1, Math.min(20, Integer.parseInt(val.trim()))); store.liveUnlocked=false; store.save(); render(true); } catch(Exception e) { toast("Enter valid count"); } })), r2lp);
        box.addView(r2); addGap(box, 8);
        LinearLayout r3 = row();
        r3.addView(actionButton("Slippage: " + String.format(Locale.US, "%.2f", store.slippageLimitPct) + "%", CYAN, v -> input("Slippage Limit %", "Example: 0.25", String.format(Locale.US, "%.2f", store.slippageLimitPct), false, val -> { try { store.slippageLimitPct = Math.max(0.01, Math.min(5.0, Double.parseDouble(val.trim()))); store.liveUnlocked=false; store.save(); render(true); } catch(Exception e) { toast("Enter valid percent"); } })), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r3lp = new LinearLayout.LayoutParams(0, dp(52), 1); r3lp.leftMargin = dp(10);
        r3.addView(actionButton("Run Dry-Run Preview", GREEN, v -> runDryRunPreview()), r3lp);
        box.addView(r3); addGap(box, 8);
        LinearLayout r4 = row();
        r4.addView(actionButton("Open Limit: " + Math.max(1, store.maxOpenTrades), CYAN, v -> input("Max Open Trades", "Use 1 for safest dry-run", String.valueOf(store.maxOpenTrades), false, val -> { try { store.maxOpenTrades = Math.max(1, Math.min(10, Integer.parseInt(val.trim()))); store.liveUnlocked=false; store.save(); render(true); } catch(Exception e) { toast("Enter valid count"); } })), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r4lp = new LinearLayout.LayoutParams(0, dp(52), 1); r4lp.leftMargin = dp(10);
        r4.addView(actionButton("Reset Safety State", AMBER, v -> { store.resetOrderSafetyState("Manual reset from Security"); toast("Order Safety state reset"); render(true); }), r4lp);
        box.addView(r4); addGap(box, 8);
        box.addView(tv("Manual confirmation remains required. Keep Binance Test Order ON first. Real order mode requires ARM REAL MICRO and resets after every real order.", 11, AMBER, false)); addGap(box, 18);
    }

    void runDryRunPreview() {
        OrderSafetyEngine.Preview p = OrderSafetyEngine.buildDryRunPreview(store);
        store.dryRunPreviewsToday++; store.save();
        if (p.pass) store.triggerAlert("Nanu Dry-Run PASS", "Order preview passed for " + p.symbol + ". Preview only; no order sent.", false, "dryrun");
        else store.triggerAlert("Nanu Dry-Run Blocked", "Order preview was blocked by safety checks. Preview only; no order sent.", true, "dryrun");
        alert("Live Order Safety Preview", p.report);
        render(false);
    }

    String orderSafetyGuide() {
        return "Controlled Live Dry-Run:\nNanu uses real mode/risk settings to preview an order, but v6.0 does not send real Binance orders.\n\n" +
                "Checks included:\n• LIVE selected\n• API Doctor private OK\n• Spot trading permission OK\n• Withdrawals confirmed OFF\n• Profit Guard ON\n• Telegram Doctor PASS\n• Panic tested\n• Minimum notional\n• Quantity rounding\n• Max trades/day\n• Order cooldown\n• Slippage limit\n\n" +
                "Purpose:\nThis proves the order safety path before adding real order execution in a later audited version.";
    }


    void buildV60LiveScalpingCard() {
        LinearLayout box = cardBox();
        LinearLayout head = row(); head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(section("v6.0 CONTROLLED LIVE SCALPING"), new LinearLayout.LayoutParams(0, -2, 1));
        String state = store.liveOrderTestMode ? "TEST ORDER" : (store.liveRealOrderArmed ? "REAL ARMED" : "REAL LOCKED");
        head.addView(tv(state, 12, store.liveOrderTestMode ? CYAN : (store.liveRealOrderArmed ? RED : AMBER), true));
        box.addView(head); addGap(box, 8);
        box.addView(tv("Manual micro order only. No 100% profit promise. No order spam. Full auto locked until proof gate passes.", 12, AMBER, false));
        box.addView(tv("Micro BUY: " + String.format(Locale.US, "%.2f USDT", store.microLiveOrderUsdt) + "  •  Test order: " + (store.liveOrderTestMode ? "ON" : "OFF") + "  •  Real armed: " + (store.liveRealOrderArmed ? "YES" : "NO"), 12, MUTED, false));
        box.addView(tv("Compliance: " + (store.complianceGuardEnabled ? "ON" : "OFF") + "  •  Rate lock: " + (store.binanceRateLimitLock ? "ACTIVE" : "Clear") + "  •  Full auto: " + (store.fullAutoLocked ? "LOCKED" : "UNLOCKED"), 12, store.binanceRateLimitLock ? RED : GREEN, true));
        if (store.lastBinanceStatusCode != 0) box.addView(tv("Last Binance HTTP: " + store.lastBinanceStatusCode + " • " + store.lastBinanceErrorDoctor, 11, store.lastBinanceStatusCode >= 400 ? RED : GREEN, false));
        addGap(box, 10);
        LinearLayout r1 = row();
        r1.addView(actionButton("Manual Micro BUY", GREEN, v -> confirmMicroBuy()), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1); lp.leftMargin = dp(10);
        r1.addView(actionButton("Manual SELL / Close", AMBER, v -> confirmMicroSell()), lp);
        box.addView(r1); addGap(box, 8);
        LinearLayout r2 = row();
        r2.addView(actionButton("Compliance Report", CYAN, v -> alert("Binance Compliance Guard", complianceReport())), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, dp(52), 1); lp2.leftMargin = dp(10);
        r2.addView(actionButton("Error Doctor", store.binanceRateLimitLock ? RED : CYAN, v -> alert("Nanu Error Doctor", errorDoctorReport())), lp2);
        box.addView(r2); addGap(box, 8);
        box.addView(actionButton("Last Live/Test Order Report", CYAN, v -> alert("Live Order Report", store.lastLiveOrderReport)), new LinearLayout.LayoutParams(-1, dp(52)));
        root.addView(box); addGap(12);
    }

    void buildV60Settings(LinearLayout box) {
        box.addView(section("v6.0 CONTROLLED LIVE SCALPING")); addGap(box, 8);
        box.addView(tv("Manual confirmed micro order, compliance guard, semi-auto approval, full-auto proof gate, backup/restore and Error Doctor.", 12, MUTED, false)); addGap(box, 8);
        LinearLayout r1 = row();
        r1.addView(actionButton("Compliance Guard: " + (store.complianceGuardEnabled ? "ON" : "OFF"), store.complianceGuardEnabled ? GREEN : RED, v -> { store.complianceGuardEnabled = !store.complianceGuardEnabled; store.liveRealOrderArmed = false; store.save(); render(true); }), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r1lp = new LinearLayout.LayoutParams(0, dp(52), 1); r1lp.leftMargin = dp(10);
        r1.addView(actionButton("Test Order: " + (store.liveOrderTestMode ? "ON" : "OFF"), store.liveOrderTestMode ? GREEN : RED, v -> toggleTestOrderMode()), r1lp);
        box.addView(r1); addGap(box, 8);

        LinearLayout r2 = row();
        r2.addView(actionButton("Micro BUY: " + String.format(Locale.US, "%.2f", store.microLiveOrderUsdt) + " USDT", CYAN, v -> input("Micro Live BUY Amount", "Safest first: 5 USDT", String.format(Locale.US, "%.2f", store.microLiveOrderUsdt), false, val -> { try { store.microLiveOrderUsdt = Math.max(5.0, Math.min(50.0, Double.parseDouble(val.trim()))); store.liveDryRunOrderUsdt = store.microLiveOrderUsdt; store.liveRealOrderArmed=false; store.save(); render(true); } catch(Exception e){ toast("Enter valid amount"); } })), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(0, dp(52), 1); r2lp.leftMargin = dp(10);
        r2.addView(actionButton(store.liveRealOrderArmed ? "Real Order ARMED" : "Arm Real Micro", store.liveRealOrderArmed ? RED : AMBER, v -> armRealMicro()), r2lp);
        box.addView(r2); addGap(box, 8);

        LinearLayout r3 = row();
        r3.addView(actionButton("Semi-Auto Approval: " + (store.semiAutoApprovalRequired ? "ON" : "OFF"), store.semiAutoApprovalRequired ? GREEN : AMBER, v -> { store.semiAutoApprovalRequired=!store.semiAutoApprovalRequired; store.save(); render(true); }), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r3lp = new LinearLayout.LayoutParams(0, dp(52), 1); r3lp.leftMargin = dp(10);
        r3.addView(actionButton("Full Auto: " + (store.fullAutoLocked ? "LOCKED" : "UNLOCKED"), store.fullAutoLocked ? AMBER : RED, v -> tryFullAutoUnlock()), r3lp);
        box.addView(r3); addGap(box, 8);

        LinearLayout r4 = row();
        r4.addView(actionButton("Export Backup", CYAN, v -> alert("Nanu Backup", exportBackupText())), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r4lp = new LinearLayout.LayoutParams(0, dp(52), 1); r4lp.leftMargin = dp(10);
        r4.addView(actionButton("Reset v6 Safety", AMBER, v -> { store.resetV60SafetyState("Manual v6 reset"); toast("v6 safety state reset"); render(true); }), r4lp);
        box.addView(r4); addGap(box, 8);

        LinearLayout r5 = row();
        r5.addView(actionButton("Send /status to Telegram", CYAN, v -> { store.triggerAlert("Nanu Status", telegramStatusText(), false, "daily"); toast("Status sent if Telegram alerts are ON"); }), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r5lp = new LinearLayout.LayoutParams(0, dp(52), 1); r5lp.leftMargin = dp(10);
        r5.addView(actionButton("Error Doctor", store.binanceRateLimitLock ? RED : CYAN, v -> alert("Nanu Error Doctor", errorDoctorReport())), r5lp);
        box.addView(r5); addGap(box, 8);
        box.addView(tv("Telegram safe commands supported as templates: /status, /pause, /resume, /panic, /report, /balance. Real BUY/SELL from Telegram stays disabled in v6.0.", 11, AMBER, false)); addGap(box, 18);
    }

    void toggleTestOrderMode() {
        if (store.liveOrderTestMode) {
            input("Disable Binance Test Order Mode", "Type ALLOW REAL", "", false, s -> {
                if ("ALLOW REAL".equals(s.trim())) { store.liveOrderTestMode=false; store.liveRealOrderArmed=false; store.save(); toast("Real order mode selected but still NOT armed"); }
                else toast("Still in test order mode");
                render(true);
            });
        } else {
            store.liveOrderTestMode=true; store.liveRealOrderArmed=false; store.save(); toast("Binance test order mode ON"); render(true);
        }
    }

    void armRealMicro() {
        if (store.liveOrderTestMode) { toast("Turn Test Order OFF first only when ready"); return; }
        String missing = manualOrderMissingItems();
        if (!missing.isEmpty()) { alert("Cannot Arm Real Micro Order", missing); return; }
        input("ARM REAL MICRO ORDER", "Type ARM REAL MICRO", "", false, s -> {
            if ("ARM REAL MICRO".equals(s.trim())) { store.liveRealOrderArmed = true; store.save(); store.triggerAlert("Nanu Real Micro Armed", "One manual confirmed real micro order is armed. It will reset after one order.", true, "live"); toast("Real micro order armed"); }
            else toast("Not armed");
            render(true);
        });
    }

    void confirmMicroBuy() {
        String missingBefore = manualOrderMissingItems();
        if (!missingBefore.isEmpty()) { alert("Micro BUY Blocked", missingBefore); render(false); return; }
        OrderSafetyEngine.Preview p = OrderSafetyEngine.buildDryRunPreview(store);
        store.dryRunPreviewsToday++;
        if (!p.pass) { store.save(); alert("Micro BUY Blocked", p.report); render(false); return; }
        // The preview sets a cooldown to prevent spam. For this immediate manual-confirmed
        // order, clear it once so the final order call can run, then BinanceClient sets
        // a fresh cooldown after the test/real order response.
        store.orderCooldownUntilMs = 0L;
        store.save();
        input("Confirm Micro BUY", "Type CONFIRM BUY", "", false, s -> {
            if (!"CONFIRM BUY".equals(s.trim())) { toast("BUY cancelled"); return; }
            toast(store.liveOrderTestMode ? "Sending Binance test order..." : "Sending REAL micro BUY...");
            BinanceClient.placeMarketOrder(store, p.symbol, "BUY", store.microLiveOrderUsdt, result -> runOnUiThread(() -> { alert("Nanu Micro BUY Result", result); render(false); }));
        });
    }

    void confirmMicroSell() {
        String sym = store.lastOrderSymbol == null || store.lastOrderSymbol.isEmpty() ? (store.watchlist.isEmpty() ? "BTCUSDT" : store.watchlist.get(0)) : store.lastOrderSymbol;
        String missing = manualOrderMissingItems();
        if (!missing.isEmpty()) { alert("Manual SELL Blocked", missing); return; }
        input("Manual SELL Quantity for " + sym, "Example BTC: 0.0001 / SOL: 0.01", "", false, qty -> {
            try {
                double amount = Double.parseDouble(qty.trim());
                if (amount <= 0) { toast("Enter quantity above zero"); return; }
                input("Confirm Manual SELL", "Type CONFIRM SELL", "", false, s -> {
                    if (!"CONFIRM SELL".equals(s.trim())) { toast("SELL cancelled"); return; }
                    BinanceClient.placeMarketOrder(store, sym, "SELL", amount, result -> runOnUiThread(() -> { alert("Nanu SELL Result", result); render(false); }));
                });
            } catch(Exception e) { toast("Enter valid quantity"); }
        });
    }

    String manualOrderMissingItems() {
        StringBuilder m = new StringBuilder();
        if (!"live".equals(store.mode)) m.append("• Select LIVE mode.\n");
        if (!store.liveUnlocked) m.append("• Unlock LIVE gate after checklist.\n");
        if (!store.apiTradingOkForCurrentMode()) m.append("• Run LIVE API Doctor and confirm Spot trading OK.\n");
        if (!store.withdrawalPermissionConfirmedOff) m.append("• Confirm API-key withdrawals are OFF.\n");
        if (!store.complianceGuardEnabled) m.append("• Turn Compliance Guard ON.\n");
        if (store.binanceRateLimitLock) m.append("• Binance rate-limit lock is active. Wait/check Binance, then reset v6 safety.\n");
        if (!store.profitGuardEnabled) m.append("• Enable Profit Guard.\n");
        if (!store.panicButtonTested) m.append("• Test Panic button once.\n");
        if (store.engine.panic) m.append("• Clear Panic state by stopping/resetting safely.\n");
        if (store.liveTradesToday >= Math.max(1, store.maxLiveTradesPerDay)) m.append("• Max trades/day reached.\n");
        if (System.currentTimeMillis() < store.orderCooldownUntilMs) m.append("• Order cooldown active.\n");
        if (!store.liveOrderTestMode && !store.liveRealOrderArmed) m.append("• For REAL order, type ARM REAL MICRO first.\n");
        if (m.length() == 0) return "";
        return "Nanu blocked manual micro order until these are fixed:\n\n" + m.toString();
    }

    void tryFullAutoUnlock() {
        if (store.fullAutoLocked) {
            StringBuilder m = new StringBuilder();
            if (store.liveDryRunPassCount < store.dryRunProofRequired) m.append("• Need ").append(store.dryRunProofRequired).append(" dry-run PASS signals. Current: ").append(store.liveDryRunPassCount).append("\n");
            if (store.consecutiveLosses > 0) m.append("• Consecutive loss counter must be 0. Current: ").append(store.consecutiveLosses).append("\n");
            if (store.binanceRateLimitLock) m.append("• Rate-limit lock must be clear.\n");
            if (!store.telegramDoctorOk) m.append("• Telegram Doctor must pass.\n");
            if (m.length() > 0) { alert("Full Auto Locked", "Full auto remains locked for safety.\n\n" + m.toString()); return; }
            input("Unlock Full Auto Gate", "Type UNLOCK FULL AUTO", "", false, s -> { if ("UNLOCK FULL AUTO".equals(s.trim())) { store.fullAutoLocked=false; store.save(); toast("Full auto gate unlocked, but Nanu still uses risk governor"); } else toast("Full auto remains locked"); render(true); });
        } else {
            store.fullAutoLocked=true; store.save(); toast("Full auto locked"); render(true);
        }
    }

    String complianceReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Nanu v6.0 Binance Compliance Guard\n\n");
        sb.append("Normal low-frequency scalping assistant: allowed only through official Binance API.\n");
        sb.append("Forbidden behavior blocked by design:\n");
        sb.append("• no order spam\n• no wash trading\n• no fake volume\n• no spoofing/cancel spam\n• no withdrawal permission\n• no bypassing API limits\n\n");
        sb.append("Guard status: ").append(store.complianceGuardEnabled ? "ON" : "OFF").append("\n");
        sb.append("Order cooldown: ").append(store.orderCooldownSeconds).append(" seconds\n");
        sb.append("Max live trades/day: ").append(store.maxLiveTradesPerDay).append("\n");
        sb.append("Max open trades: ").append(store.maxOpenTrades).append("\n");
        sb.append("Micro order USDT: ").append(String.format(Locale.US, "%.2f", store.microLiveOrderUsdt)).append("\n");
        sb.append("Binance test order mode: ").append(store.liveOrderTestMode).append("\n");
        sb.append("Rate-limit lock: ").append(store.binanceRateLimitLock).append("\n");
        sb.append("Last Binance HTTP: ").append(store.lastBinanceStatusCode).append("\n");
        sb.append("Full auto proof gate: ").append(store.liveDryRunPassCount).append(" / ").append(store.dryRunProofRequired).append(" dry-run passes\n\n");
        sb.append("Reminder: Nanu cannot guarantee profit and cannot guarantee zero bugs. Keep trade size small.");
        String r = sb.toString(); store.lastComplianceReport = r; store.save(); return r;
    }

    String errorDoctorReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Nanu v6.0 Error Doctor\n\n");
        sb.append("Last Binance HTTP: ").append(store.lastBinanceStatusCode == 0 ? "None" : String.valueOf(store.lastBinanceStatusCode)).append("\n");
        sb.append("Diagnosis: ").append(store.lastBinanceErrorDoctor).append("\n\n");
        sb.append(BinanceClient.explainBinanceCode(store.lastBinanceStatusCode, store.lastBinanceErrorDoctor)).append("\n\n");
        sb.append("Common fixes:\n• 400: check min notional/step size/symbol.\n• 401: check API key, secret, timestamp, trusted IP.\n• 403: check permissions/restrictions.\n• 418/429: stop, wait, and avoid repeated retries.\n");
        return sb.toString();
    }

    String exportBackupText() {
        StringBuilder sb = new StringBuilder();
        sb.append("NANU v6.0 SAFE BACKUP - NO SECRETS\n");
        sb.append("mode=").append(store.mode).append('\n');
        sb.append("watchlist=").append(store.watchlist).append('\n');
        sb.append("profitGuardEnabled=").append(store.profitGuardEnabled).append('\n');
        sb.append("profitTargetUsdt=").append(store.profitTargetUsdt).append('\n');
        sb.append("riskPerTrade=").append(store.riskPerTrade).append('\n');
        sb.append("dailyLossLimit=").append(store.dailyLossLimit).append('\n');
        sb.append("stopLoss=").append(store.stopLoss).append('\n');
        sb.append("takeProfit=").append(store.takeProfit).append('\n');
        sb.append("trailingStop=").append(store.trailingStop).append('\n');
        sb.append("maxOpenTrades=").append(store.maxOpenTrades).append('\n');
        sb.append("maxLiveTradesPerDay=").append(store.maxLiveTradesPerDay).append('\n');
        sb.append("orderCooldownSeconds=").append(store.orderCooldownSeconds).append('\n');
        sb.append("microLiveOrderUsdt=").append(store.microLiveOrderUsdt).append('\n');
        sb.append("telegramAlertsEnabled=").append(store.telegramAlertsEnabled).append('\n');
        sb.append("API_KEY=HIDDEN\nAPI_SECRET=HIDDEN\nTELEGRAM_TOKEN=HIDDEN\n");
        String r = sb.toString(); store.lastBackupText = r; store.save(); return r;
    }

    String telegramStatusText() {
        return "Nanu v6.0 Status\nMode: " + store.mode.toUpperCase(Locale.US) + "\nRunning: " + store.engine.running + "\nP&L: " + formatMoney(store.engine.todayPnl) + " USDT\nLive gate: " + store.liveUnlocked + "\nTest order mode: " + store.liveOrderTestMode + "\nReal armed: " + store.liveRealOrderArmed + "\nRate lock: " + store.binanceRateLimitLock;
    }

    void buildProfitGuardCard() {
        LinearLayout box = cardBox();
        LinearLayout head = row(); head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(section("PROFIT GUARD"), new LinearLayout.LayoutParams(0, -2, 1));
        int statusColor = store.profitGuardEnabled ? GREEN : MUTED;
        head.addView(tv(store.profitGuardEnabled ? "ARMED" : "OFF", 13, statusColor, true));
        box.addView(head); addGap(box, 8);
        box.addView(tv("Target: " + String.format(Locale.US, "%.2f", store.profitTargetUsdt) + " USDT  •  Today: " + formatMoney(store.engine.realizedPnl) + " USDT", 13, store.engine.realizedPnl >= 0 ? GREEN : RED, true));
        box.addView(tv("When target hits, Nanu stops automatically and sends notification + long sound.", 12, MUTED, false));
        box.addView(tv("Duplicate P&L Guard: " + (store.duplicateProfitGuardEnabled ? ("ON / " + store.sameProfitRepeats + " of " + store.duplicateProfitRepeatCount) : "OFF"), 12, store.duplicateProfitGuardEnabled ? AMBER : MUTED, false));
        addGap(box, 10);
        LinearLayout buttons = row();
        buttons.addView(actionButton(store.profitGuardEnabled ? "Disable Guard" : "Enable Guard", store.profitGuardEnabled ? AMBER : GREEN, v -> { store.profitGuardEnabled = !store.profitGuardEnabled; store.resetGuardSession(); store.save(); toast("Profit Guard " + (store.profitGuardEnabled ? "enabled" : "disabled")); render(false); }), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1); lp.leftMargin = dp(10);
        buttons.addView(actionButton("Set Target", CYAN, v -> input("Daily Profit Target", "Example: 50 or 100", String.format(Locale.US, "%.2f", store.profitTargetUsdt), false, s -> { try { store.profitTargetUsdt = Math.max(0.01, Double.parseDouble(s.trim())); store.profitGuardEnabled = true; store.resetGuardSession(); store.save(); toast("Target saved"); render(true); } catch(Exception e) { toast("Enter valid amount"); } })), lp);
        box.addView(buttons);
        root.addView(box); addGap(12);
    }

    void buildProfitGuardSettings(LinearLayout box) {
        box.addView(section("PROFIT GUARD & ALERTS")); addGap(box, 8);
        box.addView(tv("Daily profit target, repeated-profit detector, phone notification, long sound and vibration.", 13, MUTED, false)); addGap(box, 10);
        LinearLayout quick = row();
        quick.addView(actionButton("50", CYAN, v -> setProfitTarget(50)), new LinearLayout.LayoutParams(0, dp(50), 1));
        LinearLayout.LayoutParams q1 = new LinearLayout.LayoutParams(0, dp(50), 1); q1.leftMargin=dp(8); quick.addView(actionButton("100", CYAN, v -> setProfitTarget(100)), q1);
        LinearLayout.LayoutParams q2 = new LinearLayout.LayoutParams(0, dp(50), 1); q2.leftMargin=dp(8); quick.addView(actionButton("150", CYAN, v -> setProfitTarget(150)), q2);
        box.addView(quick); addGap(box, 8);
        box.addView(actionButton("Custom Profit Target: " + String.format(Locale.US, "%.2f USDT", store.profitTargetUsdt), CYAN, v -> input("Custom Profit Target", "Any amount in USDT", String.format(Locale.US, "%.2f", store.profitTargetUsdt), false, s -> { try { setProfitTarget(Double.parseDouble(s.trim())); } catch(Exception e) { toast("Enter valid amount"); } })), new LinearLayout.LayoutParams(-1, dp(52))); addGap(box, 8);
        LinearLayout toggles = row();
        toggles.addView(actionButton("Profit Target: " + (store.profitGuardEnabled ? "ON" : "OFF"), store.profitGuardEnabled ? GREEN : AMBER, v -> { store.profitGuardEnabled = !store.profitGuardEnabled; store.resetGuardSession(); store.save(); render(true); }), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams tg = new LinearLayout.LayoutParams(0, dp(52), 1); tg.leftMargin=dp(10);
        toggles.addView(actionButton("Repeat Guard: " + (store.duplicateProfitGuardEnabled ? "ON" : "OFF"), store.duplicateProfitGuardEnabled ? GREEN : AMBER, v -> { store.duplicateProfitGuardEnabled = !store.duplicateProfitGuardEnabled; store.resetGuardSession(); store.save(); render(true); }), tg);
        box.addView(toggles); addGap(box, 8);
        LinearLayout alertRow = row();
        alertRow.addView(actionButton("Repeat Count: " + store.duplicateProfitRepeatCount, CYAN, v -> input("Repeat Count", "3, 5 or 10", String.valueOf(store.duplicateProfitRepeatCount), false, s -> { try { store.duplicateProfitRepeatCount = Math.max(2, Math.min(20, Integer.parseInt(s.trim()))); store.resetGuardSession(); store.save(); render(true); } catch(Exception e) { toast("Enter valid count"); } })), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams ar = new LinearLayout.LayoutParams(0, dp(52), 1); ar.leftMargin=dp(10);
        alertRow.addView(actionButton("Test Long Alert", RED, v -> store.triggerAlert("Nanu Test Alert", "Long sound + notification test. Profit Guard can stop the bot when target is hit.", true)), ar);
        box.addView(alertRow); addGap(box, 8);
        LinearLayout soundRow = row();
        soundRow.addView(actionButton("Sound: " + (store.soundAlerts ? "ON" : "OFF"), store.soundAlerts ? GREEN : AMBER, v -> { store.soundAlerts = !store.soundAlerts; store.save(); render(true); }), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams nr = new LinearLayout.LayoutParams(0, dp(52), 1); nr.leftMargin=dp(10);
        soundRow.addView(actionButton("Notify: " + (store.phoneNotifications ? "ON" : "OFF"), store.phoneNotifications ? GREEN : AMBER, v -> { store.phoneNotifications = !store.phoneNotifications; store.save(); render(true); }), nr);
        box.addView(soundRow); addGap(box, 10);
        box.addView(tv("Best default: Profit Guard ON only when you set a target. Repeat Guard ON at 3 checks. Nanu stops new trades and alerts you.", 12, AMBER, false)); addGap(box, 18);
    }

    void setProfitTarget(double amount) {
        store.profitTargetUsdt = Math.max(0.01, amount);
        store.profitGuardEnabled = true;
        store.resetGuardSession();
        store.save();
        toast("Profit target set: " + String.format(Locale.US, "%.2f USDT", store.profitTargetUsdt));
        render(true);
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
        if ("live".equals(store.mode) && !store.liveUnlocked) { toast("Live dry-run gate locked. Use Unlock LIVE after all checks pass."); activeTab=4; render(true); return; }
        if ("live".equals(store.mode) && !store.apiTradingOkForCurrentMode()) { toast("Live auto trading blocked: run API Doctor in LIVE mode first."); activeTab=4; render(true); return; }
        store.engine.start();
        store.triggerAlert("Nanu Started", "Bot started in " + store.mode.toUpperCase(Locale.US) + ("live".equals(store.mode) ? " controlled live mode. Full auto locked; manual micro orders require confirmation." : " mode."), false, "startstop");
        try { Intent i = new Intent(this, NanuBotService.class); if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); } catch (Exception ignored) {}
        toast("Nanu started"); render(false);
    }
    void stopBot() { store.engine.stop(); stopService(new Intent(this, NanuBotService.class)); store.resetOrderSafetyState("Stop button"); store.triggerAlert("Nanu Stopped", "Bot stopped safely.", false, "startstop"); toast("Nanu stopped"); render(false); }
    void panic() { store.engine.panicClose(); store.panicButtonTested = true; store.save(); stopService(new Intent(this, NanuBotService.class)); store.triggerAlert("Nanu PANIC", "Panic close activated. Check Binance manually before restarting.", true, "panic"); toast("Panic close activated"); render(false); }

    void setMode(String m) {
        if ("live".equals(m)) {
            store.mode = "live";
            store.liveUnlocked = false;
            store.clearApiDoctorStatus("LIVE selected for API Doctor. Run API Doctor now. Auto trading is still locked.");
            toast("LIVE selected for API Doctor only. Auto trading is still locked.");
            render(true);
            return;
        }
        store.mode = m;
        store.liveUnlocked = false;
        store.clearApiDoctorStatus("Mode changed. Run API Doctor again for " + m.toUpperCase(Locale.US) + ".");
        toast("Mode: " + m.toUpperCase(Locale.US));
        render(true);
    }
    void unlockLive() {
        if (!"live".equals(store.mode)) { toast("Select LIVE mode first for API Doctor."); return; }
        if (store.apiKey.isEmpty() || store.apiSecret.isEmpty()) { toast("Add Binance API key and secret first."); return; }
        String missing = liveMissingItems();
        if (!missing.isEmpty()) {
            alert("Live Dry-Run Unlock Blocked", "Nanu will not unlock the live control gate yet. Fix these items first:\n\n" + missing + "\nThis is intentional safety protection for your Binance account.");
            return;
        }
        input("Unlock LIVE Control Gate", "Type UNLOCK LIVE", "", false, s -> { if ("UNLOCK LIVE".equals(s.trim())) { store.liveUnlocked=true; store.mode="live"; store.save(); store.triggerAlert("Nanu Live Dry-Run Unlocked", "LIVE control gate unlocked. Full auto is still locked; manual micro orders require separate confirmation.", true, "live"); toast("Live control gate unlocked"); } else toast("Live not unlocked"); render(true); });
    }

    String liveMissingItems() {
        StringBuilder m = new StringBuilder();
        if (!store.apiDoctorOkForCurrentMode()) m.append("• Run API Doctor in LIVE mode; Private API must be OK.\n");
        if (!store.apiTradingOkForCurrentMode()) m.append("• Spot trading permission must be OK.\n");
        if (!store.withdrawalPermissionConfirmedOff) m.append("• Confirm Enable Withdrawals is OFF in Binance API Management.\n");
        if (!store.telegramDoctorOk) m.append("• Run Telegram Doctor successfully.\n");
        if (!notificationReady()) m.append("• Allow Android notification permission or turn phone notifications OFF.\n");
        if (!store.profitGuardEnabled) m.append("• Enable Profit Guard.\n");
        if (store.dailyLossLimit <= 0) m.append("• Set Daily Loss Limit.\n");
        if (!store.panicButtonTested) m.append("• Test Panic button once in paper/safe state.\n");
        if (!store.liveDryRunEnabled) m.append("• Keep Controlled Live Dry-Run ON for v6.0.\n");
        if (store.liveDryRunOrderUsdt < store.minOrderNotionalUsdt) m.append("• Dry-run order amount must be above minimum notional.\n");
        if (store.orderCooldownSeconds < 10) m.append("• Order cooldown must be at least 10 seconds.\n");
        if (store.maxLiveTradesPerDay <= 0) m.append("• Max live trades/day must be set.\n");
        return m.toString();
    }

    void testApi() {
        if (apiDoctorRunning) { toast("API Doctor already running"); return; }
        apiDoctorRunning = true;
        toast("API Doctor running for " + store.mode.toUpperCase(Locale.US) + "...");
        render(false);
        BinanceClient.testApi(store, result -> runOnUiThread(() -> {
            apiDoctorRunning = false;
            if (!store.apiDoctorOkForCurrentMode()) store.triggerAlert("Nanu API Doctor Failed", "API Doctor did not pass for " + store.mode.toUpperCase(Locale.US) + ". Check app diagnosis.", true, "api");
            alert("Nanu API Doctor", result);
            render(false);
        }));
    }
    void showPublicIp() { toast("Checking public IP..."); BinanceClient.getPublicIp(store, result -> runOnUiThread(() -> { alert("Trusted IP Helper", result); render(false); })); }
    void testTelegram() {
        if (telegramDoctorRunning) { toast("Telegram Doctor already running"); return; }
        telegramDoctorRunning = true;
        toast("Sending Telegram test message...");
        render(false);
        TelegramClient.test(store, result -> runOnUiThread(() -> { telegramDoctorRunning = false; alert("Telegram Doctor", result); render(false); }));
    }


    String safetyReport() {
        StringBuilder sb = new StringBuilder();

        sb.append("NANU AI TRADING BOT v6.0\n");
        sb.append("Safety Report\n");
        sb.append("Generated: ").append(System.currentTimeMillis()).append("\n\n");

        sb.append("=== MODE ===\n");
        sb.append("Current mode: ").append(store.mode.toUpperCase(Locale.US)).append("\n");
        sb.append("Bot running: ").append(store.engine.running).append("\n");
        sb.append("Panic state: ").append(store.engine.panic).append("\n");
        sb.append("Live control gate unlocked: ").append(store.liveUnlocked).append("\n");
        sb.append("Real live orders: BLOCKED in v6.0\n\n");

        sb.append("=== NANU FACE ENGINE ===\n");
        sb.append("Face mood: ").append(faceMoodLabel()).append("\n");
        sb.append("Today P&L: ").append(String.format(Locale.US, "%+.2f", store.engine.todayPnl)).append(" USDT\n");
        sb.append("Market mood: ").append(store.engine.marketMood).append("\n\n");

        sb.append("=== API DOCTOR ===\n");
        sb.append("API Doctor OK for current mode: ").append(store.apiDoctorOkForCurrentMode()).append("\n");
        sb.append("Last API mode: ").append(store.lastApiMode).append("\n");
        sb.append("HTTP code: ").append(store.lastApiHttpCode).append("\n");
        sb.append("Private API OK: ").append(store.lastApiPrivateOk).append("\n");
        sb.append("Spot trading OK: ").append(store.lastApiCanTrade).append("\n");
        sb.append("Account withdraw ability flag: ").append(store.lastApiAccountCanWithdraw).append("\n");
        sb.append("Manual withdrawals OFF confirmed: ").append(store.withdrawalPermissionConfirmedOff).append("\n");
        sb.append("Public IP: ").append(store.lastPublicIp == null ? "" : store.lastPublicIp).append("\n\n");

        sb.append("=== TELEGRAM ===\n");
        sb.append("Telegram Doctor OK: ").append(store.telegramDoctorOk).append("\n");
        sb.append("Telegram Alerts Enabled: ").append(store.telegramAlertsEnabled).append("\n");
        sb.append("Quiet Mode: ").append(store.telegramQuietMode).append("\n");
        sb.append("Start/Stop alerts: ").append(store.telegramAlertStartStop).append("\n");
        sb.append("Profit alerts: ").append(store.telegramAlertProfit).append("\n");
        sb.append("Panic alerts: ").append(store.telegramAlertPanic).append("\n");
        sb.append("API alerts: ").append(store.telegramAlertApi).append("\n");
        sb.append("Dry-run alerts: ").append(store.telegramAlertDryRun).append("\n");
        sb.append("Live alerts: ").append(store.telegramAlertLive).append("\n\n");

        sb.append("=== PROFIT GUARD ===\n");
        sb.append("Profit Guard Enabled: ").append(store.profitGuardEnabled).append("\n");
        sb.append("Profit Target USDT: ").append(store.profitTargetUsdt).append("\n");
        sb.append("Repeated Profit Detector: ").append(store.duplicateProfitGuardEnabled).append("\n");
        sb.append("Repeated Profit Count: ").append(store.sameProfitRepeats).append(" / ").append(store.duplicateProfitRepeatCount).append("\n");
        sb.append("Phone Notifications: ").append(store.phoneNotifications).append("\n");
        sb.append("Long Sound Alerts: ").append(store.longSoundAlerts).append("\n\n");

        sb.append("=== RISK / SAFETY ENGINE ===\n");
        sb.append("Risk per trade: ").append(store.riskPerTrade).append("%\n");
        sb.append("Daily loss limit: ").append(store.dailyLossLimit).append("%\n");
        sb.append("Stop-loss: ").append(store.stopLoss).append("%\n");
        sb.append("Take-profit: ").append(store.takeProfit).append("%\n");
        sb.append("Trailing stop: ").append(store.trailingStop).append("%\n");
        sb.append("Max open trades: ").append(store.maxOpenTrades).append("\n");
        sb.append("Max live trades/day: ").append(store.maxLiveTradesPerDay).append("\n");
        sb.append("Order cooldown seconds: ").append(store.orderCooldownSeconds).append("\n");
        sb.append("Panic button tested: ").append(store.panicButtonTested).append("\n\n");

        sb.append("=== DRY-RUN ORDER PREVIEW ===\n");
        sb.append("Controlled Live Dry-Run: ").append(store.liveDryRunEnabled).append("\n");
        sb.append("Dry-run order USDT: ").append(store.liveDryRunOrderUsdt).append("\n");
        sb.append("Min notional USDT: ").append(store.minOrderNotionalUsdt).append("\n");
        sb.append("Slippage limit: ").append(store.slippageLimitPct).append("%\n");
        sb.append("Open dry-run trades: ").append(store.liveDryRunOpenTrades).append(" / ").append(Math.max(1, store.maxOpenTrades)).append("\n");
        sb.append("Dry-run previews today: ").append(store.dryRunPreviewsToday).append("\n");
        sb.append("Last preview symbol: ").append(store.lastOrderSymbol).append("\n");
        sb.append("Last preview passed: ").append(store.lastOrderSafetyPass).append("\n");
        sb.append("Last preview:\n").append(store.lastOrderPreview).append("\n\n");

        sb.append("=== BALANCE SNAPSHOT ===\n");
        sb.append(store.lastBalanceSnapshot == null ? "Balance not synced yet." : store.lastBalanceSnapshot).append("\n\n");

        sb.append("=== v6.0 CONTROLLED LIVE ===\n");
        sb.append("Compliance Guard: ").append(store.complianceGuardEnabled).append("\n");
        sb.append("Binance Test Order Mode: ").append(store.liveOrderTestMode).append("\n");
        sb.append("Real Micro Order Armed: ").append(store.liveRealOrderArmed).append("\n");
        sb.append("Semi-Auto Approval Required: ").append(store.semiAutoApprovalRequired).append("\n");
        sb.append("Full Auto Locked: ").append(store.fullAutoLocked).append("\n");
        sb.append("Micro Order USDT: ").append(store.microLiveOrderUsdt).append("\n");
        sb.append("Rate Limit Lock: ").append(store.binanceRateLimitLock).append("\n");
        sb.append("Last Binance HTTP: ").append(store.lastBinanceStatusCode).append("\n");
        sb.append("Last Binance Error Doctor: ").append(store.lastBinanceErrorDoctor).append("\n");
        sb.append("Last Live/Test Order Report:\n").append(store.lastLiveOrderReport).append("\n\n");

        sb.append("=== PRIVACY ===\n");
        sb.append("API key: HIDDEN\n");
        sb.append("API secret: HIDDEN\n");
        sb.append("Telegram token: HIDDEN\n");
        sb.append("PIN lock enabled: ").append(store.appPinEnabled).append("\n\n");

        sb.append("Report note: This report does not contain secrets. It is only for checking Nanu safety status.");

        String report = sb.toString();
        store.lastSafetyReport = report;
        store.save();
        return report;
    }

    String developerReport() { return "Nanu AI Trading Bot v6.0\nMode: " + store.mode + "\nRunning: " + store.engine.running + "\nPanic: " + store.engine.panic + "\nCoin mode: " + (store.autoCoinMode?"Auto":"Manual") + "\nWatchlist: " + store.watchlist + "\nPaper UI trades: " + store.engine.trades.size() + "\nOpen dry-run trades: " + store.liveDryRunOpenTrades + "/" + Math.max(1, store.maxOpenTrades) + "\nLive unlocked: " + store.liveUnlocked + "\nProfit Guard: " + store.profitGuardEnabled + " / target " + store.profitTargetUsdt + " USDT" + "\nDuplicate Guard: " + store.duplicateProfitGuardEnabled + " / repeats " + store.sameProfitRepeats + "/" + store.duplicateProfitRepeatCount + "\nAPI last mode: " + store.lastApiMode + "\nAPI HTTP: " + store.lastApiHttpCode + "\nAPI private OK: " + store.lastApiPrivateOk + "\nAPI can trade: " + store.lastApiCanTrade + "\nAccount withdraw ability: " + store.lastApiAccountCanWithdraw + "\nManual withdrawals OFF confirmed: " + store.withdrawalPermissionConfirmedOff + "\nTelegram Doctor: " + store.telegramDoctorOk + "\nPanic tested: " + store.panicButtonTested + "\nPublic IP: " + store.lastPublicIp + "\nAPI diagnosis: " + store.lastApiDiagnosis; }

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
    void alert(String title, String msg) {
        TextView body = tv(msg, 14, WHITE, false);
        body.setPadding(dp(18), dp(8), dp(18), dp(8));
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        new AlertDialog.Builder(this).setTitle(title).setView(sv).setPositiveButton("OK", null).show();
    }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    void footer() { addGap(14); TextView f = tv("Nanu AI Trading Bot v6.0 • Controlled Live Scalping • Manual Micro Orders • Compliance Guard", 11, MUTED, false); f.setGravity(Gravity.CENTER); root.addView(f); }
    public static class SpaceView extends View { public SpaceView(android.content.Context c) { super(c); } }
}
