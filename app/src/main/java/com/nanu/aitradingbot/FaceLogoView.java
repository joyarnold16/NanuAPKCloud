package com.nanu.aitradingbot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class FaceLogoView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private NanuEngine engine;
    public FaceLogoView(Context c) { super(c); }
    public void bind(NanuEngine e) { engine = e; invalidate(); }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int cyan = Color.rgb(0, 229, 255), green = Color.rgb(79,255,141), red = Color.rgb(255,73,73), white = Color.rgb(240,250,255);
        boolean bad = engine != null && (engine.todayPnl < -15 || engine.panic);
        boolean heavyLoss = engine != null && engine.todayPnl < -100;
        boolean bigProfit = engine != null && engine.todayPnl > 120;
        int glow = engine != null && engine.panic ? red : (bad ? red : cyan);
        float w = getWidth(), h = getHeight(), cx = w/2f, cy = h/2f, radius = Math.min(w,h)*0.36f;
        p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(3, 28, 38)); c.drawCircle(cx, cy, radius*1.18f, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(3)); p.setColor(glow); c.drawCircle(cx, cy, radius*1.2f, p); p.setStrokeWidth(dp(1.5f)); c.drawCircle(cx, cy, radius*0.9f, p);
        p.setStyle(Paint.Style.FILL); p.setColor(white); p.setTextAlign(Paint.Align.CENTER); p.setFakeBoldText(true); p.setTextSize(dp(26)); c.drawText("N", cx, cy-radius*1.35f, p); p.setFakeBoldText(false);
        p.setColor(bad ? red : green); c.drawOval(new RectF(cx-radius*.45f, cy-radius*.25f, cx-radius*.2f, cy-radius*.12f), p); c.drawOval(new RectF(cx+radius*.2f, cy-radius*.25f, cx+radius*.45f, cy-radius*.12f), p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(4)); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(white);
        RectF mouth = new RectF(cx-radius*.45f, cy-radius*.1f, cx+radius*.45f, cy+radius*.55f);
        if (bad) { c.drawArc(mouth, 205, 130, false, p); }
        else { c.drawArc(mouth, 25, 130, false, p); if (bigProfit) { p.setStyle(Paint.Style.FILL); c.drawRoundRect(new RectF(cx-radius*.32f, cy+radius*.2f, cx+radius*.32f, cy+radius*.38f), dp(5), dp(5), p); p.setStyle(Paint.Style.STROKE); } }
        if (heavyLoss || (engine != null && engine.panic)) {
            p.setColor(Color.rgb(160,235,255)); p.setStrokeWidth(dp(2.5f));
            c.drawLine(cx-radius*.32f, cy-radius*.06f, cx-radius*.38f, cy+radius*.35f, p);
            c.drawLine(cx+radius*.32f, cy-radius*.06f, cx+radius*.38f, cy+radius*.35f, p);
        }
    }
}
