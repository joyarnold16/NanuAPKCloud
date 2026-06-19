package com.nanu.aitradingbot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

public class FaceLogoView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private NanuEngine engine;
    private long startedAt = System.currentTimeMillis();

    public FaceLogoView(Context c) { super(c); }

    public void bind(NanuEngine e) {
        engine = e;
        invalidate();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private int rgb(int r, int g, int b) { return Color.rgb(r, g, b); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);

        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) * 0.34f;
        float t = (System.currentTimeMillis() - startedAt) / 1000f;
        float breath = 1f + 0.025f * (float)Math.sin(t * 2.2f);
        r *= breath;
        boolean blink = ((int)(t * 2.6f) % 13) == 0;

        boolean panic = engine != null && engine.panic;
        double pnl = engine == null ? 0 : engine.todayPnl;
        boolean heavyLoss = !panic && pnl < -100;
        boolean loss = !panic && pnl < -15;
        boolean bigProfit = !panic && pnl > 120;
        boolean profit = !panic && pnl > 15;

        int cyan = rgb(0, 229, 255);
        int violet = rgb(153, 91, 255);
        int green = rgb(79, 255, 141);
        int amber = rgb(255, 190, 70);
        int red = rgb(255, 73, 73);
        int white = rgb(238, 248, 255);
        int glow = panic ? red : heavyLoss ? red : loss ? amber : (profit || bigProfit) ? green : cyan;

        // Outer neon aura
        p.setStyle(Paint.Style.FILL);
        p.setColor(rgb(2, 10, 18));
        c.drawCircle(cx, cy, r * 1.48f, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(4));
        p.setColor(glow);
        c.drawCircle(cx, cy, r * 1.42f, p);
        p.setStrokeWidth(dp(1.5f));
        p.setColor(violet);
        c.drawCircle(cx, cy, r * 1.22f, p);

        // Hair / AI halo arcs
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(dp(5));
        p.setColor(violet);
        c.drawArc(new RectF(cx-r*1.28f, cy-r*1.35f, cx+r*1.28f, cy+r*1.35f), 210, 120, false, p);
        p.setColor(cyan);
        p.setStrokeWidth(dp(3));
        c.drawArc(new RectF(cx-r*1.08f, cy-r*1.18f, cx+r*1.08f, cy+r*1.18f), -30, 120, false, p);

        // Face shield
        p.setStyle(Paint.Style.FILL);
        p.setColor(rgb(14, 28, 45));
        c.drawOval(new RectF(cx-r*.78f, cy-r*.92f, cx+r*.78f, cy+r*1.02f), p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2.3f));
        p.setColor(glow);
        c.drawOval(new RectF(cx-r*.78f, cy-r*.92f, cx+r*.78f, cy+r*1.02f), p);

        // Circuit cheeks
        p.setStrokeWidth(dp(1.8f));
        p.setColor(cyan);
        c.drawLine(cx-r*.72f, cy-r*.02f, cx-r*.46f, cy+r*.10f, p);
        c.drawLine(cx+r*.72f, cy-r*.02f, cx+r*.46f, cy+r*.10f, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx-r*.44f, cy+r*.11f, dp(3.2f), p);
        c.drawCircle(cx+r*.44f, cy+r*.11f, dp(3.2f), p);

        // Eyes
        p.setStyle(Paint.Style.FILL);
        p.setColor(panic ? red : heavyLoss || loss ? amber : (profit || bigProfit ? green : cyan));
        float eyeTop = cy-r*.28f;
        float eyeBottom = blink && !panic ? eyeTop + dp(3) : cy-r*.12f;
        RectF leftEye = new RectF(cx-r*.48f, eyeTop, cx-r*.18f, eyeBottom);
        RectF rightEye = new RectF(cx+r*.18f, eyeTop, cx+r*.48f, eyeBottom);
        if (panic) {
            // angry eyes
            Path le = new Path(); le.moveTo(cx-r*.52f, cy-r*.34f); le.lineTo(cx-r*.16f, cy-r*.20f); le.lineTo(cx-r*.50f, cy-r*.12f); le.close(); c.drawPath(le, p);
            Path re = new Path(); re.moveTo(cx+r*.52f, cy-r*.34f); re.lineTo(cx+r*.16f, cy-r*.20f); re.lineTo(cx+r*.50f, cy-r*.12f); re.close(); c.drawPath(re, p);
        } else {
            c.drawRoundRect(leftEye, dp(12), dp(12), p);
            c.drawRoundRect(rightEye, dp(12), dp(12), p);
            if (!blink) {
                p.setColor(rgb(2, 10, 18));
                c.drawCircle(cx-r*.33f, cy-r*.20f, dp(3.0f), p);
                c.drawCircle(cx+r*.33f, cy-r*.20f, dp(3.0f), p);
            }
        }

        // Eyebrows
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(3));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(white);
        if (panic || heavyLoss || loss) {
            c.drawLine(cx-r*.52f, cy-r*.45f, cx-r*.16f, cy-r*.35f, p);
            c.drawLine(cx+r*.16f, cy-r*.35f, cx+r*.52f, cy-r*.45f, p);
        } else {
            c.drawLine(cx-r*.52f, cy-r*.40f, cx-r*.16f, cy-r*.45f, p);
            c.drawLine(cx+r*.16f, cy-r*.45f, cx+r*.52f, cy-r*.40f, p);
        }

        // Mouth - the most visible expression change
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(5));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(white);
        RectF mouth = new RectF(cx-r*.46f, cy+r*.02f, cx+r*.46f, cy+r*.58f);
        if (panic) {
            c.drawLine(cx-r*.30f, cy+r*.30f, cx+r*.30f, cy+r*.30f, p);
        } else if (heavyLoss || loss) {
            c.drawArc(mouth, 205, 130, false, p);
        } else if (bigProfit) {
            c.drawArc(mouth, 15, 150, false, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(white);
            c.drawRoundRect(new RectF(cx-r*.31f, cy+r*.23f, cx+r*.31f, cy+r*.43f), dp(8), dp(8), p);
            p.setColor(rgb(14, 28, 45));
            c.drawLine(cx, cy+r*.24f, cx, cy+r*.42f, p);
        } else if (profit) {
            c.drawArc(mouth, 25, 130, false, p);
        } else {
            c.drawLine(cx-r*.24f, cy+r*.30f, cx+r*.24f, cy+r*.30f, p);
        }

        // Tears / danger marks
        if (heavyLoss || panic) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2.8f));
            p.setColor(cyan);
            c.drawLine(cx-r*.33f, cy-r*.06f, cx-r*.42f, cy+r*.42f, p);
            c.drawLine(cx+r*.33f, cy-r*.06f, cx+r*.42f, cy+r*.42f, p);
        }

        // Profit sparkle
        if (profit || bigProfit) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(green);
            drawSpark(c, cx+r*1.02f, cy-r*.72f, r*.12f);
            drawSpark(c, cx-r*1.04f, cy-r*.55f, r*.09f);
        }

        // N badge
        p.setStyle(Paint.Style.FILL);
        p.setColor(rgb(4, 16, 28));
        c.drawCircle(cx, cy+r*1.10f, r*.23f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2));
        p.setColor(glow);
        c.drawCircle(cx, cy+r*1.10f, r*.23f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(white);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(r*.29f);
        c.drawText("N", cx, cy+r*1.18f, p);
        p.setFakeBoldText(false);
        postInvalidateDelayed(33);
    }

    private void drawSpark(Canvas c, float x, float y, float s) {
        c.drawLine(x-s, y, x+s, y, p);
        c.drawLine(x, y-s, x, y+s, p);
    }
}
