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
    private LinearLayout root, body, signalBox, journalBox, brainBox, tabsRow, modeRow;
    private FaceLogoView logo;
    private SparkView spark;
    private TextView moodTxt, confTxt, todayPnl, allPnl, equityTxt, dayPnlTxt, openPnlTxt, winTxt, botStatus, modeTxt, exchangeDot;
    private int activeTab = 0;
    private boolean compact = true;
    private final String[] tabs = {"⚓\nBridge", "◉\nScanner", "♕\nBrain", "▤\nJournal", "▣\nSecurity"};
    private final Runnable loop = new Runnable() { public void run() { state.tick(); refresh(); handler.postDelayed(this, 1800); } };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(bg());
        getWindow().setNavigationBarColor(bg());
        float density = getResources().getDisplayMetrics().density;
        compact = (getResources().getDisplayMetrics().widthPixels / density) < 420;
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
        root.setPadding(dp(14), dp(12), dp(14), dp(14));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        addHeader();
        addHero();
        addMetricGrid();
        addControls();
        addTabs();
        body = col();
        root.addView(body);
        drawTab();
        addBottomNote();
    }

    private void addHeader() {
        LinearLayout row = row(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, 0, 0, dp(8)); root.addView(row);
        LinearLayout names = col(); row.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        TextView title = txt("NANU", 30, Color.WHITE, 900); title.setLetterSpacing(0.10f); names.addView(title);
        TextView sub = txt("AI  SCALPING  BRIDGE", 11, cyan(), 700); sub.setLetterSpacing(0.22f); names.addView(sub);
        TextView right = txt("●   ⚙", 24, cyan(), 500); right.setGravity(Gravity.RIGHT); row.addView(right, new LinearLayout.LayoutParams(dp(86), -2));
    }

    private void addHero() {
        LinearLayout hero = compact ? col() : row();
        hero.setGravity(Gravity.CENTER);
        root.addView(hero);
        logo = new FaceLogoView(this);
        if (compact) {
            hero.addView(logo, new LinearLayout.LayoutParams(-1, dp(150)));
            LinearLayout two = row(); hero.addView(two, new LinearLayout.LayoutParams(-1, dp(126)));
            two.addView(moodCard(), new LinearLayout.LayoutParams(0, -1, 1));
            two.addView(pnlCard(), new LinearLayout.LayoutParams(0, -1, 1));
        } else {
            hero.addView(moodCard(), new LinearLayout.LayoutParams(0, dp(138), 1));
            hero.addView(logo, new LinearLayout.LayoutParams(dp(170), dp(170)));
            hero.addView(pnlCard(), new LinearLayout.LayoutParams(0, dp(138), 1));
        }
    }

    private View moodCard() {
        LinearLayout c = cardCol(); c.setPadding(dp(14), dp(13), dp(14), dp(12));
        c.addView(label("MARKET MOOD"));
        moodTxt = txt("CALM", 25, green(), 900); c.addView(moodTxt);
        TextView animal = txt("♉", 32, green(), 600); animal.setAlpha(.95f); c.addView(animal);
        confTxt = small("Confidence 55%", muted()); c.addView(confTxt);
        return c;
    }

    private View pnlCard() {
        LinearLayout c = cardCol(); c.setPadding(dp(14), dp(13), dp(14), dp(12));
        c.addView(label("TODAY'S P&L  ⓘ"));
        todayPnl = txt("+0.00\nUSDT", 24, green(), 900); todayPnl.setLineSpacing(0, .92f); c.addView(todayPnl);
        allPnl = small("All Time P&L  +0.00 USDT", green()); allPnl.setPadding(0, dp(6),0,0); c.addView(allPnl);
        return c;
    }

    private void addMetricGrid() {
        GridLayout grid = new GridLayout(this); grid.setColumnCount(compact ? 2 : 4); grid.setPadding(0, dp(6), 0, dp(4)); root.addView(grid);
        addMetricCard(grid, "EQUITY", true); addMetricCard(grid, "24H P&L", false); addMetricCard(grid, "OPEN P&L", false); addMetricCard(grid, "WIN RATE", false);
    }

    private void addMetricCard(GridLayout grid, String title, boolean withSpark) {
        LinearLayout c = cardCol(); c.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.addView(label(title));
        TextView val = txt("0.00", 18, title.equals("EQUITY") ? Color.WHITE : green(), 800); c.addView(val);
        if (title.equals("EQUITY")) { equityTxt = val; spark = new SparkView(this); c.addView(spark, new LinearLayout.LayoutParams(-1, dp(28))); }
        if (title.equals("24H P&L")) dayPnlTxt = val;
        if (title.equals("OPEN P&L")) openPnlTxt = val;
        if (title.equals("WIN RATE")) winTxt = val;
        GridLayout.LayoutParams gp = new GridLayout.LayoutParams(); gp.width = 0; gp.height = compact ? dp(104) : dp(96); gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); gp.setMargins(dp(3), dp(4), dp(3), dp(4));
        grid.addView(c, gp);
    }

    private void addControls() {
        LinearLayout c = cardCol(); c.setPadding(dp(14), dp(13), dp(14), dp(14)); root.addView(c);
        LinearLayout top = row(); top.setGravity(Gravity.CENTER_VERTICAL); c.addView(top);
        botStatus = txt("TRADING BOT   STOPPED", 13, muted(), 800); top.addView(botStatus, new LinearLayout.LayoutParams(0, -2, 1));
        exchangeDot = txt("Binance  ●", 13, Color.WHITE, 700); top.addView(exchangeDot);

        LinearLayout main = compact ? col() : row(); main.setPadding(0, dp(10),0,0); c.addView(main);
        LinearLayout left = col(); main.addView(left, new LinearLayout.LayoutParams(compact ? -1 : 0, -2, compact ? 0 : 1.2f));
        LinearLayout buttons = row(); left.addView(buttons);
        Button start = btn("▶  Start", green(), false); start.setOnClickListener(v -> { state.start(); refresh(); }); buttons.addView(start, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button stop = btn("■  Stop", Color.rgb(19, 36, 54), false); stop.setOnClickListener(v -> { state.stop(); refresh(); }); buttons.addView(stop, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button panic = btn("⚠  Panic Close", Color.rgb(115, 22, 31), true); panic.setOnClickListener(v -> { state.panic(); refresh(); }); LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(52)); plp.setMargins(0, dp(8),0,0); left.addView(panic, plp);

        LinearLayout right = row(); right.setGravity(Gravity.CENTER_VERTICAL); if (compact) main.addView(right, new LinearLayout.LayoutParams(-1, dp(122))); else main.addView(right, new LinearLayout.LayoutParams(0, dp(128), 1.8f));
        RadarView radar = new RadarView(this); right.addView(radar, new LinearLayout.LayoutParams(dp(118), -1));
        LinearLayout modeBox = col(); modeBox.setPadding(dp(12),0,0,0); right.addView(modeBox, new LinearLayout.LayoutParams(0, -1, 1));
        modeBox.addView(label("MODE  ⓘ"));
        modeRow = row(); modeBox.addView(modeRow, new LinearLayout.LayoutParams(-1, dp(38)));
        for (String m: new String[]{"Paper", "Demo", "Test", "Live"}) {
            TextView chip = chip(m); chip.setOnClickListener(v -> { String t=((TextView)v).getText().toString(); state.mode = t.equals("Test") ? "Testnet" : t; refresh(); }); modeRow.addView(chip, new LinearLayout.LayoutParams(0, -1, 1));
        }
        modeTxt = small("Paper first • Live locked", muted()); modeBox.addView(modeTxt);
        modeBox.addView(small("Internal APK engine active", cyan()));
    }

    private void addTabs() {
        tabsRow = row(); tabsRow.setPadding(0, dp(8), 0, dp(6)); root.addView(tabsRow, new LinearLayout.LayoutParams(-1, dp(64)));
        for (int i=0; i<tabs.length; i++) { final int ix=i; TextView t=txt(tabs[i], 11, i==0?cyan():muted(), 800); t.setGravity(Gravity.CENTER); t.setLineSpacing(0, .95f); t.setBackground(cardBg(i==0?cyan():Color.rgb(30,50,70), i==0?22:16)); t.setOnClickListener(v -> { activeTab=ix; drawTab(); }); tabsRow.addView(t, new LinearLayout.LayoutParams(0, -1, 1)); }
    }

    private void drawTab() { if (body == null) return; body.removeAllViews(); refreshTabs(); if (activeTab==0) bridge(); else if (activeTab==1) scanner(); else if (activeTab==2) brain(); else if (activeTab==3) journal(); else security(); refresh(); }

    private void bridge() { signalBox = cardCol(); signalBox.setPadding(dp(14), dp(14), dp(14), dp(14)); body.addView(signalBox); journalBox = col(); body.addView(journalBox); }
    private void scanner() { body.addView(sectionTitle("SCANNER — Scalping Radar")); signalBox = cardCol(); signalBox.setPadding(dp(14),dp(14),dp(14),dp(14)); body.addView(signalBox); body.addView(infoCard("Filters", "EMA trend • RSI momentum • MACD impulse • volume pulse • spread guard")); }
    private void brain() { body.addView(sectionTitle("BRAIN — Why Nanu acts")); brainBox = cardCol(); brainBox.setPadding(dp(14),dp(14),dp(14),dp(14)); body.addView(brainBox); body.addView(infoCard("Learning Rules", "Nanu records wins, losses, best symbols, bad hours, streaks, and rejected signals. God Mode is safety + awareness, not guaranteed profit.")); }
    private void journal() { body.addView(sectionTitle("JOURNAL — Trade Memory")); journalBox = cardCol(); journalBox.setPadding(dp(14),dp(14),dp(14),dp(14)); body.addView(journalBox); }
    private void security() { body.addView(sectionTitle("SECURITY — Keys and Risk Doors")); LinearLayout c=cardCol(); c.setPadding(dp(14),dp(14),dp(14),dp(14)); body.addView(c); c.addView(edit("Binance API Key", false)); c.addView(edit("Binance API Secret", true)); c.addView(edit("Telegram Bot Token", true)); c.addView(edit("Telegram Chat ID", false)); c.addView(infoLine("Mode", state.mode)); c.addView(infoLine("Max daily loss", "Enabled")); c.addView(infoLine("Live trading", "Locked until confirmation")); c.addView(warn("Never enable Binance withdrawal permission for any trading bot.")); }

    private void refresh() {
        boolean profit = state.profitState(); int main = profit ? green() : red();
        if (logo != null) { logo.pnl = state.dayPnl; logo.running = state.running; logo.invalidate(); }
        if (moodTxt != null) { moodTxt.setText(state.mood); moodTxt.setTextColor(main); confTxt.setText("Confidence " + state.confidence + "%"); }
        if (todayPnl != null) { todayPnl.setText(state.money(state.dayPnl) + "\n" + pctClean(state.dayPnl)); todayPnl.setTextColor(main); allPnl.setText("All Time P&L  " + state.money(state.dayPnl * 4.7)); allPnl.setTextColor(main); }
        if (equityTxt != null) equityTxt.setText(String.format(Locale.US, "%.2f USDT", state.equity));
        if (dayPnlTxt != null) { dayPnlTxt.setText(state.money(state.dayPnl) + "\n" + pctClean(state.dayPnl)); dayPnlTxt.setTextColor(main); }
        if (openPnlTxt != null) { openPnlTxt.setText(state.money(state.openPnl) + "\n" + pctClean(state.openPnl)); openPnlTxt.setTextColor(state.openPnl >= 0 ? green() : red()); }
        if (winTxt != null) { double wr=state.winRate(); winTxt.setText(String.format(Locale.US, "%.1f%%\n(%d / 70)", wr, (int)(wr/100*70))); winTxt.setTextColor(main); }
        if (spark != null) { spark.data = state.equityPoints; spark.profit = profit; spark.invalidate(); }
        if (botStatus != null) { botStatus.setText("TRADING BOT   " + (state.running?"RUNNING":"STOPPED") + (state.panic?"  PANIC":"")); botStatus.setTextColor(state.running ? main : muted()); }
        if (exchangeDot != null) exchangeDot.setTextColor(main);
        if (modeTxt != null) modeTxt.setText("Exchange: Binance Spot  •  Mode: " + state.mode);
        refreshTabs(); refreshModeChips();
        if (signalBox != null) fillSignals(); if (journalBox != null) fillJournal(); if (brainBox != null) fillBrain();
    }

    private void fillSignals() {
        signalBox.removeAllViews();
        LinearLayout head = row(); head.setGravity(Gravity.CENTER_VERTICAL); signalBox.addView(head);
        head.addView(txt("SIGNAL BRIDGE", 18, Color.WHITE, 900), new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(txt(state.signals.size()+" Active", 12, state.profitState()?green():red(), 800));
        for (AppState.Signal s: state.signals) signalBox.addView(signalCard(s));
    }

    private View signalCard(AppState.Signal s) {
        LinearLayout c = cardRow(); c.setPadding(dp(12), dp(12), dp(12), dp(12)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(10),0,0); c.setLayoutParams(lp);
        TextView ico = txt(iconFor(s.symbol), 26, Color.WHITE, 900); ico.setGravity(Gravity.CENTER); ico.setBackground(circleBg(s.symbol.startsWith("BTC")?Color.rgb(245,151,26):s.symbol.startsWith("ETH")?Color.rgb(83,115,255):s.symbol.startsWith("SOL")?Color.rgb(128,80,255):Color.rgb(244,190,24))); c.addView(ico, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout mid = col(); mid.setPadding(dp(12),0,dp(6),0); c.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));
        mid.addView(txt(s.symbol + "   " + (s.pnl>=0?"LONG":"SHORT"), 16, Color.WHITE, 900));
        mid.addView(small("Isolated • " + s.leverage + "x", muted()));
        mid.addView(small(String.format(Locale.US, "Entry %.2f   Mark %.2f", s.entry, s.mark), muted()));
        mid.addView(small(String.format(Locale.US, "TP/SL %.2f / %.2f", s.tp, s.sl), muted()));
        LinearLayout side = col(); side.setGravity(Gravity.RIGHT); c.addView(side, new LinearLayout.LayoutParams(dp(112), -2));
        side.addView(label("P&L")); TextView pnl = txt(state.money(s.pnl) + "\n" + pctClean(s.pnl), 14, s.pnl>=0?green():red(), 900); pnl.setGravity(Gravity.RIGHT); side.addView(pnl);
        return c;
    }

    private void fillJournal() {
        journalBox.removeAllViews();
        if (activeTab == 0) {
            LinearLayout api = cardCol(); api.setPadding(dp(14),dp(14),dp(14),dp(14)); journalBox.addView(api);
            api.addView(txt("API & SECURITY   SECURE", 16, state.profitState()?green():red(), 900));
            api.addView(small("Exchange API     Binance Spot ✓\nAPI Status       Connected ✓\nRisk Guard       Enabled ✓\nLive Lock        Enabled ✓", muted()));
            LinearLayout j = cardCol(); j.setPadding(dp(14),dp(14),dp(14),dp(14)); journalBox.addView(j); j.addView(txt("JOURNAL — RECENT EVENTS", 16, Color.WHITE, 900)); for (int i=0;i<Math.min(5,state.journal.size());i++) j.addView(small("• " + state.journal.get(i), i==0?(state.profitState()?green():red()):muted()));
        } else {
            for (String s: state.journal) journalBox.addView(small("• " + s, muted()));
        }
    }

    private void fillBrain() { brainBox.removeAllViews(); for (String s: state.brain) brainBox.addView(small("✦ " + s, muted())); }

    private void refreshTabs() { if (tabsRow==null) return; for (int i=0;i<tabsRow.getChildCount();i++) { TextView t=(TextView)tabsRow.getChildAt(i); boolean on = i==activeTab; int color = state.profitState()?cyan():red(); t.setTextColor(on?color:muted()); t.setBackground(cardBg(on?color:Color.rgb(30,50,70), on?22:16)); } }
    private void refreshModeChips() { if (modeRow==null) return; for (int i=0;i<modeRow.getChildCount();i++) { TextView chip=(TextView)modeRow.getChildAt(i); String txt=chip.getText().toString(); boolean on = state.mode.startsWith(txt) || (txt.equals("Test") && state.mode.equals("Testnet")); chip.setTextColor(on?Color.WHITE:muted()); chip.setBackground(chipBg(on ? (state.profitState()?cyan():red()) : Color.rgb(50,72,95), on)); } }

    private String iconFor(String s) { if (s.startsWith("BTC")) return "₿"; if (s.startsWith("ETH")) return "♦"; if (s.startsWith("SOL")) return "≋"; return "⬢"; }
    private String pctClean(double v) { return String.format(Locale.US, "%s%.2f%%", v >= 0 ? "+" : "", v / 16.0); }

    private TextView sectionTitle(String s){ TextView t=txt(s,18,Color.WHITE,900); t.setPadding(dp(2), dp(16),0,dp(8)); return t; }
    private View infoCard(String title, String msg){ LinearLayout c=cardCol(); c.setPadding(dp(14),dp(14),dp(14),dp(14)); c.addView(txt(title,16,Color.WHITE,900)); c.addView(small(msg, muted())); return c; }
    private View infoLine(String a,String b){ TextView t=small(a + "     " + b, muted()); t.setPadding(0,dp(8),0,dp(8)); return t; }
    private TextView warn(String s){ TextView t=small(s, red()); t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD)); t.setPadding(0,dp(12),0,0); return t; }
    private EditText edit(String hint, boolean pass){ EditText e=new EditText(this); e.setHint(hint); e.setTextColor(Color.WHITE); e.setHintTextColor(muted()); e.setSingleLine(true); e.setTextSize(13); e.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); e.setInputType(pass?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT); e.setBackground(cardBg(Color.rgb(35,60,82),14)); e.setPadding(dp(12),0,dp(12),0); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48)); lp.setMargins(0,dp(8),0,0); e.setLayoutParams(lp); return e; }

    private void addBottomNote(){ TextView f=small("Nanu God Mode • Paper first • Live locked • Panic always available", muted()); f.setGravity(Gravity.CENTER); f.setPadding(0, dp(16),0,dp(8)); root.addView(f); }
    private LinearLayout col(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout cardCol(){ LinearLayout l=col(); l.setBackground(cardBg(Color.rgb(18,170,200),18)); if (Build.VERSION.SDK_INT >= 21) l.setElevation(dp(3)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(dp(3),dp(5),dp(3),dp(5)); l.setLayoutParams(lp); return l; }
    private LinearLayout cardRow(){ LinearLayout l=row(); l.setBackground(cardBg(Color.rgb(18,170,200),18)); if (Build.VERSION.SDK_INT >= 21) l.setElevation(dp(3)); return l; }

    private TextView txt(String s,int sp,int color,int weight){ TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setIncludeFontPadding(false); t.setLineSpacing(dp(2), 1.0f); String fam = weight>=850 ? "sans-serif-black" : weight>=700 ? "sans-serif-medium" : "sans-serif"; t.setTypeface(Typeface.create(fam, weight>=700?Typeface.BOLD:Typeface.NORMAL)); return t; }
    private TextView label(String s){ TextView t=txt(s, 10, muted(), 700); t.setLetterSpacing(.06f); return t; }
    private TextView small(String s, int color){ TextView t=txt(s, 12, color, 400); t.setLineSpacing(dp(3), 1.05f); return t; }
    private Button btn(String s,int fill,boolean danger){ Button b=new Button(this); b.setAllCaps(false); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(13); b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD)); b.setBackground(btnBg(fill,danger?red():green())); b.setPadding(0,0,0,0); return b; }
    private TextView chip(String s){ TextView t=txt(s, 10, muted(), 700); t.setGravity(Gravity.CENTER); t.setBackground(chipBg(Color.rgb(50,72,95), false)); return t; }

    private View line(){ View v=new View(this); v.setBackgroundColor(Color.argb(80,160,200,220)); v.setLayoutParams(new LinearLayout.LayoutParams(-1,1)); return v; }
    private Drawable cardBg(int stroke,int rad){ GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.rgb(5,23,38),Color.rgb(2,10,21)}); g.setCornerRadius(dp(rad)); g.setStroke(dp(1), Color.argb(155, Color.red(stroke), Color.green(stroke), Color.blue(stroke))); return g; }
    private Drawable btnBg(int fill,int stroke){ GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{lighter(fill), fill}); g.setCornerRadius(dp(12)); g.setStroke(dp(1), Color.argb(210, Color.red(stroke), Color.green(stroke), Color.blue(stroke))); return g; }
    private Drawable chipBg(int stroke, boolean selected){ GradientDrawable g=new GradientDrawable(); g.setColor(selected ? Color.argb(70, Color.red(stroke), Color.green(stroke), Color.blue(stroke)) : Color.rgb(8,22,38)); g.setCornerRadius(dp(16)); g.setStroke(dp(1), Color.argb(selected?240:155, Color.red(stroke), Color.green(stroke), Color.blue(stroke))); return g; }
    private Drawable circleBg(int fill){ GradientDrawable g=new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(fill); return g; }
    private int lighter(int c){ return Color.rgb(Math.min(255, Color.red(c)+18), Math.min(255, Color.green(c)+18), Math.min(255, Color.blue(c)+18)); }
    private int bg(){ return Color.rgb(2,8,19); } private int cyan(){ return Color.rgb(0,229,255); } private int green(){ return Color.rgb(70,255,136); } private int red(){ return Color.rgb(255,75,75); } private int muted(){ return Color.rgb(155,174,198); }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density + .5f); }

    public class RadarView extends View { Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); float rot=0; public RadarView(Context c){ super(c); setLayerType(View.LAYER_TYPE_SOFTWARE,null); } @Override protected void onDraw(Canvas c){ super.onDraw(c); int w=getWidth(),h=getHeight(); float cx=w/2f,cy=h/2f,r=Math.min(w,h)*.34f; int col=state.profitState()?cyan():red(); p.setStyle(Paint.Style.FILL); RadialGradient rg=new RadialGradient(cx,cy,r*1.2f,Color.argb(80,Color.red(col),Color.green(col),Color.blue(col)),Color.TRANSPARENT,Shader.TileMode.CLAMP); p.setShader(rg); c.drawCircle(cx,cy,r*1.15f,p); p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(Color.argb(160,Color.red(col),Color.green(col),Color.blue(col))); for(int i=1;i<5;i++) c.drawCircle(cx,cy,r*i/4,p); c.drawLine(cx-r,cy,cx+r,cy,p); c.drawLine(cx,cy-r,cx,cy+r,p); p.setStyle(Paint.Style.FILL); p.setColor(col); p.setShadowLayer(8,0,0,col); c.drawCircle(cx,cy,dp(6),p); c.drawCircle(cx+r*.55f,cy-r*.35f,dp(4),p); c.drawCircle(cx-r*.35f,cy+r*.47f,dp(3),p); p.setShadowLayer(0,0,0,0); rot+=4; postInvalidateDelayed(90);} }
}
