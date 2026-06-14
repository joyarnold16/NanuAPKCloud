package com.nanu.godmode;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public class FaceLogoView extends View {
    public double pnl = 0;
    public boolean running = false;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float pulse = 0f;

    public FaceLogoView(Context c) {
        super(c);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) * 0.34f;
        boolean profit = pnl >= 0;
        boolean bigProfit = pnl > 70;
        boolean heavyLoss = pnl < -70;
        int glow = profit ? Color.rgb(0, 229, 255) : Color.rgb(255, 72, 72);
        int mood = profit ? Color.rgb(75, 255, 142) : Color.rgb(255, 82, 82);
        int deep = Color.rgb(3, 14, 28);
        pulse += 0.055f;
        float pulseScale = (float)(Math.sin(pulse) * 0.08f + 1.0f);

        p.setStyle(Paint.Style.FILL);
        RadialGradient aura = new RadialGradient(cx, cy, r * 2.0f * pulseScale,
                Color.argb(running ? 150 : 95, Color.red(glow), Color.green(glow), Color.blue(glow)),
                Color.TRANSPARENT, Shader.TileMode.CLAMP);
        p.setShader(aura);
        c.drawCircle(cx, cy, r * 1.95f, p);
        p.setShader(null);

        // compass rings
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(dp(3));
        p.setColor(Color.argb(230, Color.red(glow), Color.green(glow), Color.blue(glow)));
        p.setShadowLayer(14, 0, 0, glow);
        c.drawCircle(cx, cy, r * 1.08f, p);
        p.setShadowLayer(0, 0, 0, 0);
        p.setStrokeWidth(dp(1));
        p.setAlpha(140);
        c.drawCircle(cx, cy, r * 0.82f, p);
        c.drawCircle(cx, cy, r * 1.28f, p);
        p.setAlpha(255);

        // compass points
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(240, 230, 250, 255));
        drawTriangle(c, cx, cy - r * 1.62f, cx - dp(8), cy - r * 1.12f, cx + dp(8), cy - r * 1.12f);
        drawTriangle(c, cx + r * 1.62f, cy, cx + r * 1.12f, cy - dp(8), cx + r * 1.12f, cy + dp(8));
        drawTriangle(c, cx - r * 1.62f, cy, cx - r * 1.12f, cy - dp(8), cx - r * 1.12f, cy + dp(8));

        // face plate
        p.setStyle(Paint.Style.FILL);
        LinearGradient plate = new LinearGradient(cx, cy - r, cx, cy + r,
                Color.rgb(6, 38, 56), deep, Shader.TileMode.CLAMP);
        p.setShader(plate);
        c.drawCircle(cx, cy, r * 0.74f, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2));
        p.setColor(Color.argb(120, Color.red(glow), Color.green(glow), Color.blue(glow)));
        c.drawCircle(cx, cy, r * 0.74f, p);

        // small circuit marks
        p.setStrokeWidth(dp(1));
        p.setColor(Color.argb(120, Color.red(glow), Color.green(glow), Color.blue(glow)));
        c.drawLine(cx - r * .55f, cy - r * .02f, cx - r * .32f, cy - r * .02f, p);
        c.drawLine(cx + r * .32f, cy - r * .02f, cx + r * .55f, cy - r * .02f, p);
        c.drawCircle(cx - r * .58f, cy - r * .02f, dp(2), p);
        c.drawCircle(cx + r * .58f, cy - r * .02f, dp(2), p);

        // letter N above face
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        p.setTextSize(r * 0.54f);
        p.setColor(Color.WHITE);
        p.setShadowLayer(10, 0, 0, glow);
        c.drawText("N", cx, cy - r * 1.10f, p);
        p.setShadowLayer(0, 0, 0, 0);

        // eyes and brows
        p.setStrokeCap(Paint.Cap.ROUND);
        if (profit) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(mood);
            p.setShadowLayer(10, 0, 0, mood);
            c.drawOval(cx - r * .46f, cy - r * .20f, cx - r * .23f, cy - r * .08f, p);
            c.drawOval(cx + r * .23f, cy - r * .20f, cx + r * .46f, cy - r * .08f, p);
            p.setShadowLayer(0,0,0,0);
        } else {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(4));
            p.setColor(mood);
            p.setShadowLayer(10, 0, 0, mood);
            c.drawLine(cx - r * .48f, cy - r * .22f, cx - r * .24f, cy - r * .12f, p);
            c.drawLine(cx + r * .24f, cy - r * .12f, cx + r * .48f, cy - r * .22f, p);
            p.setShadowLayer(0,0,0,0);
        }

        // mouth
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(5));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(profit ? Color.WHITE : mood);
        RectF mouth = new RectF(cx - r * .44f, cy + r * .04f, cx + r * .44f, cy + r * .48f);
        if (profit) {
            c.drawArc(mouth, 20, 140, false, p);
            if (bigProfit) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.WHITE);
                RectF teeth = new RectF(cx - r*.34f, cy + r*.23f, cx + r*.34f, cy + r*.38f);
                c.drawRoundRect(teeth, dp(8), dp(8), p);
                p.setColor(Color.argb(160, 10, 20, 35));
                p.setStrokeWidth(dp(1));
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(cx, cy + r*.24f, cx, cy + r*.38f, p);
            }
        } else {
            RectF sad = new RectF(cx - r*.38f, cy + r*.24f, cx + r*.38f, cy + r*.70f);
            c.drawArc(sad, 205, 130, false, p);
            if (heavyLoss) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb(230, 170, 235, 255));
                c.drawOval(cx - r*.52f, cy - r*.02f, cx - r*.40f, cy + r*.34f, p);
                c.drawOval(cx + r*.40f, cy - r*.02f, cx + r*.52f, cy + r*.34f, p);
            }
        }
        postInvalidateDelayed(90);
    }

    private void drawTriangle(Canvas c, float x1, float y1, float x2, float y2, float x3, float y3) {
        Path path = new Path(); path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close(); c.drawPath(path, p);
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
}
