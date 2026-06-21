package com.nanu.aitradingbot;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
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
    boolean portfolioSyncRunning = false;
    boolean pinSessionUnlocked = false;
    static final int ALERT_SOUND_PICKER_REQUEST = 401;
    final Handler handler = new Handler(Looper.getMainLooper());
    final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!refreshLoopActive) return;
            if (store != null) store.engine.tick(false);
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
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(b);
        try { if (getActionBar() != null) getActionBar().hide(); } catch (Exception ignored) {}
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
        if (store != null && store.appPinEnabled && !pinSessionUnlocked) {
            render(true);
            return;
        }
        if (!refreshLoopActive) {
            refreshLoopActive = true;
            handler.post(refresh);
        }
        if (store != null && (!store.appPinEnabled || pinSessionUnlocked) && "live".equals(store.mode) && store.apiDoctorOkForCurrentMode() && !portfolioSyncRunning && System.currentTimeMillis() - store.lastPortfolioSyncMs > 5 * 60 * 1000L) {
            syncPortfolio(true);
        }
    }

    @Override protected void onPause() {
        refreshLoopActive = false;
        handler.removeCallbacks(refresh);
        if (store != null && store.appPinEnabled) pinSessionUnlocked = false;
        super.onPause();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != ALERT_SOUND_PICKER_REQUEST || resultCode != RESULT_OK) return;
        Uri selected = data == null ? null : data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        store.alertSoundUri = selected == null ? "" : selected.toString();
        store.save();
        toast(selected == null ? "Nanu alert sound reset to device default" : "Nanu alert sound selected");
        render(false);
    }

    int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    int statusBarHeight() { int id = getResources().getIdentifier("status_bar_height", "dimen", "android"); return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24); }

    void render(boolean newScroll) {
        store.ensureDailySafetyWindow();
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
            scroll.setClipToPadding(true);
            scroll.setPadding(0, statusBarHeight() + dp(8), 0, 0);

            root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(16), dp(8), dp(16), dp(24));
            scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
            setContentView(scroll);
        } else {
            root.removeAllViews();
        }

        if (store.appPinEnabled && !pinSessionUnlocked) {
            buildLockedScreen();
        } else {
            buildHeader();
            buildExecutionOverview();
            buildTabs();
            if (activeTab == 0) buildBridge();
            else if (activeTab == 1) buildScanner();
            else if (activeTab == 2) buildBrain();
            else if (activeTab == 3) buildJournal();
            else buildSecurity();
            footer();
        }
        if (!newScroll) scroll.post(() -> scroll.scrollTo(0, oldY));
    }

    void buildLockedScreen() {
        addGap(48);
        LinearLayout box = cardBox();
        box.addView(screenTitle("NANU LOCKED"));
        box.addView(tv("Enter your app PIN to view Binance balances, API controls, signals, and order controls.", 14, MUTED, false));
        addGap(box, 18);
        box.addView(actionButton("Unlock", CYAN, v -> promptPin(() -> render(true))), new LinearLayout.LayoutParams(-1, dp(54)));
        root.addView(box);
    }

    void buildHeader() {
        LinearLayout row = row(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, 0, 0, dp(10));
        ImageView avatar = nanuAvatar(dp(46));
        LinearLayout.LayoutParams avp = new LinearLayout.LayoutParams(dp(46), dp(46)); avp.rightMargin = dp(10); row.addView(avatar, avp);
        LinearLayout titles = col();
        TextView title = tv("NANU AI TRADING BOT", 20, WHITE, true); title.setLetterSpacing(0f); title.setSingleLine(true); titles.addView(title);
        LinearLayout meta = row(); meta.setGravity(Gravity.CENTER_VERTICAL);
        TextView sub = tv("AUTOMATIC SPOT", 12, CYAN, true); sub.setLetterSpacing(0f); sub.setSingleLine(true); meta.addView(sub, new LinearLayout.LayoutParams(0, -2, 1));
        String state = store.runtimeState();
        TextView status = "PANIC".equals(state) ? panicStatusPill() : ("ACTIVE".equals(state) ? activeStatusPill() : pill("IDLE", MUTED, 11));
        status.setMinWidth(dp(64)); meta.addView(status, new LinearLayout.LayoutParams(-2, -2));
        titles.addView(meta, new LinearLayout.LayoutParams(-1, -2));
        row.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView settings = pill("⚙", CYAN, 19); settings.setPadding(dp(10), dp(7), dp(10), dp(7)); settings.setOnClickListener(v -> openSecurity());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(46), dp(46)); sp.leftMargin = dp(6); row.addView(settings, sp);
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
            if (store.verifyAppPin(s)) {
                pinSessionUnlocked = true;
                if (!refreshLoopActive) { refreshLoopActive = true; handler.post(refresh); }
                cb.ok();
            }
            else toast("Wrong PIN");
        });
    }

    TextView activeStatusPill() {
        TextView t = pill("ACTIVE", GREEN, 11);
        t.setTextColor(BG);
        t.setBackground(bg(GREEN, Color.WHITE, 10));
        t.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        t.setShadowLayer(dp(10), 0, 0, GREEN);
        AlphaAnimation glow = new AlphaAnimation(0.62f, 1.0f);
        glow.setDuration(900L);
        glow.setRepeatMode(Animation.REVERSE);
        glow.setRepeatCount(Animation.INFINITE);
        t.startAnimation(glow);
        return t;
    }

    TextView panicStatusPill() {
        TextView t = pill("PANIC", RED, 11);
        t.setTextColor(WHITE);
        t.setBackground(bg(RED, Color.WHITE, 10));
        return t;
    }

    void buildSafetyDashboardCard() {
        LinearLayout box = cardBox();
        box.addView(section("SAFETY STATUS"));
        box.addView(tv("Runtime: " + store.runtimeState() + " • Device heartbeat: " + heartbeatAgeLabel(), 12, "ACTIVE".equals(store.runtimeState()) ? GREEN : (store.panicLatched() ? RED : AMBER), true));
        box.addView(tv("Spot trading • LIVE gate " + (store.liveUnlocked ? "unlocked" : "locked") + " • API Doctor " + (store.apiDoctorOkForCurrentMode() ? "passed" : "required"), 12, store.liveUnlocked ? GREEN : AMBER, true));
        box.addView(tv("Real BUY is manual, single-use armed, and attempts Binance-side OCO target/stop protection immediately after a fill.", 12, CYAN, false));
        box.addView(tv("Four approved pairs: BTCUSDT, ETHUSDT, BNBUSDT, SOLUSDT • Daily cap: " + store.liveTradesToday + " / " + store.maxLiveTradesPerDay, 12, MUTED, false));
        if (store.hasPendingProtectionCheck()) {
            box.addView(tv("ACTION REQUIRED: inspect Binance for " + store.pendingProtectionSymbol + ". New BUYs stay blocked until the protection check is acknowledged in Control.", 12, RED, true));
        }
        root.addView(box); addGap(12);
    }

    void buildExecutionOverview() {
        LinearLayout box = cardBox();
        LinearLayout head = row(); head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(section("SPOT CONTROL"), new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(tv("MANUAL", 12, CYAN, true));
        box.addView(head); addGap(box, 8);
        box.addView(big(store.portfolioSyncOk ? "BINANCE BALANCE READY" : "SYNC BINANCE FIRST", store.portfolioSyncOk ? GREEN : AMBER, 20));
        box.addView(tv("No server, URL, or executor token is required. Your encrypted API credentials stay on this device.", 12, MUTED, false));
        box.addView(tv("BUY amount: " + String.format(Locale.US, "%.2f", store.microLiveOrderUsdt) + " USDT • Daily cap: " + store.liveTradesToday + " / " + store.maxLiveTradesPerDay, 13, WHITE, true));
        box.addView(tv("Stop " + String.format(Locale.US, "%.2f", store.stopLoss) + "% • Target " + String.format(Locale.US, "%.2f", store.takeProfit) + "% • Protection: Binance OCO after a real fill", 12, MUTED, false));
        root.addView(box); addGap(12);
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
        String[] names = {"Home", "Markets", "Strategy", "Activity", "Control"};
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
        buildPortfolioCard();
        LinearLayout metrics1 = row(); metrics1.setBaselineAligned(false);
        addMetric(metrics1, "SPOT EQUITY", store.portfolioEquityLabel(), "Binance portfolio", store.portfolioSyncOk ? GREEN : AMBER);
        addMetric(metrics1, "ORDERS", store.liveTradesToday + " / " + store.maxLiveTradesPerDay, "daily hard limit", store.liveTradesToday >= store.maxLiveTradesPerDay ? AMBER : GREEN);
        root.addView(metrics1); addGap(10);
        LinearLayout metrics2 = row(); metrics2.setBaselineAligned(false);
        addMetric(metrics2, "MODE", store.mode.toUpperCase(Locale.US), store.liveUnlocked ? "live gate unlocked" : "live gate locked", store.liveUnlocked ? GREEN : AMBER);
        addMetric(metrics2, "OCO", "ON BUY", "target + stop exit", CYAN);
        root.addView(metrics2); addGap(12);
        buildAutoExecutionCard();
        buildV60LiveScalpingCard();
        buildScalperCard();
        buildSignalCards(false);
        addBottomPanels();
    }

    void buildPortfolioCard() {
        LinearLayout box = cardBox();
        LinearLayout head = row(); head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(section("BINANCE SPOT PORTFOLIO"), new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(tv(store.portfolioSyncOk ? "SYNCED" : "NOT SYNCED", 12, store.portfolioSyncOk ? GREEN : AMBER, true));
        box.addView(head);
        addGap(box, 8);
        box.addView(tv("Spot equity: " + store.portfolioEquityLabel() + "  •  Age: " + store.portfolioAgeLabel(), 14, store.portfolioSyncOk ? GREEN : AMBER, true));
        box.addView(tv("USDT free " + moneyOrDash(store.spotFreeUsdt) + "  •  locked " + moneyOrDash(store.spotLockedUsdt) + "  •  assets " + store.spotAssetCount, 12, MUTED, false));
        box.addView(tv("Top: " + store.topPortfolioAssets, 11, MUTED, false));
        if (store.portfolioWarnings != null && !store.portfolioWarnings.isEmpty()) box.addView(tv(store.portfolioWarnings, 11, AMBER, false));
        addGap(box, 8);
        box.addView(actionButton(portfolioSyncRunning ? "Syncing Spot Portfolio..." : "Sync Spot Portfolio", portfolioSyncRunning ? AMBER : CYAN, v -> syncPortfolio(false)), new LinearLayout.LayoutParams(-1, dp(50)));
        root.addView(box); addGap(12);
    }

    void buildAutoExecutionCard() {
        LinearLayout box = cardBox();
        LinearLayout head = row(); head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(section("AUTOMATIC SPOT BOT"), new LinearLayout.LayoutParams(0, -2, 1));
        String state = store.autoRunning ? "RUNNING" : (store.autoPanic ? "PANIC STOP" : "STOPPED");
        head.addView(tv(state, 12, store.autoRunning ? GREEN : (store.autoPanic ? RED : AMBER), true));
        box.addView(head); addGap(box, 8);
        box.addView(tv("Scans BTC, ETH, BNB and SOL every " + store.scalperScanSeconds + " seconds. It chooses only the strongest qualified closed-candle setup and holds one Binance OCO-protected position at a time.", 12, MUTED, false));
        box.addView(tv("Automatic protected entries require at least " + String.format(Locale.US, "%.2f", AutoTradingPolicy.MINIMUM_AUTOMATIC_QUOTE_USDT) + " USDT; this keeps the stop-side order above Binance minimum after fees.", 11, AMBER, false));
        box.addView(tv("Auto amount " + String.format(Locale.US, "%.2f", store.microLiveOrderUsdt) + " USDT  |  Min confidence " + store.autoMinConfidence + "/100  |  Entries today " + store.liveTradesToday + " / " + store.maxLiveTradesPerDay, 12, WHITE, true));
        box.addView(tv(store.autoStatus, 11, store.autoRunning ? CYAN : AMBER, false));
        if (store.hasAutoPosition()) {
            box.addView(tv("OPEN: " + store.autoActiveSymbol + " | Binance OCO " + store.autoOcoOrderListId + " | protected quantity " + String.format(Locale.US, "%.8f", store.autoProtectedQuantity), 11, GREEN, true));
        }
        if (store.hasAutoPendingOrder()) {
            box.addView(tv("REVIEW REQUIRED: pending automatic " + store.autoPendingSymbol + " order " + store.autoPendingClientOrderId + ". Check Binance first; Emergency Close can only attempt a market exit after your confirmation.", 11, RED, true));
        }
        addGap(box, 10);
        LinearLayout r1 = row();
        r1.addView(actionButton("Start Scanner", GREEN, v -> startBot()), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r1lp = new LinearLayout.LayoutParams(0, dp(52), 1); r1lp.leftMargin = dp(10);
        r1.addView(actionButton("Stop Bot", AMBER, v -> stopBot()), r1lp);
        box.addView(r1); addGap(box, 8);
        LinearLayout r2 = row();
        r2.addView(actionButton("Start Automatic LIVE", store.autoRunning ? GREEN : RED, v -> startAutomaticLive()), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(0, dp(52), 1); r2lp.leftMargin = dp(10);
        r2.addView(actionButton("Panic Stop", RED, v -> panic()), r2lp);
        box.addView(r2); addGap(box, 8);
        LinearLayout r3 = row();
        r3.addView(actionButton("Automation Preflight", CYAN, v -> runAutomaticPreflight()), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r3lp = new LinearLayout.LayoutParams(0, dp(52), 1); r3lp.leftMargin = dp(10);
        r3.addView(actionButton("Emergency Close", RED, v -> confirmAutomaticEmergencyClose()), r3lp);
        box.addView(r3);
        if (store.panicLatched()) {
            addGap(box, 8);
            box.addView(actionButton("Reset Panic State", AMBER, v -> confirmPanicReset(null)), new LinearLayout.LayoutParams(-1, dp(52)));
            box.addView(tv("Panic recovery never restarts automatic trading. Check Binance first; a fresh arm is always required.", 11, AMBER, false));
        }
        root.addView(box); addGap(12);
    }

    void runAutomaticPreflight() {
        String blockers = store.autoStartBlockers();
        BinanceClient.verifyTrustedIp(store, result -> runOnUiThread(() -> {
            String report = "Automatic Spot Preflight\n\n" + result.report + "\n\n";
            report += blockers.isEmpty() ? "All local gates are ready. The one-time Start Automatic LIVE confirmation is still required." : "Complete these local gates:\n" + blockers;
            alert("Automatic Spot Preflight", report);
            render(false);
        }));
    }

    void startAutomaticLive() {
        if (store.autoRunning) { toast("Automatic Spot bot is already running"); return; }
        String blockers = store.autoStartBlockers();
        if (!blockers.isEmpty()) { alert("Automatic LIVE Blocked", blockers); return; }
        input("Start Automatic LIVE Spot Bot", "Type START AUTO BOT", "", false, value -> {
            if (!"START AUTO BOT".equals(value.trim())) { toast("Automatic start cancelled"); return; }
            store.engine.autoExecution.start(result -> runOnUiThread(() -> {
                if (store.autoRunning) startForegroundBotService();
                alert("Automatic Spot Bot", result);
                render(true);
            }));
        });
    }

    void confirmAutomaticEmergencyClose() {
        if (!store.hasAutoPosition() && !store.hasAutoPendingOrder()) { toast("No tracked automatic position or pending BUY is open"); return; }
        input("Emergency Close Automatic Position", "Type EMERGENCY CLOSE", "", false, value -> {
            if (!"EMERGENCY CLOSE".equals(value.trim())) { toast("Emergency close cancelled"); return; }
            store.engine.autoExecution.emergencyClose(result -> runOnUiThread(() -> {
                alert("Automatic Emergency Close", result);
                stopService(new Intent(this, NanuBotService.class));
                render(true);
            }));
        });
    }

    void buildScalperCard() {
        LinearLayout box = cardBox();
        LinearLayout head = row(); head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(section("LIVE SPOT SCALPER"), new LinearLayout.LayoutParams(0, -2, 1));
        int signalColor = "BUY".equals(store.lastScalperSignal) ? GREEN : ("EXIT".equals(store.lastScalperSignal) ? AMBER : CYAN);
        head.addView(tv(store.lastScalperSignal, 12, signalColor, true));
        box.addView(head); addGap(box, 8);
        box.addView(tv(store.scalperSymbol + " • 1m closed candles • EMA 9/21 + RSI 14 • checked " + store.scalperAgeLabel(), 12, MUTED, false));
        box.addView(tv("Price " + moneyOrDash(store.lastScalperPrice) + " • confidence " + store.lastScalperConfidence + "/100 • market checks " + store.scalperMarketChecks, 13, signalColor, true));
        box.addView(tv("The scanner studies closed candles. In Automatic LIVE it selects the strongest qualified pair; every real fill must create Binance OCO exit protection before another entry is allowed.", 12, AMBER, false));
        if (store.lastScalperError != null && !store.lastScalperError.isEmpty()) box.addView(tv(store.lastScalperError, 11, RED, false));
        addGap(box, 8);
        LinearLayout controls = row();
        controls.addView(actionButton("Scan Live Now", CYAN, v -> scanScalperNow()), new LinearLayout.LayoutParams(0, dp(50), 1));
        LinearLayout.LayoutParams reportLp = new LinearLayout.LayoutParams(0, dp(50), 1); reportLp.leftMargin = dp(10);
        controls.addView(actionButton("Signal Report", CYAN, v -> alert("Nanu Live Spot Signal", store.lastScalperReport)), reportLp);
        box.addView(controls); addGap(box, 8);
        LinearLayout settings = row();
        settings.addView(actionButton("Pair: " + store.scalperSymbol, CYAN, v -> chooseSupportedPair()), new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams amountLp = new LinearLayout.LayoutParams(0, dp(48), 1); amountLp.leftMargin = dp(10);
        settings.addView(actionButton("Paper: " + String.format(Locale.US, "%.2f", store.scalperTradeAmountUsdt), GREEN, v -> input("Paper Trade Amount", "Minimum 5 USDT", String.format(Locale.US, "%.2f", store.scalperTradeAmountUsdt), false, value -> {
            try { store.scalperTradeAmountUsdt = Math.max(5.0, Math.min(100.0, Double.parseDouble(value.trim()))); store.save(); render(true); }
            catch (Exception e) { toast("Enter a valid USDT amount"); }
        })), amountLp);
        box.addView(settings); addGap(box, 8);
        LinearLayout behavior = row();
        behavior.addView(actionButton("Paper Auto: " + (store.scalperPaperAutoTrade ? "ON" : "OFF"), store.scalperPaperAutoTrade ? GREEN : AMBER, v -> {
            store.scalperPaperAutoTrade = !store.scalperPaperAutoTrade; store.save(); render(true);
        }), new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams intervalLp = new LinearLayout.LayoutParams(0, dp(48), 1); intervalLp.leftMargin = dp(10);
        behavior.addView(actionButton("Scan: " + store.scalperScanSeconds + " sec", CYAN, v -> input("Scan Interval", "Minimum 30 seconds", String.valueOf(store.scalperScanSeconds), false, value -> {
            try { store.scalperScanSeconds = Math.max(30, Math.min(300, Integer.parseInt(value.trim()))); store.save(); render(true); }
            catch (Exception e) { toast("Enter 30 to 300 seconds"); }
        })), intervalLp);
        box.addView(behavior);
        root.addView(box); addGap(12);
    }

    void scanScalperNow() {
        toast("Reading closed Binance candles...");
        BinanceClient.scanScalper(store, (signal, report) -> runOnUiThread(() -> {
            if (signal == null) alert("Market Scan Failed", report);
            else alert("Nanu Live Spot Signal", report);
            render(false);
        }));
    }

    void chooseSupportedPair() {
        input("Active Scalper Pair", "BTCUSDT, ETHUSDT, BNBUSDT, or SOLUSDT", store.scalperSymbol, false, value -> {
            String pair = store.normalizeCoin(value);
            if (!BinanceClient.isSupportedPair(pair)) { toast("Use BTCUSDT, ETHUSDT, BNBUSDT, or SOLUSDT"); return; }
            store.scalperSymbol = pair;
            store.save();
            render(true);
        });
    }

    void buildScanner() {
        LinearLayout box = cardBox();
        box.addView(screenTitle("SCANNER"));
        box.addView(tv("The Nanu scanner reads live closed Binance candles for the four approved USDT Spot pairs. It does not predict profit; automatic orders require the separate Automatic LIVE safety gates.", 13, MUTED, false));
        addGap(box, 12);
        String[] pairs = {"BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT"};
        for (String coin : pairs) {
            LinearLayout row = cardMini(); row.setGravity(Gravity.CENTER_VERTICAL); row.addView(tv(coin, 17, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
            TextView use = pill("Use", CYAN, 12); use.setOnClickListener(v -> { store.scalperSymbol = coin; store.save(); render(true); });
            LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(-2, -2); up.leftMargin = dp(8); row.addView(use, up);
            box.addView(row); addGap(box, 8);
        }
        root.addView(box); addGap(12); buildScalperCard(); buildSignalCards(true);
    }

    void buildBrain() {
        LinearLayout box = cardBox(); box.addView(screenTitle("SCALPING STRATEGY"));
        box.addView(tv("The device strategy uses closed one-minute candles, EMA 9/21, RSI 14, volatility, volume, and bounded local learning. It does not predict profit or remove market risk.", 13, MUTED, false)); addGap(box, 12);
        LinearLayout brainMetrics = row(); brainMetrics.setBaselineAligned(false);
        addMetric(brainMetrics, "LEARNING", String.valueOf(store.brainLearningCycles), "cycles", CYAN);
        addMetric(brainMetrics, "BIAS", String.format(Locale.US, "%.2f", store.brainAdaptiveBias), "adaptive", store.brainAdaptiveBias >= 0 ? GREEN : AMBER);
        box.addView(brainMetrics); addGap(box, 10);
        for (String note : store.engine.brain) { LinearLayout n = cardMini(); n.addView(tv(note, 14, WHITE, false)); box.addView(n); addGap(box, 8); }
        if (store.brainMemoryLog != null && !store.brainMemoryLog.isEmpty()) {
            LinearLayout memory = cardMini(); memory.setOrientation(LinearLayout.VERTICAL);
            memory.addView(tv("Recent memory", 12, CYAN, true));
            String[] rows = store.brainMemoryLog.split("\\n");
            for (int i = 0; i < Math.min(3, rows.length); i++) memory.addView(tv(rows[i], 11, MUTED, false));
            box.addView(memory); addGap(box, 8);
        }
        LinearLayout matrix = cardMini(); matrix.setOrientation(LinearLayout.VERTICAL);
        matrix.addView(section("Live Indicator Matrix"));
        matrix.addView(tv("Pair " + store.scalperSymbol + " • signal " + store.lastScalperSignal + " • confidence " + store.lastScalperConfidence + "/100 • live checks " + store.scalperMarketChecks, 13, MUTED, false));
        matrix.addView(tv("Risk approval: paper auto only; real Binance orders require Test Order, a manual typed confirmation, and Binance OCO protection after the fill.", 13, MUTED, false));
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
        box.addView(tv("Account controls, local research tools, API Doctor, permissions, and safety checks. No VPS, executor URL, or executor token is required.", 13, MUTED, false)); addGap(box, 14);
        box.addView(section("TRADING MODE")); addGap(box, 8);
        box.addView(modeButton("PAPER", "Safe simulation", "paper"), new LinearLayout.LayoutParams(-1, dp(76))); addGap(box, 10);
        LinearLayout m2 = row(); m2.addView(modeButton("TESTNET", "API Doctor", "testnet"), new LinearLayout.LayoutParams(0, dp(76), 1)); LinearLayout.LayoutParams mlp2 = new LinearLayout.LayoutParams(0, dp(76), 1); mlp2.leftMargin = dp(10); m2.addView(modeButton("LIVE", "Locked checklist", "live"), mlp2); box.addView(m2);
        box.addView(tv("Live requires API key + API health check + risk limits + typing UNLOCK LIVE.", 12, AMBER, false)); addGap(box, 18);

        box.addView(section("BINANCE API")); addGap(box, 8);
        box.addView(actionButton(store.apiKey.isEmpty() ? "Add Binance API Key" : "API Key Saved • Tap to Replace", CYAN, v -> input("Binance API Key", "Paste key", "", false, s -> { if (!s.trim().isEmpty()) { store.apiKey = s.trim(); store.liveUnlocked=false; store.clearApiDoctorStatus("API key changed. Run API Doctor again for the selected mode."); } render(true); })), new LinearLayout.LayoutParams(-1, dp(54))); addGap(box, 8);
        box.addView(actionButton(store.apiSecret.isEmpty() ? "Add API Secret" : "API Secret Saved • Tap to Replace", CYAN, v -> input("Binance API Secret", "Paste secret", "", true, s -> { if (!s.trim().isEmpty()) { store.apiSecret = s.trim(); store.liveUnlocked=false; store.clearApiDoctorStatus("API secret changed. Run API Doctor again for the selected mode."); } render(true); })), new LinearLayout.LayoutParams(-1, dp(54))); addGap(box, 8);
        box.addView(actionButton(apiDoctorRunning ? "API Doctor Running..." : "Run API Doctor", apiDoctorRunning ? AMBER : GREEN, v -> testApi()), new LinearLayout.LayoutParams(-1, dp(56))); addGap(box, 8);
        LinearLayout apiTools = row();
        apiTools.addView(actionButton("Show My Public IP", CYAN, v -> showPublicIp()), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams apiToolsLp = new LinearLayout.LayoutParams(0, dp(52), 1); apiToolsLp.leftMargin = dp(10);
        apiTools.addView(actionButton("Device Readiness", CYAN, v -> alert("Device Readiness", deviceReadinessReport())), apiToolsLp);
        box.addView(apiTools); addGap(box, 10);
        box.addView(actionButton("API Mode Guide", AMBER, v -> alert("API Mode Guide", apiModeGuide())), new LinearLayout.LayoutParams(-1, dp(52))); addGap(box, 10);
        buildApiDoctorStatus(box);
        addGap(box, 8);
        buildLiveChecklist(box);
        addGap(box, 8);
        box.addView(actionButton("Confirm API Withdrawals OFF", store.withdrawalPermissionConfirmedOff ? GREEN : AMBER, v -> { store.withdrawalPermissionConfirmedOff = !store.withdrawalPermissionConfirmedOff; store.liveUnlocked = false; store.save(); toast(store.withdrawalPermissionConfirmedOff ? "Withdrawal permission confirmed OFF" : "Withdrawal confirmation removed"); render(false); }), new LinearLayout.LayoutParams(-1, dp(52)));
        box.addView(tv("Use this only after checking Binance API Management and confirming Enable Withdrawals is OFF for this API key.", 11, AMBER, false));
        addGap(box, 8);
        box.addView(actionButton("Unlock LIVE Control Gate", RED, v -> unlockLive()), new LinearLayout.LayoutParams(-1, dp(54)));
        box.addView(tv("This device can run automatic Spot execution only after every live gate, static-IP verification, a one-time arm, and Start Automatic LIVE. Every filled automatic BUY receives Binance OCO protection.", 12, AMBER, false));
        if (store.hasPendingProtectionCheck()) {
            addGap(box, 8);
            box.addView(actionButton("Acknowledge Binance Protection Check", RED, v -> acknowledgeProtectionCheck()), new LinearLayout.LayoutParams(-1, dp(52)));
        }
        addGap(box, 18);

        box.addView(section("RISK SHIELD")); addGap(box, 8);
        box.addView(tv("Risk/trade: " + store.riskPerTrade + "%  •  Daily loss limit: " + store.dailyLossLimit + "%  •  Max open trades: " + store.maxOpenTrades, 13, MUTED, false));
        box.addView(tv("Stop-loss: " + store.stopLoss + "%  •  Take-profit: " + store.takeProfit + "%  •  Trailing: " + store.trailingStop + "%", 13, MUTED, false)); addGap(box, 8);
        buildOrderSafetySettings(box);
        buildV60Settings(box);
        buildAutomaticSettings(box);
        box.addView(actionButton("Reset Paper Wallet", AMBER, v -> { store.engine.resetPaper(); render(true); }), new LinearLayout.LayoutParams(-1, dp(52))); addGap(box, 18);

        buildProfitGuardSettings(box);

        box.addView(section("TELEGRAM / ALERTS")); addGap(box, 8);
        box.addView(tv("Paste Telegram bot token + chat ID, then run Telegram Doctor. Nanu sends alerts on Start, Stop, Panic, Profit Guard and API warnings.", 12, MUTED, false)); addGap(box, 8);
        LinearLayout tele = row();
        tele.addView(actionButton(store.telegramToken.isEmpty() ? "Bot Token" : "Bot Token Saved", CYAN, v -> input("Telegram Bot Token", "Paste token from BotFather", "", true, t -> { if (!t.trim().isEmpty()) { store.telegramToken=t.trim(); store.telegramDoctorOk=false; store.save(); } render(true); })), new LinearLayout.LayoutParams(0, dp(52), 1));
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
            if (!store.appPinEnabled && (store.appPinHash == null || store.appPinHash.isEmpty())) { input("Set Nanu PIN", "Enter new PIN", "", true, s -> { if (s.trim().length() < 4) toast("Use at least 4 digits/characters"); else { store.setAppPin(s); pinSessionUnlocked=true; render(false); } }); }
            else { store.appPinEnabled = !store.appPinEnabled; if (!store.appPinEnabled) pinSessionUnlocked=false; store.save(); render(false); }
        }), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1); lp.leftMargin = dp(10);
        r.addView(actionButton("Change PIN", AMBER, v -> input("Change Nanu PIN", "Enter new PIN", "", true, s -> { if (s.trim().length() < 4) toast("Use at least 4 digits/characters"); else { store.setAppPin(s); pinSessionUnlocked=true; toast("PIN saved"); render(false); } })), lp);
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
        status.addView(tv("Current public IP: " + (store.lastPublicIp.isEmpty() ? "Not checked" : store.lastPublicIp) + " (" + publicIpAgeLabel() + ")", 12, MUTED, false));
        status.addView(tv("Expected device IP: " + (store.autoTrustedStaticIp.isEmpty() ? "Not set" : store.autoTrustedStaticIp), 12, MUTED, false));
        status.addView(tv("Account withdraw ability: " + (sameMode && store.lastApiAccountCanWithdraw ? "ON / account-level flag" : "OFF or not checked"), 12, sameMode && store.lastApiAccountCanWithdraw ? AMBER : MUTED, false));
        status.addView(tv("Manual API-key withdrawal confirmation: " + (store.withdrawalPermissionConfirmedOff ? "OFF confirmed" : "Required before live unlock"), 12, store.withdrawalPermissionConfirmedOff ? GREEN : RED, true));
        status.addView(tv(store.lastApiDiagnosis, 12, store.lastApiCanTrade ? GREEN : AMBER, false));
        status.addView(tv("Automatic LIVE requires API Doctor + Telegram Doctor + Profit Guard + Panic test + withdrawals OFF + static-IP verification + one-time arm.", 12, AMBER, false));
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
        list.addView(tv("Automatic LIVE is separate from this manual checklist: it needs its own static-IP check, Binance test order, one-time arm, and Start Automatic LIVE confirmation. Every real BUY must receive Binance OCO protection after the fill.", 11, AMBER, false));
        box.addView(list);
    }

    TextView checkLine(String label, boolean ok) {
        return tv((ok ? "✅ " : "❌ ") + label, 12, ok ? GREEN : AMBER, true);
    }

    boolean notificationReady() {
        return !store.phoneNotifications || Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    String apiModeGuide() {
        return "Paper:\nNo API key. Live candle data may drive internal paper positions only.\n\n" +
                "Testnet:\nUse Binance Spot Testnet API key only for API Doctor. Do not paste a live key.\n\n" +
                "Live:\nUse a dedicated real Binance API key. Enable Spot trading only, keep Withdrawals OFF, and restrict the key to a trusted IP where practical.\n\n" +
                "Trusted IP meaning:\nThe IP must be the public IP of the device/server that sends API requests. If your mobile internet IP changes, update Binance trusted IP again.\n\n" +
                "Nanu App:\nNo VPS is needed for scanning, portfolio sync, test orders, manual protected Spot orders, or Automatic LIVE while the foreground service is running. Keep the app on your private device.\n\nAutomatic LIVE checklist:\nAPI Doctor PASS + Telegram Doctor PASS + Profit Guard ON + Panic tested + withdrawals OFF confirmation + matching static public IP + successful Binance Test Order + one-time Automatic LIVE arm.\n\nAutomatic LIVE sends only qualifying closed-candle entries across the four approved pairs, holds one Binance OCO-protected position, and stops for review on uncertain exchange state.";
    }


    void buildOrderSafetyCard() {
        LinearLayout box = cardBox();
        LinearLayout head = row(); head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(section("ORDER SAFETY ENGINE"), new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(tv(store.liveDryRunEnabled ? "DRY-RUN" : "OFF", 13, store.liveDryRunEnabled ? GREEN : AMBER, true));
        box.addView(head); addGap(box, 8);
        box.addView(tv("v6.2 previews live orders, checks safety rules, and can send manual confirmed micro Binance orders only when armed.", 12, MUTED, false));
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
        r2.addView(actionButton("Max/day: " + store.maxLiveTradesPerDay, CYAN, v -> input("Max Live Trades Per Day", "1 to 4", String.valueOf(store.maxLiveTradesPerDay), false, val -> { try { store.maxLiveTradesPerDay = Math.max(1, Math.min(4, Integer.parseInt(val.trim()))); store.liveUnlocked=false; store.save(); render(true); } catch(Exception e) { toast("Enter 1 to 4"); } })), r2lp);
        box.addView(r2); addGap(box, 8);
        LinearLayout r3 = row();
        r3.addView(actionButton("Slippage: " + String.format(Locale.US, "%.2f", store.slippageLimitPct) + "%", CYAN, v -> input("Slippage Limit %", "Example: 0.25", String.format(Locale.US, "%.2f", store.slippageLimitPct), false, val -> { try { store.slippageLimitPct = Math.max(0.01, Math.min(5.0, Double.parseDouble(val.trim()))); store.liveUnlocked=false; store.save(); render(true); } catch(Exception e) { toast("Enter valid percent"); } })), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r3lp = new LinearLayout.LayoutParams(0, dp(52), 1); r3lp.leftMargin = dp(10);
        r3.addView(actionButton("Run Dry-Run Preview", GREEN, v -> runDryRunPreview()), r3lp);
        box.addView(r3); addGap(box, 8);
        LinearLayout r4 = row();
        r4.addView(actionButton("Daily Loss: " + String.format(Locale.US, "%.2f", store.dailyLossLimit) + "%", CYAN, v -> input("Daily Loss Limit Percent", "Example: 1 or 2", String.format(Locale.US, "%.2f", store.dailyLossLimit), false, val -> { try { store.dailyLossLimit = Math.max(0.1, Math.min(10.0, Double.parseDouble(val.trim()))); store.liveUnlocked=false; store.save(); render(true); } catch(Exception e) { toast("Enter 0.1 to 10 percent"); } })), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams openLimitLp = new LinearLayout.LayoutParams(0, dp(52), 1); openLimitLp.leftMargin = dp(10);
        r4.addView(actionButton("Open Limit: " + Math.max(1, store.maxOpenTrades), CYAN, v -> input("Max Open Trades", "Use 1 for safest dry-run", String.valueOf(store.maxOpenTrades), false, val -> { try { store.maxOpenTrades = Math.max(1, Math.min(10, Integer.parseInt(val.trim()))); store.liveUnlocked=false; store.save(); render(true); } catch(Exception e) { toast("Enter valid count"); } })), openLimitLp);
        box.addView(r4); addGap(box, 8);
        box.addView(actionButton("Reset Safety State", AMBER, v -> { store.resetOrderSafetyState("Manual reset from Security"); toast("Order Safety state reset"); render(true); }), new LinearLayout.LayoutParams(-1, dp(52))); addGap(box, 8);
        box.addView(tv("Manual confirmation remains required. Keep Binance Test Order ON first. Real mode requires ARM REAL BUY and resets after every real order. After a filled real BUY, Nanu requests a Binance OCO target/stop order; always verify it in Binance Open Orders.", 11, AMBER, false)); addGap(box, 18);
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
        return "Controlled Live Dry-Run:\nNanu uses real mode/risk settings to preview an order. The preview sends no order; manual BUY/SELL requires a separate typed confirmation.\n\n" +
                "Checks included:\n• LIVE selected\n• API Doctor private OK\n• Spot trading permission OK\n• Withdrawals confirmed OFF\n• Profit Guard ON\n• Telegram Doctor PASS\n• Panic tested\n• Minimum notional\n• Quantity rounding\n• Max trades/day\n• Order cooldown\n• Slippage limit\n\n" +
                "Protection:\nAfter a confirmed real BUY, Nanu calculates Binance-compliant prices and requests an exchange-side OCO target/stop order. If that request fails, it attempts an emergency market sell and reports the result. Always check Binance Open Orders yourself.";
    }


    void buildV60LiveScalpingCard() {
        LinearLayout box = cardBox();
        LinearLayout head = row(); head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(section("PROTECTED SPOT ORDER"), new LinearLayout.LayoutParams(0, -2, 1));
        String state = store.liveOrderTestMode ? "TEST ORDER" : (store.liveRealOrderArmed ? "REAL ARMED" : "REAL LOCKED");
        head.addView(tv(state, 12, store.liveOrderTestMode ? CYAN : (store.liveRealOrderArmed ? RED : AMBER), true));
        box.addView(head); addGap(box, 8);
        box.addView(tv("Manual protected orders remain available. Automatic LIVE execution is controlled separately by its static-IP, one-time arm, foreground-service, and OCO safety gates.", 12, AMBER, false));
        box.addView(tv("BUY: " + String.format(Locale.US, "%.2f USDT", store.microLiveOrderUsdt) + "  •  Limit: " + String.format(Locale.US, "%.2f USDT", store.manualOrderLimitUsdt) + "  •  Test order: " + (store.liveOrderTestMode ? "ON" : "OFF"), 12, MUTED, false));
        box.addView(tv("Compliance: " + (store.complianceGuardEnabled ? "ON" : "OFF") + "  •  Rate lock: " + (store.binanceRateLimitLock ? "ACTIVE" : "Clear") + "  •  OCO: created after a real filled BUY", 12, store.binanceRateLimitLock ? RED : GREEN, true));
        if (store.lastBinanceStatusCode != 0) box.addView(tv("Last Binance HTTP: " + store.lastBinanceStatusCode + " • " + store.lastBinanceErrorDoctor, 11, store.lastBinanceStatusCode >= 400 ? RED : GREEN, false));
        addGap(box, 10);
        LinearLayout r1 = row();
        r1.addView(actionButton("Protected Spot BUY", GREEN, v -> confirmMicroBuy()), new LinearLayout.LayoutParams(0, dp(52), 1));
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
        box.addView(section("DEVICE ORDER CONTROLS")); addGap(box, 8);
        box.addView(tv("Manual protected Spot orders, automatic execution controls, API diagnostics, backup, and Error Doctor.", 12, MUTED, false)); addGap(box, 8);
        LinearLayout r1 = row();
        r1.addView(actionButton("Compliance Guard: " + (store.complianceGuardEnabled ? "ON" : "OFF"), store.complianceGuardEnabled ? GREEN : RED, v -> { store.complianceGuardEnabled = !store.complianceGuardEnabled; store.liveRealOrderArmed = false; store.save(); render(true); }), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r1lp = new LinearLayout.LayoutParams(0, dp(52), 1); r1lp.leftMargin = dp(10);
        r1.addView(actionButton("Test Order: " + (store.liveOrderTestMode ? "ON" : "OFF"), store.liveOrderTestMode ? GREEN : RED, v -> toggleTestOrderMode()), r1lp);
        box.addView(r1); addGap(box, 8);

        LinearLayout r2 = row();
        r2.addView(actionButton("BUY: " + String.format(Locale.US, "%.2f", store.microLiveOrderUsdt) + " USDT", CYAN, v -> input("Spot BUY Amount", "6 to your manual limit", String.format(Locale.US, "%.2f", store.microLiveOrderUsdt), false, val -> { try { store.microLiveOrderUsdt = Math.max(AutoTradingPolicy.MINIMUM_AUTOMATIC_QUOTE_USDT, Math.min(store.manualOrderLimitUsdt, Double.parseDouble(val.trim()))); store.liveDryRunOrderUsdt = store.microLiveOrderUsdt; store.liveRealOrderArmed=false; store.save(); render(true); } catch(Exception e){ toast("Enter an amount within your manual limit"); } })), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(0, dp(52), 1); r2lp.leftMargin = dp(10);
        r2.addView(actionButton(store.liveRealOrderArmed ? "Real Order ARMED" : "Arm Real BUY", store.liveRealOrderArmed ? RED : AMBER, v -> armRealMicro()), r2lp);
        box.addView(r2); addGap(box, 8);

        LinearLayout r3 = row();
        r3.addView(actionButton("Order Limit: " + String.format(Locale.US, "%.2f", store.manualOrderLimitUsdt) + " USDT", CYAN, v -> input("Manual Order Limit", "6 to 1000 USDT", String.format(Locale.US, "%.2f", store.manualOrderLimitUsdt), false, val -> { try { store.manualOrderLimitUsdt = Math.max(AutoTradingPolicy.MINIMUM_AUTOMATIC_QUOTE_USDT, Math.min(1000.0, Double.parseDouble(val.trim()))); if (store.microLiveOrderUsdt > store.manualOrderLimitUsdt) store.microLiveOrderUsdt = store.manualOrderLimitUsdt; store.liveRealOrderArmed=false; store.save(); render(true); } catch(Exception e){ toast("Enter 6 to 1000 USDT"); } })), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r3lp = new LinearLayout.LayoutParams(0, dp(52), 1); r3lp.leftMargin = dp(10);
        r3.addView(actionButton("Manual Approval Guide", GREEN, v -> alert("Manual Order Approval", "Manual real orders need a fresh dry-run, a one-time arm phrase, and a typed BUY or SELL confirmation. Automatic LIVE uses its separate static-IP, API Doctor, Telegram Doctor, arm, and Start Automatic LIVE flow.")), r3lp);
        box.addView(r3); addGap(box, 8);

        LinearLayout r4 = row();
        r4.addView(actionButton("Export Backup", CYAN, v -> alert("Nanu Backup", exportBackupText())), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r4lp = new LinearLayout.LayoutParams(0, dp(52), 1); r4lp.leftMargin = dp(10);
        r4.addView(actionButton(store.binanceRateLimitLock ? "Clear Rate Limit Lock" : "Rate Limit: Clear", store.binanceRateLimitLock ? RED : MUTED, v -> confirmRateLimitClear()), r4lp);
        box.addView(r4); addGap(box, 8);

        LinearLayout r5 = row();
        r5.addView(actionButton("Send /status to Telegram", CYAN, v -> { store.triggerAlert("Nanu Status", telegramStatusText(), false, "daily"); toast("Status sent if Telegram alerts are ON"); }), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r5lp = new LinearLayout.LayoutParams(0, dp(52), 1); r5lp.leftMargin = dp(10);
        r5.addView(actionButton("Error Doctor", store.binanceRateLimitLock ? RED : CYAN, v -> alert("Nanu Error Doctor", errorDoctorReport())), r5lp);
        box.addView(r5); addGap(box, 8);
        box.addView(tv("Telegram messages can report status and alerts. Telegram cannot submit a real BUY or SELL from this device.", 11, AMBER, false)); addGap(box, 18);
    }

    void buildAutomaticSettings(LinearLayout box) {
        box.addView(section("AUTOMATIC LIVE EXECUTION")); addGap(box, 8);
        box.addView(tv("Automatic mode scans BTCUSDT, ETHUSDT, BNBUSDT and SOLUSDT. It selects the highest qualified closed-candle BUY, permits one OCO-protected position at a time, and enforces the daily entry limit.", 12, MUTED, false)); addGap(box, 8);
        LinearLayout r1 = row();
        r1.addView(actionButton(store.autoTrustedStaticIp.isEmpty() ? "Set Expected Public IP" : "Expected IP: " + store.autoTrustedStaticIp, CYAN, v -> input("Expected Device Public IP", "Example: 203.0.113.10", store.autoTrustedStaticIp, false, value -> {
            String ip = value.trim();
            if (!AutoTradingPolicy.isStaticIp(ip)) { toast("Enter a valid public IPv4 or IPv6 address"); return; }
            store.autoTrustedStaticIp = ip; store.autoLiveArmed = false; store.save(); render(true);
        })), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r1lp = new LinearLayout.LayoutParams(0, dp(52), 1); r1lp.leftMargin = dp(10);
        r1.addView(actionButton("Use Current IP", CYAN, v -> useCurrentPublicIp()), r1lp);
        box.addView(r1); addGap(box, 8);
        box.addView(actionButton("Check Device IP", CYAN, v -> checkDeviceIp()), new LinearLayout.LayoutParams(-1, dp(52))); addGap(box, 6);
        box.addView(tv("Expected: " + (store.autoTrustedStaticIp.isEmpty() ? "Not set" : store.autoTrustedStaticIp) + "  |  Current: " + (store.lastPublicIp.isEmpty() ? "Not checked" : store.lastPublicIp) + "  |  Last check: " + publicIpAgeLabel(), 11, MUTED, false)); addGap(box, 8);
        LinearLayout r2 = row();
        r2.addView(actionButton("Auto Amount: " + String.format(Locale.US, "%.2f", store.microLiveOrderUsdt) + " USDT", CYAN, v -> input("Automatic Spot Order Amount", "6 to your order limit", String.format(Locale.US, "%.2f", store.microLiveOrderUsdt), false, value -> {
            try { store.microLiveOrderUsdt = Math.max(AutoTradingPolicy.MINIMUM_AUTOMATIC_QUOTE_USDT, Math.min(store.manualOrderLimitUsdt, Double.parseDouble(value.trim()))); store.liveDryRunOrderUsdt = store.microLiveOrderUsdt; store.autoLiveArmed = false; store.save(); render(true); }
            catch (Exception e) { toast("Enter an amount inside your order limit"); }
        })), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(0, dp(52), 1); r2lp.leftMargin = dp(10);
        r2.addView(actionButton("Minimum Signal: " + store.autoMinConfidence, CYAN, v -> input("Automatic Minimum Confidence", "60 to 95", String.valueOf(store.autoMinConfidence), false, value -> {
            try { store.autoMinConfidence = Math.max(60, Math.min(95, Integer.parseInt(value.trim()))); store.autoLiveArmed = false; store.save(); render(true); }
            catch (Exception e) { toast("Enter a whole number from 60 to 95"); }
        })), r2lp);
        box.addView(r2); addGap(box, 8);
        LinearLayout r3 = row();
        r3.addView(actionButton(store.autoLiveArmed ? "Automatic LIVE ARMED" : "Arm Automatic LIVE", store.autoLiveArmed ? RED : AMBER, v -> armAutomaticLive()), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams r3lp = new LinearLayout.LayoutParams(0, dp(52), 1); r3lp.leftMargin = dp(10);
        r3.addView(actionButton("Readiness Report", CYAN, v -> runAutomaticPreflight()), r3lp);
        box.addView(r3); addGap(box, 8);
        box.addView(tv("Arm is cleared on Stop, Panic, API changes, restart, and any automatic failure. Test Order Mode must be OFF only after you have completed a successful Binance test order. Never enable API withdrawals.", 11, AMBER, false)); addGap(box, 18);
    }

    void armAutomaticLive() {
        if (store.autoRunning) { toast("Stop the automatic bot before changing its arm state"); return; }
        if (store.liveOrderTestMode) { toast("Complete a Binance Test Order, then turn Test Order OFF before automatic LIVE arming"); return; }
        input("Arm Automatic LIVE Spot", "Type ARM AUTO LIVE", "", false, value -> {
            if (!"ARM AUTO LIVE".equals(value.trim())) { toast("Automatic LIVE not armed"); return; }
            store.autoLiveArmed = true;
            String blockers = store.autoStartBlockers();
            if (!blockers.isEmpty()) {
                store.autoLiveArmed = false;
                store.save();
                alert("Automatic LIVE Cannot Arm", blockers);
                render(true);
                return;
            }
            store.autoStatus = "Automatic LIVE armed. Use Start Automatic LIVE on Home to begin the foreground executor.";
            store.save();
            store.triggerAlert("Nanu Automatic LIVE Armed", "Automatic Spot bot is armed for one operator-started session. It still requires Start Automatic LIVE.", true, "live");
            render(true);
        });
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
        if (!missing.isEmpty()) { alert("Cannot Arm Real Spot BUY", missing); return; }
        input("ARM REAL SPOT BUY", "Type ARM REAL BUY", "", false, s -> {
            if ("ARM REAL BUY".equals(s.trim())) { store.liveRealOrderArmed = true; store.save(); store.triggerAlert("Nanu Real Spot BUY Armed", "One manual confirmed real Spot BUY is armed. It will reset after one order.", true, "live"); toast("Real Spot BUY armed"); }
            else toast("Not armed");
            render(true);
        });
    }

    void confirmMicroBuy() {
        String missingBefore = manualOrderMissingItems();
        if (!missingBefore.isEmpty()) { alert("Spot BUY Blocked", missingBefore); render(false); return; }
        OrderSafetyEngine.Preview p = OrderSafetyEngine.buildDryRunPreview(store);
        store.dryRunPreviewsToday++;
        if (!p.pass) { store.save(); alert("Spot BUY Blocked", p.report); render(false); return; }
        // The preview sets a cooldown to prevent spam. For this immediate manual-confirmed
        // order, clear it once so the final order call can run, then BinanceClient sets
        // a fresh cooldown after the test/real order response.
        store.orderCooldownUntilMs = 0L;
        store.save();
        input("Confirm Spot BUY", "Type CONFIRM BUY", "", false, s -> {
            if (!"CONFIRM BUY".equals(s.trim())) { toast("BUY cancelled"); return; }
            toast(store.liveOrderTestMode ? "Sending Binance test order..." : "Sending real protected Spot BUY...");
            BinanceClient.placeMarketOrder(store, p.symbol, "BUY", store.microLiveOrderUsdt, result -> runOnUiThread(() -> { alert("Nanu AI Trading Bot BUY Result", result); render(false); }));
        });
    }

    void confirmMicroSell() {
        String sym = store.lastOrderSymbol == null || store.lastOrderSymbol.isEmpty() ? (store.watchlist.isEmpty() ? "BTCUSDT" : store.watchlist.get(0)) : store.lastOrderSymbol;
        String missing = manualOrderMissingItems(false);
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
        return manualOrderMissingItems(true);
    }

    void acknowledgeProtectionCheck() {
        if (!store.hasPendingProtectionCheck()) return;
        String symbol = store.pendingProtectionSymbol;
        input("Confirm Binance Review", "Type I CHECKED BINANCE", "", false, value -> {
            if (!"I CHECKED BINANCE".equals(value.trim())) { toast("Protection check remains active"); return; }
            store.clearProtectionCheck("User confirmed Binance order history and Open Orders review for " + symbol);
            toast("Protection check cleared");
            render(true);
        });
    }

    String manualOrderMissingItems(boolean enteringBuy) {
        StringBuilder m = new StringBuilder();
        if (!"live".equals(store.mode)) m.append("• Select LIVE mode.\n");
        if (!store.liveUnlocked) m.append("• Unlock LIVE gate after checklist.\n");
        if (store.apiKey.isEmpty() || store.apiSecret.isEmpty()) m.append("• Add Binance API key and secret.\n");
        if (enteringBuy) {
            if (!store.apiTradingOkForCurrentMode()) m.append("• Run LIVE API Doctor and confirm Spot trading OK.\n");
            if (!store.withdrawalPermissionConfirmedOff) m.append("• Confirm API-key withdrawals are OFF.\n");
            if (!store.complianceGuardEnabled) m.append("• Turn Compliance Guard ON.\n");
            if (store.binanceRateLimitLock) m.append("• Binance rate-limit lock is active. Wait/check Binance, then reset safety.\n");
            if (store.hasPendingProtectionCheck()) m.append("• Inspect Binance and acknowledge the pending ").append(store.pendingProtectionSymbol).append(" protection check.\n");
            if (!store.profitGuardEnabled) m.append("• Enable Profit Guard.\n");
            if (!store.panicButtonTested) m.append("• Test Panic button once.\n");
            if (store.engine.panic) m.append("• Clear Panic state by stopping/resetting safely.\n");
            if (store.liveTradesToday >= Math.max(1, store.maxLiveTradesPerDay)) m.append("• Max trades/day reached.\n");
            if (System.currentTimeMillis() < store.orderCooldownUntilMs) m.append("• Order cooldown active.\n");
            if (!store.liveOrderTestMode && !store.liveRealOrderArmed) m.append("• For REAL order, type ARM REAL BUY first.\n");
        }
        if (m.length() == 0) return "";
        return "Nanu blocked the manual Spot order until these are fixed:\n\n" + m.toString();
    }

    String complianceReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Nanu AI Trading Bot Binance Compliance Guard\n\n");
        sb.append("Normal low-frequency scalping assistant: allowed only through official Binance API.\n");
        sb.append("Forbidden behavior blocked by design:\n");
        sb.append("• no order spam\n• no wash trading\n• no fake volume\n• no spoofing/cancel spam\n• no withdrawal permission\n• no bypassing API limits\n\n");
        sb.append("Guard status: ").append(store.complianceGuardEnabled ? "ON" : "OFF").append("\n");
        sb.append("Order cooldown: ").append(store.orderCooldownSeconds).append(" seconds\n");
        sb.append("Max live trades/day: ").append(store.maxLiveTradesPerDay).append("\n");
        sb.append("Max open trades: ").append(store.maxOpenTrades).append("\n");
        sb.append("Manual order USDT: ").append(String.format(Locale.US, "%.2f", store.microLiveOrderUsdt)).append("\n");
        sb.append("Manual order limit USDT: ").append(String.format(Locale.US, "%.2f", store.manualOrderLimitUsdt)).append("\n");
        sb.append("Binance test order mode: ").append(store.liveOrderTestMode).append("\n");
        sb.append("Rate-limit lock: ").append(store.binanceRateLimitLock).append("\n");
        sb.append("Last Binance HTTP: ").append(store.lastBinanceStatusCode).append("\n");
        sb.append("Remote executor: not used.\n");
        sb.append("Real BUY protection: Binance OCO target/stop requested after a confirmed fill.\n\n");
        sb.append("Reminder: Nanu cannot guarantee profit and cannot guarantee zero bugs. Keep trade size small.");
        String r = sb.toString(); store.lastComplianceReport = r; store.save(); return r;
    }

    String errorDoctorReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Nanu AI Trading Bot Error Doctor\n\n");
        sb.append("Last Binance HTTP: ").append(store.lastBinanceStatusCode == 0 ? "None" : String.valueOf(store.lastBinanceStatusCode)).append("\n");
        sb.append("Diagnosis: ").append(store.lastBinanceErrorDoctor).append("\n\n");
        sb.append(BinanceClient.explainBinanceCode(store.lastBinanceStatusCode, store.lastBinanceErrorDoctor)).append("\n\n");
        sb.append("Common fixes:\n• 400: check min notional/step size/symbol.\n• 401: check API key, secret, timestamp, trusted IP.\n• 403: check permissions/restrictions.\n• 418/429: stop, wait, and avoid repeated retries.\n");
        return sb.toString();
    }

    String exportBackupText() {
        StringBuilder sb = new StringBuilder();
        sb.append("NANU AI TRADING BOT BACKUP - NO SECRETS\n");
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
        sb.append("manualOrderLimitUsdt=").append(store.manualOrderLimitUsdt).append('\n');
        sb.append("telegramAlertsEnabled=").append(store.telegramAlertsEnabled).append('\n');
        sb.append("API_KEY=HIDDEN\nAPI_SECRET=HIDDEN\nTELEGRAM_TOKEN=HIDDEN\n");
        String r = sb.toString(); store.lastBackupText = r; store.save(); return r;
    }

    String telegramStatusText() {
        return "Nanu AI Trading Bot Status\nMode: " + store.mode.toUpperCase(Locale.US) + "\nBot: " + store.runtimeState() + "\nReal entries: " + store.liveTradesToday + "/" + store.maxLiveTradesPerDay + "\nScalper: " + store.scalperSymbol + " / " + store.lastScalperSignal + "\nSpot equity: " + store.portfolioEquityLabel() + "\nManual order limit: " + String.format(Locale.US, "%.2f", store.manualOrderLimitUsdt) + " USDT";
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
        box.addView(actionButton("Choose Alert Sound: " + alertSoundLabel(), CYAN, v -> chooseAlertSound()), new LinearLayout.LayoutParams(-1, dp(52))); addGap(box, 8);
        box.addView(tv("This applies to Nanu alerts and tests. The quiet foreground status notification stays silent.", 11, MUTED, false)); addGap(box, 10);
        box.addView(tv("Best default: Profit Guard ON only when you set a target. Repeat Guard ON at 3 checks. Nanu stops new trades and alerts you.", 12, AMBER, false)); addGap(box, 18);
    }

    void chooseAlertSound() {
        Intent picker = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        picker.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        picker.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        picker.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        Uri existing = store.alertSoundUri == null || store.alertSoundUri.isEmpty()
                ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                : Uri.parse(store.alertSoundUri);
        picker.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing);
        startActivityForResult(picker, ALERT_SOUND_PICKER_REQUEST);
    }

    String alertSoundLabel() {
        try {
            Uri uri = store.alertSoundUri == null || store.alertSoundUri.isEmpty()
                    ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    : Uri.parse(store.alertSoundUri);
            Ringtone ringtone = uri == null ? null : RingtoneManager.getRingtone(this, uri);
            String label = ringtone == null ? "Device Default" : ringtone.getTitle(this);
            return label == null || label.trim().isEmpty() ? "Device Default" : label;
        } catch (Exception ignored) {
            return "Device Default";
        }
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
        LinearLayout head = row(); head.setGravity(Gravity.CENTER_VERTICAL); head.addView(section("PAPER POSITIONS"), new LinearLayout.LayoutParams(0, -2, 1)); head.addView(tv(store.engine.trades.size() + " Open", 13, GREEN, true)); box.addView(head); addGap(box, 8);
        if (store.engine.trades.isEmpty()) box.addView(tv("No paper position open. The app waits for a live-candle signal; no Binance position is implied.", 12, MUTED, false));
        for (NanuEngine.Trade t : store.engine.trades) {
            LinearLayout item = cardMini(); item.setOrientation(LinearLayout.VERTICAL);
            LinearLayout top = row(); top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(coinBadge(t.symbol), new LinearLayout.LayoutParams(dp(48), dp(48)));
            LinearLayout info = col(); info.addView(tv(t.symbol + "   " + t.side, 17, WHITE, true)); info.addView(tv("Internal paper position • no leverage", 12, MUTED, false)); top.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
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
        if (store.panicLatched()) {
            confirmPanicReset(this::startBot);
            return;
        }
        if ("live".equals(store.mode) && !store.liveUnlocked) { toast("Live signal scanner locked. Use Unlock LIVE after all checks pass."); activeTab=4; render(true); return; }
        if ("live".equals(store.mode) && !store.apiTradingOkForCurrentMode()) { toast("Live signal scanner blocked: run API Doctor in LIVE mode first."); activeTab=4; render(true); return; }
        store.engine.start();
        store.triggerAlert("Nanu Started", "Live candle scanner started in " + store.mode.toUpperCase(Locale.US) + ". Automatic Binance orders require the separate Automatic LIVE arm and start flow.", false, "startstop");
        startForegroundBotService();
        toast("Nanu started"); render(false);
    }
    void startForegroundBotService() { try { Intent i = new Intent(this, NanuBotService.class); if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); } catch (Exception ignored) {} }
    void stopBot() { store.engine.stop(); stopService(new Intent(this, NanuBotService.class)); store.resetOrderSafetyState("Stop button"); store.triggerAlert("Nanu Stopped", "Scanner and automatic entries stopped. Existing Binance OCO protection remains on the exchange.", false, "startstop"); toast("Nanu stopped"); render(false); }
    void panic() { store.engine.panicClose(); store.panicButtonTested = true; store.save(); stopService(new Intent(this, NanuBotService.class)); store.triggerAlert("Nanu PANIC", "Panic stopped new entries. Existing Binance OCO protection stays active; check Binance, then reset Panic before restarting the scanner.", true, "panic"); toast("Panic stop activated"); render(false); }

    void confirmPanicReset(Runnable afterReset) {
        if (!store.panicLatched()) { toast("Panic state is already clear"); return; }
        input("Reset Panic State", "Type RESET PANIC", "", false, value -> {
            if (!"RESET PANIC".equals(value.trim())) { toast("Panic state remains active"); return; }
            store.resetPanicState("Operator confirmed after Panic Stop");
            store.triggerAlert("Nanu Panic Reset", "Panic state cleared. Scanner is idle; Automatic LIVE remains disarmed and requires a fresh arm.", true, "startstop");
            toast("Panic state reset. Automatic LIVE remains disarmed.");
            if (afterReset != null) afterReset.run(); else render(true);
        });
    }

    void confirmRateLimitClear() {
        if (!store.binanceRateLimitLock) { toast("No Binance rate-limit lock is active"); return; }
        input("Clear Binance Rate Limit Lock", "Type CLEAR RATE LOCK", "", false, value -> {
            if (!"CLEAR RATE LOCK".equals(value.trim())) { toast("Rate-limit lock remains active"); return; }
            store.clearRateLimitLock("Operator confirmed after checking Binance");
            store.triggerAlert("Nanu Rate Limit Lock Cleared", "The local rate-limit lock was cleared. Run API Doctor and Automatic Preflight before any new LIVE session.", true, "api");
            toast("Rate-limit lock cleared. Run API Doctor and Preflight.");
            render(true);
        });
    }

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
        input("Unlock LIVE Control Gate", "Type UNLOCK LIVE", "", false, s -> { if ("UNLOCK LIVE".equals(s.trim())) { store.liveUnlocked=true; store.mode="live"; store.save(); store.triggerAlert("Nanu Live Signal Control Unlocked", "LIVE scanner unlocked. Automatic execution still requires its own static-IP preflight, one-time arm, and Start Automatic LIVE.", true, "live"); toast("Live scanner unlocked"); syncPortfolio(true); } else toast("Live not unlocked"); render(true); });
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
        if (!store.liveDryRunEnabled) m.append("• Keep Controlled Live Dry-Run ON for v6.2.\n");
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

    void syncPortfolio(boolean quiet) {
        if (portfolioSyncRunning) { if (!quiet) toast("Portfolio sync already running"); return; }
        if (store.apiKey.isEmpty() || store.apiSecret.isEmpty()) { if (!quiet) toast("Add Binance API key and secret first"); return; }
        portfolioSyncRunning = true;
        if (!quiet) toast("Syncing Binance Spot portfolio...");
        render(false);
        BinanceClient.syncSpotPortfolio(store, result -> runOnUiThread(() -> {
            portfolioSyncRunning = false;
            if (!quiet) alert("Nanu AI Trading Bot Portfolio", result);
            else toast(store.portfolioSyncOk ? "Spot portfolio synced" : "Spot portfolio sync failed");
            render(false);
        }));
    }

    void showPublicIp() { toast("Checking public IP..."); BinanceClient.getPublicIp(store, result -> runOnUiThread(() -> { alert("Trusted IP Helper", result); render(false); })); }

    void useCurrentPublicIp() {
        final long requestedAt = System.currentTimeMillis();
        toast("Checking current public IP...");
        BinanceClient.getPublicIp(store, result -> runOnUiThread(() -> {
            if (store.lastPublicIpCheckedMs < requestedAt || !AutoTradingPolicy.isStaticIp(store.lastPublicIp)) {
                alert("Expected Device IP Not Saved", "Nanu could not get a fresh public IP. Stay on the intended network and try again.");
                return;
            }
            store.autoTrustedStaticIp = store.lastPublicIp;
            store.autoLiveArmed = false;
            store.save();
            alert("Expected Device IP Saved", "Expected public IP: " + store.autoTrustedStaticIp + "\n\nThis does not make the address static. Add the same stable address to Binance API trusted IP restrictions, then run Check Device IP.");
            render(true);
        }));
    }

    void checkDeviceIp() {
        toast("Checking device IP...");
        BinanceClient.verifyTrustedIp(store, result -> runOnUiThread(() -> {
            String report = "Expected IP: " + (store.autoTrustedStaticIp.isEmpty() ? "Not set" : store.autoTrustedStaticIp)
                    + "\nCurrent IP: " + (store.lastPublicIp.isEmpty() ? "Unknown" : store.lastPublicIp)
                    + "\nLast check: " + publicIpAgeLabel()
                    + "\n\n" + result.report;
            alert(result.ok ? "Device IP Match" : "Device IP Mismatch", report);
            render(false);
        }));
    }

    String heartbeatAgeLabel() { return ageLabel(store.deviceLastHeartbeatMs); }
    String publicIpAgeLabel() { return ageLabel(store.lastPublicIpCheckedMs); }

    String ageLabel(long timestampMs) {
        if (timestampMs <= 0L) return "not checked";
        long seconds = Math.max(0L, (System.currentTimeMillis() - timestampMs) / 1000L);
        if (seconds < 10L) return "just now";
        if (seconds < 60L) return seconds + "s ago";
        return (seconds / 60L) + "m ago";
    }

    boolean deviceHasInternet() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= 23) {
            android.net.Network network = cm.getActiveNetwork();
            NetworkCapabilities capabilities = network == null ? null : cm.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        android.net.NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    String deviceReadinessReport() {
        boolean online = deviceHasInternet();
        PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean batterySaver = power != null && Build.VERSION.SDK_INT >= 21 && power.isPowerSaveMode();
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int plugged = battery == null ? 0 : battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        String batteryLabel = level < 0 || scale <= 0 ? "Unknown" : Math.round(level * 100f / scale) + "%" + (plugged != 0 ? " and charging" : " on battery");
        boolean heartbeatFresh = store.runtimeActive() && store.deviceLastHeartbeatMs > 0L && System.currentTimeMillis() - store.deviceLastHeartbeatMs < 12_000L;

        return "Runtime: " + store.runtimeState()
                + "\nNetwork: " + (online ? "Internet available" : "NO INTERNET")
                + "\nExpected public IP: " + (store.autoTrustedStaticIp.isEmpty() ? "Not set" : store.autoTrustedStaticIp)
                + "\nCurrent public IP: " + (store.lastPublicIp.isEmpty() ? "Not checked" : store.lastPublicIp)
                + "\nIP status: " + (AutoTradingPolicy.publicIpMatches(store.autoTrustedStaticIp, store.lastPublicIp) ? "MATCH" : "Check required or mismatch")
                + "\nBattery: " + batteryLabel
                + "\nBattery Saver: " + (batterySaver ? "ON - turn it off for an active bot" : "OFF")
                + "\nForeground heartbeat: " + heartbeatAgeLabel() + (store.runtimeActive() ? (heartbeatFresh ? " (fresh)" : " (stale - inspect app)") : "")
                + "\nUnexpected service stops: " + store.deviceUnexpectedStopCount
                + "\nLast stop note: " + store.deviceLastStopReason
                + "\n\nFor Automatic LIVE, keep this device charged, on the approved stable network, with Battery Saver off. Android can still restrict background work; the heartbeat and Telegram alerts help you notice it, but they cannot guarantee 24/7 operation.";
    }
    void testTelegram() {
        if (telegramDoctorRunning) { toast("Telegram Doctor already running"); return; }
        telegramDoctorRunning = true;
        toast("Sending Telegram test message...");
        render(false);
        TelegramClient.test(store, result -> runOnUiThread(() -> { telegramDoctorRunning = false; alert("Telegram Doctor", result); render(false); }));
    }


    String safetyReport() {
        StringBuilder sb = new StringBuilder();

        sb.append("NANU AI TRADING BOT\n");
        sb.append("Safety Report\n");
        sb.append("Generated: ").append(System.currentTimeMillis()).append("\n\n");

        sb.append("=== MODE ===\n");
        sb.append("Current mode: ").append(store.mode.toUpperCase(Locale.US)).append("\n");
        sb.append("Runtime state: ").append(store.runtimeState()).append("\n");
        sb.append("Panic state: ").append(store.panicLatched()).append("\n");
        sb.append("Live control gate unlocked: ").append(store.liveUnlocked).append("\n");
        sb.append("Live execution: manual protected orders or separately armed automatic Spot execution after live unlock, fresh sync, API Doctor, Telegram Doctor, static-IP verification, and Start Automatic LIVE.\n\n");

        sb.append("=== DEVICE EXECUTION ===\n");
        sb.append("Remote executor: not used.\n");
        sb.append("Approved pairs: BTCUSDT, ETHUSDT, BNBUSDT, SOLUSDT\n");
        sb.append("Entries today: ").append(store.liveTradesToday).append(" / ").append(store.maxLiveTradesPerDay).append("\n");
        sb.append("Current order amount: ").append(store.microLiveOrderUsdt).append(" USDT\n");
        sb.append("Manual order limit: ").append(store.manualOrderLimitUsdt).append(" USDT\n");
        sb.append("Real BUY exit protection: Binance OCO target/stop requested after a filled order.\n\n");

        sb.append("=== AUTOMATIC EXECUTION ===\n");
        sb.append("Automatic LIVE armed: ").append(store.autoLiveArmed).append("\n");
        sb.append("Automatic executor running: ").append(store.autoRunning).append("\n");
        sb.append("Automatic panic: ").append(store.autoPanic).append("\n");
        sb.append("Device heartbeat: ").append(heartbeatAgeLabel()).append("\n");
        sb.append("Unexpected device-service stops: ").append(store.deviceUnexpectedStopCount).append("\n");
        sb.append("Last device stop note: ").append(store.deviceLastStopReason).append("\n");
        sb.append("Expected static IP: ").append(store.autoTrustedStaticIp).append("\n");
        sb.append("Minimum confidence: ").append(store.autoMinConfidence).append("\n");
        sb.append("Automatic active position: ").append(store.hasAutoPosition() ? store.autoActiveSymbol : "none").append("\n");
        sb.append("Automatic OCO list: ").append(store.autoOcoOrderListId).append("\n");
        sb.append("Automatic realized P&L: ").append(String.format(Locale.US, "%+.2f", store.autoRealizedPnlUsdt)).append(" USDT\n");
        sb.append("Automatic status: ").append(store.autoStatus).append("\n\n");

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
        sb.append("Public IP: ").append(store.lastPublicIp == null ? "" : store.lastPublicIp).append("\n");
        sb.append("Expected device IP: ").append(store.autoTrustedStaticIp == null ? "" : store.autoTrustedStaticIp).append("\n\n");

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
        sb.append("Alert Sound: ").append(alertSoundLabel()).append("\n\n");

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
        sb.append("Spot equity: ").append(store.portfolioEquityLabel()).append("\n");
        sb.append("Portfolio sync age: ").append(store.portfolioAgeLabel()).append("\n");
        sb.append("Top assets: ").append(store.topPortfolioAssets).append("\n");
        if (store.portfolioWarnings != null && !store.portfolioWarnings.isEmpty()) sb.append("Portfolio warning: ").append(store.portfolioWarnings).append("\n");
        sb.append("\n");

        sb.append("=== BRAIN MEMORY ===\n");
        sb.append("Learning cycles: ").append(store.brainLearningCycles).append("\n");
        sb.append("Adaptive bias: ").append(String.format(Locale.US, "%.2f", store.brainAdaptiveBias)).append("\n");
        sb.append("Last insight: ").append(store.lastBrainInsight).append("\n\n");

        sb.append("=== CONTROLLED LIVE ===\n");
        sb.append("Compliance Guard: ").append(store.complianceGuardEnabled).append("\n");
        sb.append("Binance Test Order Mode: ").append(store.liveOrderTestMode).append("\n");
        sb.append("Real Spot Order Armed: ").append(store.liveRealOrderArmed).append("\n");
        sb.append("Manual approval required: true\n");
        sb.append("Automatic real trading: available only through the armed foreground Automatic LIVE executor\n");
        sb.append("Spot order USDT: ").append(store.microLiveOrderUsdt).append("\n");
        sb.append("Manual order limit USDT: ").append(store.manualOrderLimitUsdt).append("\n");
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

    String developerReport() { return "Nanu AI Trading Bot\nRemote executor: not used\nAutomatic executor: " + store.autoStatus + "\nActive scanner pair: " + store.scalperSymbol + "\nLatest local signal: " + store.lastScalperSignal + " / " + store.lastScalperConfidence + "\nSpot equity: " + store.portfolioEquityLabel() + "\nReal entries: " + store.liveTradesToday + "/" + store.maxLiveTradesPerDay + "\nAutomatic position: " + (store.hasAutoPosition() ? store.autoActiveSymbol + " / OCO " + store.autoOcoOrderListId : "none") + "\nOrder limit: " + store.manualOrderLimitUsdt + " USDT\nAPI last mode: " + store.lastApiMode + "\nAPI private OK: " + store.lastApiPrivateOk + "\nAPI can trade: " + store.lastApiCanTrade + "\nBinance API secret: HIDDEN"; }

    TextView tv(String s, int sp, int color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setIncludeFontPadding(true); t.setLineSpacing(dp(2), 1.0f); t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); return t; }
    TextView label(String s) { return tv(s, 12, MUTED, true); }
    TextView section(String s) { return tv(s, 17, WHITE, true); }
    TextView screenTitle(String s) { return tv(s, 24, WHITE, true); }
    TextView big(String s, int color, int sp) { TextView t = tv(s, sp, color, true); t.setTypeface(Typeface.create("monospace", Typeface.BOLD)); return t; }
    LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    LinearLayout col() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    void addGap(int h) { SpaceView sp = new SpaceView(this); root.addView(sp, new LinearLayout.LayoutParams(1, dp(h))); }
    void addGap(LinearLayout parent, int h) { SpaceView sp = new SpaceView(this); parent.addView(sp, new LinearLayout.LayoutParams(1, dp(h))); }

    LinearLayout cardBox() { LinearLayout l = col(); l.setPadding(dp(16), dp(14), dp(16), dp(14)); l.setBackground(bg(CARD, Color.rgb(16, 116, 139), 8)); return l; }
    LinearLayout cardMini() { LinearLayout l = row(); l.setPadding(dp(12), dp(10), dp(12), dp(10)); l.setBackground(bg(CARD2, Color.rgb(18, 86, 108), 6)); return l; }
    GradientDrawable bg(int fill, int stroke, int rad) { GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(rad)); g.setStroke(dp(1.2f), stroke); return g; }

    TextView pill(String s, int color, int sp) { TextView t = tv(s, sp, color, true); t.setGravity(Gravity.CENTER); t.setPadding(dp(10), dp(7), dp(10), dp(7)); t.setBackground(bg(Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)), color, 8)); return t; }
    Button actionButton(String s, int color, View.OnClickListener l) { Button b = new Button(this); b.setText(s); b.setTextSize(15); b.setAllCaps(false); b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); b.setTextColor(color == GREEN ? BG : WHITE); b.setBackground(bg(color == GREEN ? GREEN : (color == RED ? Color.rgb(104, 26, 34) : CARD2), color, 8)); b.setOnClickListener(l); return b; }
    TextView modeButton(String title, String sub, String mode) { boolean selected = mode.equals(store.mode); TextView t = tv(title + "\n" + sub, 15, selected ? BG : WHITE, true); t.setGravity(Gravity.CENTER); t.setTextAlignment(View.TEXT_ALIGNMENT_CENTER); t.setBackground(bg(selected ? GREEN : CARD2, selected ? GREEN : CYAN, 8)); t.setOnClickListener(v -> setMode(mode)); return t; }

    void addMetric(LinearLayout parent, String title, String v, String sub, int color) { LinearLayout m = cardBox(); m.addView(label(title)); m.addView(tv(v, 19, color, true)); m.addView(tv(sub, 13, MUTED, true)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(dp(3), 0, dp(3), 0); parent.addView(m, lp); }
    TextView coinBadge(String symbol) { String c = symbol.length() >= 3 ? symbol.substring(0, Math.min(3, symbol.length())) : symbol; TextView t = tv(c, 15, Color.WHITE, true); t.setGravity(Gravity.CENTER); int fill = symbol.startsWith("BTC") ? Color.rgb(247,147,26) : symbol.startsWith("ETH") ? Color.rgb(86,112,255) : symbol.startsWith("SOL") ? Color.rgb(126,70,255) : symbol.startsWith("BNB") ? Color.rgb(240,190,20) : CYAN; t.setBackground(bg(fill, fill, 100)); return t; }

    int pnlColor() { return store.engine.todayPnl < -15 ? RED : GREEN; }
    String formatMoney(double v) { return String.format(Locale.US, "%+.2f", v); }
    String percent(double v) { return String.format(Locale.US, "%+.2f%%", v); }
    String moneyOrDash(double v) { return Double.isNaN(v) || Double.isInfinite(v) ? "--" : String.format(Locale.US, "%.4f", v); }
    void input(String title, String hint, String old, boolean secret, InputCb cb) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(old);
        e.setSingleLine(false);
        e.setTextColor(WHITE);
        e.setHintTextColor(MUTED);
        e.setInputType(secret ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD) : InputType.TYPE_CLASS_TEXT);
        LinearLayout box = dialogBox(title);
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog d = new AlertDialog.Builder(this).setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save", (dialog,w) -> cb.ok(e.getText().toString())).create();
        d.setOnShowListener(dialog -> styleDialog(d));
        d.show();
        styleDialog(d);
    }
    interface InputCb { void ok(String s); }
    void alert(String title, String msg) {
        LinearLayout box = dialogBox(title);
        TextView body = tv(msg == null ? "" : msg, 14, WHITE, false);
        body.setTextIsSelectable(true);
        body.setLineSpacing(dp(2), 1.0f);
        body.setPadding(0, dp(8), 0, dp(8));
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(false);
        sv.addView(body, new ScrollView.LayoutParams(-1, -2));
        box.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog d = new AlertDialog.Builder(this).setView(box).setPositiveButton("OK", null).create();
        d.setOnShowListener(dialog -> styleDialog(d));
        d.show();
        styleDialog(d);
    }
    LinearLayout dialogBox(String title) {
        LinearLayout box = col();
        box.setPadding(dp(22), dp(18), dp(22), dp(10));
        box.setBackground(bg(CARD, CYAN, 22));
        TextView t = tv(title == null ? "Nanu" : title, 22, WHITE, true);
        t.setPadding(0, 0, 0, dp(8));
        box.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }
    void styleDialog(AlertDialog d) {
        if (d == null || d.getWindow() == null) return;
        d.getWindow().setBackgroundDrawable(bg(Color.TRANSPARENT, Color.TRANSPARENT, 24));
        Button ok = d.getButton(AlertDialog.BUTTON_POSITIVE);
        Button cancel = d.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (ok != null) { ok.setTextColor(CYAN); ok.setTypeface(Typeface.DEFAULT, Typeface.BOLD); }
        if (cancel != null) { cancel.setTextColor(AMBER); cancel.setTypeface(Typeface.DEFAULT, Typeface.BOLD); }
    }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    void footer() { addGap(14); TextView f = tv("Nanu AI Trading Bot • Closed-Candle Scanner • Automatic Protected Spot", 11, MUTED, false); f.setGravity(Gravity.CENTER); root.addView(f); }
    public static class SpaceView extends View { public SpaceView(android.content.Context c) { super(c); } }
}
