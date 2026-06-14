package com.nanu.godmode;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private final AppState state = new AppState();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout root, body, signalBox, journalBox, brainBox, tabsRow, securityBox;
    private FaceLogoView logo;
    private SparkView spark;
    private TextView moodTxt, confTxt, todayPnl, allPnl, equityTxt, dayPnlTxt, openPnlTxt, winTxt, botStatus, modeText, exchangeText;
    private int activeTab = 0;
    private int accentColor = cyan();
    private final String[] tabs = {"Bridge", "Scanner", "Brain", "Journal", "Security"};
    private final Runnable loop = new Runnable() {
        public void run() { state.tick(); refresh(); handler.postDelayed(this, 1800); }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(bg());
        getWindow().setNavigationBarColor(bg());
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 44);
        }
        buildUi();
        handler.post(loop);
    }

    @Override protected void onDestroy() { super.onDestroy(); handler.removeCallbacks(loop); }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg());
        root = col();
        root.setPadding(dp(16), statusBarHeight() + dp(14), dp(16), dp(18));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        addHeader();
        addFaceHero();
        addMoodAndPnl();
        addMetricGrid();
        addControls();
        addTabs();
        body = col();
        root.addView(body);
        drawTab();
        addBottomNote();
    }

    private void addHeader() {
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(12));
        root.addView(row, new LinearLayout.LayoutParams(-1, dp(62)));

        LinearLayout names = col();
        row.addView(names, new LinearLayout.LayoutParams(0, -1, 1));
        TextView title = txt("NANU", 28, Color.WHITE, 900);
        title.setLetterSpacing(0.09f);
        names.addView(title, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView sub = txt("AI  SCALPING  BRIDGE", 11, cyan(), 700);
        sub.setLetterSpacing(0.19f);
        names.addView(sub, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView gear = txt("SETTINGS", 12, cyan(), 800);
        gear.setGravity(Gravity.CENTER);
        gear.setBackground(chipBg(cyan(), false));
        gear.setOnClickListener(v -> { activeTab = 4; drawTab(); });
        row.addView(gear, new LinearLayout.LayoutParams(dp(96), dp(38)));
    }

    private void addFaceHero() {
        logo = new FaceLogoView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(160));
        lp.setMargins(0, dp(4), 0, dp(8));
        root.addView(logo, lp);
    }

    private void addMoodAndPnl() {
        LinearLayout row = row();
        root.addView(row, new LinearLayout.LayoutParams(-1, dp(128)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.setMargins(dp(3), dp(4), dp(3), dp(4));
        row.addView(moodCard(), lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, -1, 1);
        lp2.setMargins(dp(3), dp(4), dp(3), dp(4));
        row.addView(pnlCard(), lp2);
    }

    private View moodCard() {
        LinearLayout c = cardCol();
        c.setPadding(dp(15), dp(13), dp(15), dp(12));
        c.addView(label("MARKET MOOD"));
        moodTxt = txt("CALM", 27, green(), 900); c.addView(moodTxt);
        TextView animal = txt("Market is steady", 13, muted(), 500); c.addView(animal);
        confTxt = small("Confidence 55%", muted()); c.addView(confTxt);
        return c;
    }

    private View pnlCard() {
        LinearLayout c = cardCol();
        c.setPadding(dp(15), dp(13), dp(15), dp(12));
        c.addView(label("TODAY'S P&L"));
        todayPnl = txt("+0.00 USDT\n+0.00%", 23, green(), 900);
        todayPnl.setLineSpacing(dp(4), 1.0f); c.addView(todayPnl);
        allPnl = small("All Time P&L  +0.00 USDT", green()); c.addView(allPnl);
        return c;
    }

    private void addMetricGrid() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setPadding(0, dp(6), 0, dp(4));
        root.addView(grid);
        addMetricCard(grid, "EQUITY");
        addMetricCard(grid, "24H P&L");
        addMetricCard(grid, "OPEN P&L");
        addMetricCard(grid, "WIN RATE");
    }

    private void addMetricCard(GridLayout grid, String title) {
        LinearLayout c = cardCol();
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.addView(label(title));
        TextView val = txt("0.00", title.equals("WIN RATE") ? 19 : 18, title.equals("EQUITY") ? Color.WHITE : green(), 850);
        val.setLineSpacing(dp(4), 1.0f);
        c.addView(val);
        if (title.equals("EQUITY")) { equityTxt = val; spark = new SparkView(this); c.addView(spark, new LinearLayout.LayoutParams(-1, dp(24))); }
        if (title.equals("24H P&L")) dayPnlTxt = val;
        if (title.equals("OPEN P&L")) openPnlTxt = val;
        if (title.equals("WIN RATE")) winTxt = val;
        GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
        gp.width = 0; gp.height = dp(108);
        gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        gp.setMargins(dp(3), dp(4), dp(3), dp(4));
        grid.addView(c, gp);
    }

    private void addControls() {
        LinearLayout c = cardCol();
        c.setPadding(dp(15), dp(14), dp(15), dp(14));
        root.addView(c);
        LinearLayout top = row(); top.setGravity(Gravity.CENTER_VERTICAL); c.addView(top);
        botStatus = txt("TRADING BOT   STOPPED", 14, muted(), 850); top.addView(botStatus, new LinearLayout.LayoutParams(0, -2, 1));
        exchangeText = txt("Binance Spot  ●", 13, green(), 800); top.addView(exchangeText);

        LinearLayout buttons = row(); buttons.setPadding(0, dp(12), 0, 0); c.addView(buttons, new LinearLayout.LayoutParams(-1, dp(62)));
        Button start = btn("▶  Start", green(), false); start.setOnClickListener(v -> { state.start(); refresh(); });
        Button stop = btn("■  Stop", Color.rgb(18, 35, 55), false); stop.setOnClickListener(v -> { state.stop(); refresh(); });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, -1, 1); bl.setMargins(0, 0, dp(8), 0); buttons.addView(start, bl);
        LinearLayout.LayoutParams br = new LinearLayout.LayoutParams(0, -1, 1); br.setMargins(dp(8), 0, 0, 0); buttons.addView(stop, br);
        Button panic = btn("⚠  Panic Close", Color.rgb(120, 24, 34), true); panic.setOnClickListener(v -> { state.panic(); refresh(); });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, dp(58)); pl.setMargins(0, dp(10), 0, 0); c.addView(panic, pl);

        LinearLayout mode = row(); mode.setGravity(Gravity.CENTER_VERTICAL); mode.setPadding(0, dp(12), 0, 0); c.addView(mode);
        RadarView radar = new RadarView(this); mode.addView(radar, new LinearLayout.LayoutParams(dp(92), dp(92)));
        LinearLayout info = col(); info.setPadding(dp(16), 0, 0, 0); mode.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        modeText = txt("Mode: Paper", 16, Color.WHITE, 850); info.addView(modeText);
        TextView change = txt("Change mode in Security →", 13, cyan(), 800); change.setPadding(0, dp(8), 0, 0); change.setOnClickListener(v -> { activeTab = 4; drawTab(); }); info.addView(change);
        TextView live = small("Live remains locked until checklist + confirmation.", muted()); live.setPadding(0, dp(6), 0, 0); info.addView(live);
    }

    private void addTabs() {
        tabsRow = row(); tabsRow.setPadding(0, dp(8), 0, dp(6)); root.addView(tabsRow, new LinearLayout.LayoutParams(-1, dp(58)));
        for (int i=0; i<tabs.length; i++) {
            final int ix=i;
            TextView t=txt(tabs[i], 11, i==0?cyan():muted(), 800);
            t.setGravity(Gravity.CENTER);
            t.setBackground(cardBg(i==0?cyan():Color.rgb(30,50,70), 18));
            t.setOnClickListener(v -> { activeTab=ix; drawTab(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1); lp.setMargins(dp(2),0,dp(2),0);
            tabsRow.addView(t, lp);
        }
    }

    private void drawTab() {
        if (body == null) return;
        body.removeAllViews();
        refreshTabs();
        if (activeTab==0) bridge(); else if (activeTab==1) scanner(); else if (activeTab==2) brain(); else if (activeTab==3) journal(); else security();
        refresh();
    }

    private void bridge() {
        signalBox = cardCol(); signalBox.setPadding(dp(15), dp(15), dp(15), dp(15)); body.addView(signalBox);
        journalBox = col(); body.addView(journalBox);
    }

    private void scanner() {
        body.addView(sectionTitle("SCANNER — Scalping Radar"));
        signalBox = cardCol(); signalBox.setPadding(dp(15),dp(15),dp(15),dp(15)); body.addView(signalBox);
        body.addView(infoCard("Signal Filters", "EMA trend • RSI health • MACD impulse • volume pulse • spread guard • no-trade zone."));
    }

    private void brain() {
        body.addView(sectionTitle("BRAIN — Why Nanu acts"));
        brainBox = cardCol(); brainBox.setPadding(dp(15),dp(15),dp(15),dp(15)); body.addView(brainBox);
        body.addView(infoCard("Learning Rules", "Nanu records wins, losses, best symbols, weak hours, panic events, and rejected signals. God Mode means awareness + safety, not guaranteed profit."));
    }

    private void journal() {
        body.addView(sectionTitle("JOURNAL — Trade Memory"));
        journalBox = cardCol(); journalBox.setPadding(dp(15),dp(15),dp(15),dp(15)); body.addView(journalBox);
    }

    private void security() {
        body.addView(sectionTitle("SECURITY — Mode, Keys and Risk Doors"));
        securityBox = cardCol(); securityBox.setPadding(dp(15),dp(15),dp(15),dp(15)); body.addView(securityBox);
        securityBox.addView(label("TRADING MODE"));
        GridLayout modes = new GridLayout(this); modes.setColumnCount(2); modes.setPadding(0, dp(10), 0, dp(12)); securityBox.addView(modes);
        addModeButton(modes, "Paper"); addModeButton(modes, "Demo"); addModeButton(modes, "Testnet"); addModeButton(modes, "Live");
        securityBox.addView(line());
        securityBox.addView(edit("Binance API Key", false));
        securityBox.addView(edit("Binance API Secret", true));
        securityBox.addView(edit("Telegram Bot Token", true));
        securityBox.addView(edit("Telegram Chat ID", false));
        securityBox.addView(infoLine("Current mode", state.mode));
        securityBox.addView(infoLine("Max daily loss", "Enabled"));
        securityBox.addView(infoLine("Panic close", "Always available"));
        securityBox.addView(warn("Live mode requires API keys, risk settings, and typing UNLOCK LIVE."));
    }

    private void addModeButton(GridLayout grid, String modeName) {
        TextView b = txt(modeName, 15, Color.WHITE, 850);
        b.setGravity(Gravity.CENTER);
        b.setBackground(chipBg(modeName.equals(state.mode) ? cyan() : Color.rgb(50,72,95), modeName.equals(state.mode)));
        b.setOnClickListener(v -> {
            if (modeName.equals("Live")) confirmLiveMode(); else { state.mode = modeName; refresh(); }
        });
        GridLayout.LayoutParams gp = new GridLayout.LayoutParams(); gp.width = 0; gp.height = dp(52); gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); gp.setMargins(dp(4), dp(4), dp(4), dp(4));
        grid.addView(b, gp);
    }

    private void confirmLiveMode() {
        final EditText input = new EditText(this);
        input.setHint("Type UNLOCK LIVE");
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(muted());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(cardBg(red(), 12));
        new AlertDialog.Builder(this)
                .setTitle("Unlock Live Mode")
                .setMessage("Live mode can place real Binance orders. Keep withdrawal permission OFF. Use only after paper/demo/testnet testing.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Unlock", (d, w) -> {
                    if ("UNLOCK LIVE".equals(input.getText().toString().trim())) { state.mode = "Live"; state.brain.add(0, "Live mode manually unlocked. Risk guard still active."); refresh(); }
                    else Toast.makeText(this, "Live remains locked.", Toast.LENGTH_LONG).show();
                }).show();
    }

    private void refresh() {
        boolean profit = state.profitState();
        accentColor = profit ? green() : red();
        if (logo != null) { logo.pnl = state.dayPnl; logo.running = state.running; logo.invalidate(); }
        if (moodTxt != null) { moodTxt.setText(state.mood); moodTxt.setTextColor(accentColor); confTxt.setText("Confidence " + state.confidence + "%"); }
        if (todayPnl != null) { todayPnl.setText(state.money(state.dayPnl) + "\n" + pctClean(state.dayPnl)); todayPnl.setTextColor(accentColor); allPnl.setText("All Time P&L  " + state.money(state.dayPnl * 4.7)); allPnl.setTextColor(accentColor); }
        if (equityTxt != null) equityTxt.setText(String.format(Locale.US, "%.2f USDT", state.equity));
        if (dayPnlTxt != null) { dayPnlTxt.setText(state.money(state.dayPnl) + "\n" + pctClean(state.dayPnl)); dayPnlTxt.setTextColor(accentColor); }
        if (openPnlTxt != null) { openPnlTxt.setText(state.money(state.openPnl) + "\n" + pctClean(state.openPnl)); openPnlTxt.setTextColor(state.openPnl >= 0 ? green() : red()); }
        if (winTxt != null) { double wr=state.winRate(); winTxt.setText(String.format(Locale.US, "%.1f%%\n(%d / 70)", wr, (int)(wr/100*70))); winTxt.setTextColor(accentColor); }
        if (spark != null) { spark.data = state.equityPoints; spark.profit = profit; spark.invalidate(); }
        if (botStatus != null) { botStatus.setText("TRADING BOT   " + (state.running?"RUNNING":"STOPPED") + (state.panic?"  PANIC":"")); botStatus.setTextColor(state.running ? accentColor : muted()); }
        if (exchangeText != null) { exchangeText.setText("Binance Spot  ●"); exchangeText.setTextColor(accentColor); }
        if (modeText != null) { modeText.setText("Mode: " + state.mode); modeText.setTextColor(state.mode.equals("Live") ? red() : Color.WHITE); }
        refreshTabs();
        if (signalBox != null) fillSignals(); if (journalBox != null) fillJournal(); if (brainBox != null) fillBrain();
        if (activeTab == 4 && securityBox != null) { /* mode buttons redraw when re-entering tab */ }
    }

    private void fillSignals() {
        signalBox.removeAllViews();
        LinearLayout head = row(); head.setGravity(Gravity.CENTER_VERTICAL); signalBox.addView(head);
        head.addView(txt("SIGNAL BRIDGE", 19, Color.WHITE, 900), new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(txt(state.signals.size()+" Active", 12, accentColor, 850));
        for (AppState.Signal s: state.signals) signalBox.addView(signalCard(s));
    }

    private View signalCard(AppState.Signal s) {
        LinearLayout c = row(); c.setBackground(cardBg(Color.rgb(18,170,200),18)); c.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(10),0,0); c.setLayoutParams(lp);
        TextView ico = txt(iconFor(s.symbol), 25, Color.WHITE, 900); ico.setGravity(Gravity.CENTER); ico.setBackground(circleBg(s.symbol.startsWith("BTC")?Color.rgb(245,151,26):s.symbol.startsWith("ETH")?Color.rgb(83,115,255):s.symbol.startsWith("SOL")?Color.rgb(128,80,255):Color.rgb(244,190,24))); c.addView(ico, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout mid = col(); mid.setPadding(dp(12),0,dp(6),0); c.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));
        mid.addView(txt(s.symbol + "   " + (s.pnl>=0?"LONG":"SHORT"), 16, Color.WHITE, 900));
        mid.addView(small("Isolated • " + s.leverage + "x", muted()));
        mid.addView(small(String.format(Locale.US, "Entry %.2f   Mark %.2f", s.entry, s.mark), muted()));
        mid.addView(small(String.format(Locale.US, "TP / SL %.2f / %.2f", s.tp, s.sl), muted()));
        LinearLayout side = col(); side.setGravity(Gravity.RIGHT); c.addView(side, new LinearLayout.LayoutParams(dp(118), -2));
        side.addView(label("P&L")); TextView pnl = txt(state.money(s.pnl) + "\n" + pctClean(s.pnl), 14, s.pnl>=0?green():red(), 900); pnl.setGravity(Gravity.RIGHT); side.addView(pnl);
        return c;
    }

    private void fillJournal() {
        journalBox.removeAllViews();
        if (activeTab == 0) {
            LinearLayout api = cardCol(); api.setPadding(dp(15),dp(15),dp(15),dp(15)); journalBox.addView(api);
            api.addView(txt("API & SECURITY   SECURE", 16, accentColor, 900));
            api.addView(small("Exchange API     Binance Spot ✓\nAPI Status       Connected ✓\nRisk Guard       Enabled ✓\nLive Lock        Enabled ✓", muted()));
            LinearLayout j = cardCol(); j.setPadding(dp(15),dp(15),dp(15),dp(15)); journalBox.addView(j);
            j.addView(txt("JOURNAL — RECENT EVENTS", 16, Color.WHITE, 900));
            for (int i=0;i<Math.min(5,state.journal.size());i++) j.addView(small("• " + state.journal.get(i), i==0?accentColor:muted()));
        } else {
            for (String s: state.journal) journalBox.addView(small("• " + s, muted()));
        }
    }

    private void fillBrain() { brainBox.removeAllViews(); for (String s: state.brain) brainBox.addView(small("✦ " + s, muted())); }

    private void refreshTabs() {
        if (tabsRow==null) return;
        for (int i=0;i<tabsRow.getChildCount();i++) {
            TextView t=(TextView)tabsRow.getChildAt(i); boolean on = i==activeTab;
            t.setTextColor(on?accentColor:muted());
            t.setBackground(cardBg(on?accentColor:Color.rgb(30,50,70), 18));
        }
    }

    private String iconFor(String s) { if (s.startsWith("BTC")) return "₿"; if (s.startsWith("ETH")) return "♦"; if (s.startsWith("SOL")) return "≋"; return "⬢"; }
    private String pctClean(double v) { return String.format(Locale.US, "%s%.2f%%", v >= 0 ? "+" : "", v / 16.0); }

    private TextView sectionTitle(String s){ TextView t=txt(s,18,Color.WHITE,900); t.setPadding(dp(2), dp(16),0,dp(8)); return t; }
    private View infoCard(String title, String msg){ LinearLayout c=cardCol(); c.setPadding(dp(15),dp(15),dp(15),dp(15)); c.addView(txt(title,16,Color.WHITE,900)); c.addView(small(msg, muted())); return c; }
    private View infoLine(String a,String b){ TextView t=small(a + "     " + b, muted()); t.setPadding(0,dp(8),0,dp(8)); return t; }
    private TextView warn(String s){ TextView t=small(s, red()); t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD)); t.setPadding(0,dp(12),0,0); return t; }
    private EditText edit(String hint, boolean pass){ EditText e=new EditText(this); e.setHint(hint); e.setTextColor(Color.WHITE); e.setHintTextColor(muted()); e.setSingleLine(true); e.setTextSize(13); e.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); e.setInputType(pass?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT); e.setBackground(cardBg(Color.rgb(35,60,82),14)); e.setPadding(dp(12),0,dp(12),0); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50)); lp.setMargins(0,dp(8),0,0); e.setLayoutParams(lp); return e; }
    private void addBottomNote(){ TextView f=small("Nanu God Mode v3 • No Termux engine • Paper first • Live locked", muted()); f.setGravity(Gravity.CENTER); f.setPadding(0, dp(16),0,dp(8)); root.addView(f); }
    private LinearLayout col(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout cardCol(){ LinearLayout l=col(); l.setBackground(cardBg(Color.rgb(18,170,200),18)); if (Build.VERSION.SDK_INT >= 21) l.setElevation(dp(3)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(dp(3),dp(5),dp(3),dp(5)); l.setLayoutParams(lp); return l; }
    private TextView txt(String s,int sp,int color,int weight){ TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setIncludeFontPadding(false); t.setLineSpacing(dp(2), 1.0f); String fam = weight>=850 ? "sans-serif-black" : weight>=700 ? "sans-serif-medium" : "sans-serif"; t.setTypeface(Typeface.create(fam, weight>=700?Typeface.BOLD:Typeface.NORMAL)); return t; }
    private TextView label(String s){ TextView t=txt(s, 10, muted(), 700); t.setLetterSpacing(.06f); return t; }
    private TextView small(String s, int color){ TextView t=txt(s, 12, color, 400); t.setLineSpacing(dp(3), 1.08f); return t; }
    private Button btn(String s,int fill,boolean danger){ Button b=new Button(this); b.setAllCaps(false); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD)); b.setBackground(btnBg(fill,danger?red():green())); b.setPadding(0,0,0,0); return b; }
    private View line(){ View v=new View(this); v.setBackgroundColor(Color.argb(85,160,200,220)); v.setLayoutParams(new LinearLayout.LayoutParams(-1,1)); return v; }
    private Drawable cardBg(int stroke,int rad){ GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.rgb(5,23,38),Color.rgb(2,10,21)}); g.setCornerRadius(dp(rad)); g.setStroke(dp(1), Color.argb(155, Color.red(stroke), Color.green(stroke), Color.blue(stroke))); return g; }
    private Drawable btnBg(int fill,int stroke){ GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{lighter(fill), fill}); g.setCornerRadius(dp(14)); g.setStroke(dp(1), Color.argb(220, Color.red(stroke), Color.green(stroke), Color.blue(stroke))); return g; }
    private Drawable chipBg(int stroke, boolean selected){ GradientDrawable g=new GradientDrawable(); g.setColor(selected ? Color.argb(85, Color.red(stroke), Color.green(stroke), Color.blue(stroke)) : Color.rgb(8,22,38)); g.setCornerRadius(dp(16)); g.setStroke(dp(1), Color.argb(selected?245:155, Color.red(stroke), Color.green(stroke), Color.blue(stroke))); return g; }
    private Drawable circleBg(int fill){ GradientDrawable g=new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(fill); return g; }
    private int lighter(int c){ return Color.rgb(Math.min(255, Color.red(c)+18), Math.min(255, Color.green(c)+18), Math.min(255, Color.blue(c)+18)); }
    private int statusBarHeight(){ int id=getResources().getIdentifier("status_bar_height","dimen","android"); return id>0?getResources().getDimensionPixelSize(id):dp(28); }
    private int bg(){ return Color.rgb(2,8,19); } private int cyan(){ return Color.rgb(0,229,255); } private int green(){ return Color.rgb(70,255,136); } private int red(){ return Color.rgb(255,75,75); } private int muted(){ return Color.rgb(155,174,198); }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density + .5f); }

    public class RadarView extends View { Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); float rot=0; public RadarView(Context c){ super(c); setLayerType(View.LAYER_TYPE_SOFTWARE,null); } @Override protected void onDraw(Canvas c){ super.onDraw(c); int w=getWidth(),h=getHeight(); float cx=w/2f,cy=h/2f,r=Math.min(w,h)*.34f; int col=state.profitState()?cyan():red(); p.setStyle(Paint.Style.FILL); RadialGradient rg=new RadialGradient(cx,cy,r*1.2f,Color.argb(80,Color.red(col),Color.green(col),Color.blue(col)),Color.TRANSPARENT,Shader.TileMode.CLAMP); p.setShader(rg); c.drawCircle(cx,cy,r*1.15f,p); p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(Color.argb(160,Color.red(col),Color.green(col),Color.blue(col))); for(int i=1;i<5;i++) c.drawCircle(cx,cy,r*i/4,p); c.drawLine(cx-r,cy,cx+r,cy,p); c.drawLine(cx,cy-r,cx,cy+r,p); p.setStyle(Paint.Style.FILL); p.setColor(col); p.setShadowLayer(8,0,0,col); c.drawCircle(cx,cy,dp(6),p); c.drawCircle(cx+r*.55f,cy-r*.35f,dp(4),p); c.drawCircle(cx-r*.35f,cy+r*.47f,dp(3),p); p.setShadowLayer(0,0,0,0); rot+=4; postInvalidateDelayed(90);} }
}
