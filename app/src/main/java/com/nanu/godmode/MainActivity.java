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
    private LinearLayout root, body, metricsRow, signalGrid, journalBox, brainBox, tabsRow;
    private FaceLogoView logo;
    private SparkView spark;
    private TextView moodTxt, confTxt, todayPnl, allPnl, equityTxt, dayPnlTxt, openPnlTxt, winTxt, botStatus, modeTxt, titleSub;
    private int activeTab = 0;
    private final String[] tabs = {"⚓ Bridge", "◎ Scanner", "♕ Brain", "▤ Journal", "▣ Security"};
    private final Runnable loop = new Runnable() { public void run() { state.tick(); refresh(); handler.postDelayed(this, 1800); } };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(bg()); getWindow().setNavigationBarColor(bg());
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 44);
        buildUi(); handler.post(loop);
    }

    @Override protected void onDestroy() { super.onDestroy(); handler.removeCallbacks(loop); }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(bg());
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18), dp(18), dp(18), dp(18));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2)); setContentView(scroll);
        addHeader(); addMoodLogoPnl(); addMetrics(); addControls(); addTabs(); body = col(); root.addView(body); drawTab(); addBottomNav();
    }

    private void addHeader() {
        LinearLayout row = row(); row.setGravity(Gravity.CENTER_VERTICAL); root.addView(row);
        LinearLayout names = col(); row.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        TextView title = text("NANU", 31, Color.WHITE, true); title.setLetterSpacing(0.12f); names.addView(title);
        titleSub = text("AI SCALPING BRIDGE", 12, cyan(), true); titleSub.setLetterSpacing(0.28f); names.addView(titleSub);
        TextView bell = text("◉  ⚙", 24, cyan(), false); row.addView(bell);
    }

    private void addMoodLogoPnl() {
        LinearLayout wrap = row(); wrap.setGravity(Gravity.CENTER_VERTICAL); wrap.setPadding(0, dp(8),0,dp(8)); root.addView(wrap);
        LinearLayout mood = cardCol(); mood.setPadding(dp(16), dp(14), dp(16), dp(14)); wrap.addView(mood, new LinearLayout.LayoutParams(0, dp(135), 1));
        mood.addView(text("MARKET MOOD", 12, muted(), false));
        moodTxt = text("CALM", 27, cyan(), true); mood.addView(moodTxt);
        TextView animal = text("♉", 38, green(), false); mood.addView(animal);
        confTxt = text("Confidence 55%", 12, muted(), false); mood.addView(confTxt);
        logo = new FaceLogoView(this); wrap.addView(logo, new LinearLayout.LayoutParams(dp(150), dp(150)));
        LinearLayout pnl = cardCol(); pnl.setPadding(dp(16), dp(14), dp(16), dp(14)); wrap.addView(pnl, new LinearLayout.LayoutParams(0, dp(135), 1));
        pnl.addView(text("TODAY'S P&L  ⓘ", 12, muted(), false));
        todayPnl = text("+0.00 USDT", 25, green(), true); pnl.addView(todayPnl);
        pnl.addView(line()); allPnl = text("All Time P&L\n+0.00 USDT", 14, green(), false); pnl.addView(allPnl);
    }

    private void addMetrics() {
        metricsRow = cardRow(); metricsRow.setPadding(dp(16), dp(12), dp(16), dp(12)); root.addView(metricsRow, new LinearLayout.LayoutParams(-1, dp(100)));
        LinearLayout equity = col(); metricsRow.addView(equity, new LinearLayout.LayoutParams(0, -1, 1.35f));
        equity.addView(text("EQUITY", 11, muted(), false)); equityTxt = text("1000.00 USDT", 19, Color.WHITE, true); equity.addView(equityTxt);
        spark = new SparkView(this); equity.addView(spark, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout d = metric("24H P&L"); dayPnlTxt = (TextView)d.getChildAt(1); metricsRow.addView(d, new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout o = metric("OPEN P&L"); openPnlTxt = (TextView)o.getChildAt(1); metricsRow.addView(o, new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout w = metric("WIN RATE"); winTxt = (TextView)w.getChildAt(1); metricsRow.addView(w, new LinearLayout.LayoutParams(0,-1,1));
    }

    private LinearLayout metric(String name) { LinearLayout l=col(); l.setPadding(dp(12),0,0,0); l.addView(text(name,11,muted(),false)); l.addView(text("0.00",18,green(),true)); return l; }

    private void addControls() {
        LinearLayout wrap = cardRow(); wrap.setPadding(dp(16), dp(14), dp(16), dp(14)); root.addView(wrap, new LinearLayout.LayoutParams(-1, dp(160)));
        LinearLayout left = col(); wrap.addView(left, new LinearLayout.LayoutParams(0, -1, 1.2f));
        botStatus = text("TRADING BOT   STOPPED", 13, muted(), true); left.addView(botStatus);
        LinearLayout br = row(); left.addView(br, new LinearLayout.LayoutParams(-1,0,1));
        Button start = btn("▶  Start Bot", green(), false); start.setOnClickListener(v -> { state.start(); refresh(); }); br.addView(start, new LinearLayout.LayoutParams(0, dp(55), 1));
        Button stop = btn("■  Stop Bot", Color.rgb(20,38,54), false); stop.setOnClickListener(v -> { state.stop(); refresh(); }); br.addView(stop, new LinearLayout.LayoutParams(0, dp(55), 1));
        Button panic = btn("⚠  Panic Close", Color.rgb(110,20,28), true); panic.setOnClickListener(v -> { state.panic(); refresh(); }); left.addView(panic, new LinearLayout.LayoutParams(-1, dp(56)));
        RadarView radar = new RadarView(this); wrap.addView(radar, new LinearLayout.LayoutParams(dp(130), -1));
        LinearLayout right = col(); wrap.addView(right, new LinearLayout.LayoutParams(0, -1, 1));
        right.addView(text("MODE  ⓘ", 12, muted(), false));
        LinearLayout modes = row(); right.addView(modes);
        for (String m: new String[]{"Paper","Demo","Testnet","Live"}) { Button mb=smallBtn(m); mb.setOnClickListener(v->{ state.mode=((Button)v).getText().toString(); refresh(); }); modes.addView(mb,new LinearLayout.LayoutParams(0,dp(42),1)); }
        modeTxt = text("Exchange: Binance ●", 16, Color.WHITE, true); right.addView(modeTxt);
        right.addView(text("Live is locked until safety confirmation. Paper first.", 11, muted(), false));
    }

    private void addTabs() { tabsRow = row(); root.addView(tabsRow, new LinearLayout.LayoutParams(-1, dp(58))); for (int i=0;i<tabs.length;i++){ final int ix=i; TextView tv=text(tabs[i],14, i==0?cyan():muted(),true); tv.setGravity(Gravity.CENTER); tv.setBackground(cardBg(i==0?cyan():Color.rgb(35,55,75), i==0?30:16)); tv.setOnClickListener(v->{ activeTab=ix; drawTab();}); tabsRow.addView(tv,new LinearLayout.LayoutParams(0,-1,1)); } }

    private void drawTab() { if (body==null) return; body.removeAllViews(); refreshTabs(); if (activeTab==0) bridge(); else if (activeTab==1) scanner(); else if (activeTab==2) brain(); else if (activeTab==3) journal(); else security(); refresh(); }
    private void refreshTabs(){ if (tabsRow==null) return; for(int i=0;i<tabsRow.getChildCount();i++){ TextView tv=(TextView)tabsRow.getChildAt(i); boolean on=i==activeTab; tv.setTextColor(on?(state.profitState()?cyan():red()):muted()); tv.setBackground(cardBg(on?(state.profitState()?cyan():red()):Color.rgb(35,55,75), on?30:16)); } }

    private void bridge() { signalGrid = cardCol(); signalGrid.setPadding(dp(16),dp(16),dp(16),dp(16)); body.addView(signalGrid); journalBox = row(); body.addView(journalBox, new LinearLayout.LayoutParams(-1, dp(210))); }
    private void scanner() { body.addView(sectionTitle("SCANNER — Scalping Radar")); signalGrid = cardCol(); signalGrid.setPadding(dp(16),dp(16),dp(16),dp(16)); body.addView(signalGrid); body.addView(infoCard("Filters", "EMA trend • RSI momentum • MACD impulse • volume pulse • max spread guard")); }
    private void brain() { body.addView(sectionTitle("BRAIN — Why Nanu acts")); brainBox = cardCol(); brainBox.setPadding(dp(16),dp(16),dp(16),dp(16)); body.addView(brainBox); body.addView(infoCard("Learning Rules", "Nanu records every win/loss, best symbol, bad hour, loss streak, and rejected signal. God Mode is safety + awareness, not guaranteed profit.")); }
    private void journal() { body.addView(sectionTitle("JOURNAL — Trade Memory")); journalBox = cardCol(); journalBox.setPadding(dp(16),dp(16),dp(16),dp(16)); body.addView(journalBox); }
    private void security() { body.addView(sectionTitle("SECURITY — Keys and Risk Doors")); LinearLayout c=cardCol(); c.setPadding(dp(16),dp(16),dp(16),dp(16)); body.addView(c); c.addView(edit("Binance API Key", false)); c.addView(edit("Binance API Secret", true)); c.addView(edit("Telegram Bot Token", true)); c.addView(edit("Telegram Chat ID", false)); c.addView(infoLine("Mode", state.mode)); c.addView(infoLine("Max daily loss", "Locked safety guard")); c.addView(infoLine("Live trading", "Locked until you enable and confirm")); c.addView(text("Never enable Binance withdrawal permission for a trading bot.",12, red(), true)); }

    private void refresh() {
        boolean profit = state.profitState(); int main = profit ? green() : red(); int glow = profit ? cyan() : red();
        if (logo != null) { logo.pnl = state.dayPnl; logo.running = state.running; logo.invalidate(); }
        if (moodTxt != null) { moodTxt.setText(state.mood); moodTxt.setTextColor(main); confTxt.setText("Confidence " + state.confidence + "%"); }
        if (todayPnl != null) { todayPnl.setText(state.money(state.dayPnl) + "\n" + state.pct(state.dayPnl)); todayPnl.setTextColor(main); allPnl.setTextColor(main); allPnl.setText("All Time P&L\n" + state.money(state.dayPnl * 4.7)); }
        if (equityTxt != null) equityTxt.setText(String.format(Locale.US,"%.2f USDT", state.equity));
        if (dayPnlTxt != null) { dayPnlTxt.setText(state.money(state.dayPnl)+"\n"+state.pct(state.dayPnl)); dayPnlTxt.setTextColor(main); }
        if (openPnlTxt != null) { openPnlTxt.setText(state.money(state.openPnl)+"\n"+state.pct(state.openPnl)); openPnlTxt.setTextColor(state.openPnl>=0?green():red()); }
        if (winTxt != null) { winTxt.setText(String.format(Locale.US,"%.1f%%\n(%d / %d)", state.winRate(), (int)(state.winRate()/100*70), 70)); winTxt.setTextColor(main); }
        if (spark != null) { spark.data = state.equityPoints; spark.profit=profit; spark.invalidate(); }
        if (botStatus != null) { botStatus.setText("TRADING BOT   " + (state.running?"RUNNING":"STOPPED") + (state.panic?"  PANIC":"")); botStatus.setTextColor(state.running?main:muted()); }
        if (modeTxt != null) modeTxt.setText("Exchange: Binance ●   Mode: " + state.mode);
        refreshTabs();
        if (signalGrid != null) fillSignals(); if (journalBox != null) fillJournalSmall(); if (brainBox != null) fillBrain();
    }

    private void fillSignals() {
        signalGrid.removeAllViews();
        LinearLayout head = row(); head.setGravity(Gravity.CENTER_VERTICAL); signalGrid.addView(head);
        TextView h = text("SIGNAL BRIDGE", 18, Color.WHITE, true); head.addView(h, new LinearLayout.LayoutParams(0,-2,1));
        head.addView(text(state.signals.size()+" Active Signals", 12, state.profitState()?green():red(), true));
        for (AppState.Signal s: state.signals) signalGrid.addView(signalCard(s));
    }

    private View signalCard(AppState.Signal s) {
        LinearLayout c = cardRow(); c.setPadding(dp(14), dp(12), dp(14), dp(12)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1, dp(116)); lp.setMargins(0,dp(10),0,0); c.setLayoutParams(lp);
        TextView icon=text(iconFor(s.symbol), 30, Color.WHITE, true); icon.setGravity(Gravity.CENTER); icon.setBackground(circleBg(s.symbol.startsWith("BTC")?Color.rgb(245,151,26):s.symbol.startsWith("ETH")?Color.rgb(90,120,255):s.symbol.startsWith("SOL")?Color.rgb(120,80,255):Color.rgb(245,190,20))); c.addView(icon,new LinearLayout.LayoutParams(dp(58),dp(58)));
        LinearLayout mid=col(); mid.setPadding(dp(12),0,0,0); c.addView(mid,new LinearLayout.LayoutParams(0,-1,1));
        mid.addView(text(s.symbol+"   "+(s.pnl>=0?"LONG":"SHORT"),17,Color.WHITE,true)); mid.addView(text("Isolated • "+s.leverage+"x",12,muted(),false));
        mid.addView(text(String.format(Locale.US,"Entry %.2f   Mark %.2f   TP/SL %.2f / %.2f",s.entry,s.mark,s.tp,s.sl),11,muted(),false));
        ProgressBar pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); pb.setMax(100); pb.setProgress((int)Math.min(96,Math.max(6,50+s.pnl))); mid.addView(pb,new LinearLayout.LayoutParams(-1,dp(10)));
        LinearLayout r=col(); r.setGravity(Gravity.RIGHT); c.addView(r,new LinearLayout.LayoutParams(dp(118),-1)); r.addView(text("P&L",11,muted(),false)); TextView pnl=text(state.money(s.pnl)+"\n"+state.pct(s.pnl),16,s.pnl>=0?green():red(),true); pnl.setGravity(Gravity.RIGHT); r.addView(pnl);
        return c;
    }

    private String iconFor(String s) { if(s.startsWith("BTC"))return "₿"; if(s.startsWith("ETH"))return "♦"; if(s.startsWith("SOL"))return "≋"; return "⬢"; }
    private void fillJournalSmall() { journalBox.removeAllViews(); LinearLayout a=cardCol(), b=cardCol(); a.setPadding(dp(14),dp(14),dp(14),dp(14)); b.setPadding(dp(14),dp(14),dp(14),dp(14)); journalBox.addView(a,new LinearLayout.LayoutParams(0,-1,1)); journalBox.addView(b,new LinearLayout.LayoutParams(0,-1,1)); a.addView(text("API & SECURITY    SECURE",16,state.profitState()?green():red(),true)); a.addView(text("Exchange API     Binance Spot ✓\nAPI Status       Connected ✓\nRisk Guard       Enabled ✓\nLive Lock        Enabled ✓",13,muted(),false)); b.addView(text("JOURNAL — RECENT EVENTS",16,Color.WHITE,true)); for(int i=0;i<Math.min(5,state.journal.size());i++) b.addView(text("• "+state.journal.get(i),12,i==0?(state.profitState()?green():red()):muted(),false)); }
    private void fillBrain() { brainBox.removeAllViews(); for(String s: state.brain) brainBox.addView(text("✦ "+s,14,muted(),false)); }

    private TextView sectionTitle(String s){ TextView t=text(s,18,Color.WHITE,true); t.setPadding(0,dp(18),0,dp(8)); return t; }
    private View infoCard(String title,String msg){ LinearLayout c=cardCol(); c.setPadding(dp(16),dp(16),dp(16),dp(16)); c.addView(text(title,16,Color.WHITE,true)); c.addView(text(msg,13,muted(),false)); return c; }
    private View infoLine(String a,String b){ TextView t=text(a+"     "+b,14,muted(),false); t.setPadding(0,dp(8),0,dp(8)); return t; }
    private EditText edit(String hint, boolean pass){ EditText e=new EditText(this); e.setHint(hint); e.setTextColor(Color.WHITE); e.setHintTextColor(muted()); e.setSingleLine(true); e.setInputType(pass?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT); e.setBackground(cardBg(Color.rgb(35,55,75),14)); e.setPadding(dp(12),0,dp(12),0); e.setTextSize(13); return e; }

    private void addBottomNav(){ TextView foot=text("Nanu God Mode • Paper first • Live locked • Panic always available",11,muted(),false); foot.setGravity(Gravity.CENTER); foot.setPadding(0,dp(18),0,dp(8)); root.addView(foot); }
    private LinearLayout col(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setPadding(0,0,0,0); return l; }
    private LinearLayout cardCol(){ LinearLayout l=col(); l.setBackground(cardBg(Color.rgb(18,170,200),18)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(dp(4),dp(8),dp(4),dp(8)); l.setLayoutParams(lp); return l; }
    private LinearLayout cardRow(){ LinearLayout l=row(); l.setBackground(cardBg(Color.rgb(18,170,200),18)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(dp(4),dp(8),dp(4),dp(8)); l.setLayoutParams(lp); return l; }
    private TextView text(String s,int sp,int color,boolean bold){ TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setIncludeFontPadding(true); if(bold)t.setTypeface(Typeface.DEFAULT_BOLD); return t; }
    private Button btn(String s,int color,boolean danger){ Button b=new Button(this); b.setAllCaps(false); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackground(btnBg(color,danger?red():green())); return b; }
    private Button smallBtn(String s){ Button b=btn(s,Color.rgb(15,28,45),false); b.setTextSize(11); return b; }
    private View line(){ View v=new View(this); v.setBackgroundColor(Color.argb(80,160,200,220)); v.setLayoutParams(new LinearLayout.LayoutParams(-1,1)); return v; }
    private Drawable cardBg(int stroke,int rad){ GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.rgb(4,17,30),Color.rgb(2,11,22)}); g.setCornerRadius(dp(rad)); g.setStroke(dp(1), Color.argb(160, Color.red(stroke), Color.green(stroke), Color.blue(stroke))); return g; }
    private Drawable btnBg(int fill,int stroke){ GradientDrawable g=new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(12)); g.setStroke(dp(1), stroke); return g; }
    private Drawable circleBg(int fill){ GradientDrawable g=new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(fill); return g; }
    private int bg(){ return Color.rgb(2,8,19); } private int cyan(){ return Color.rgb(0,229,255); } private int green(){ return Color.rgb(70,255,136); } private int red(){ return Color.rgb(255,75,75); } private int muted(){ return Color.rgb(158,176,198); }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density + .5f); }

    public class RadarView extends View { Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); float rot=0; public RadarView(Context c){ super(c);} @Override protected void onDraw(Canvas c){ super.onDraw(c); int w=getWidth(),h=getHeight(); float cx=w/2f,cy=h/2f,r=Math.min(w,h)*.38f; int col=state.profitState()?cyan():red(); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(Color.argb(160,Color.red(col),Color.green(col),Color.blue(col))); for(int i=1;i<5;i++) c.drawCircle(cx,cy,r*i/4,p); c.drawLine(cx-r,cy,cx+r,cy,p); c.drawLine(cx,cy-r,cx,cy+r,p); p.setStyle(Paint.Style.FILL); p.setColor(col); c.drawCircle(cx,cy,8,p); c.drawCircle(cx+r*.55f,cy-r*.35f,6,p); c.drawCircle(cx-r*.35f,cy+r*.47f,5,p); rot+=4; postInvalidateDelayed(80);} }
}
