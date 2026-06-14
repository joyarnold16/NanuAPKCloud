package com.nanu.godmode;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public class FaceLogoView extends View {
    public double pnl = 0;
    public boolean running = false;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float pulse = 0;
    public FaceLogoView(Context c) { super(c); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w=getWidth(), h=getHeight(); float cx=w/2f, cy=h/2f; float r=Math.min(w,h)*0.36f;
        boolean profit = pnl >= 0;
        int glow = profit ? Color.rgb(0,229,255) : Color.rgb(255,70,70);
        int mood = profit ? Color.rgb(70,255,136) : Color.rgb(255,90,90);
        pulse = (pulse + 0.05f) % 6.28f;
        c.drawColor(Color.TRANSPARENT);
        RadialGradient rg = new RadialGradient(cx, cy, r*1.6f, Color.argb(130, Color.red(glow), Color.green(glow), Color.blue(glow)), Color.TRANSPARENT, Shader.TileMode.CLAMP);
        p.setShader(rg); p.setStyle(Paint.Style.FILL); c.drawCircle(cx,cy,r*1.65f,p); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5); p.setColor(glow); c.drawCircle(cx,cy,r,p);
        p.setStrokeWidth(2); p.setAlpha(140); c.drawCircle(cx,cy,r*0.82f,p); c.drawCircle(cx,cy,r*1.18f,p); p.setAlpha(255);
        // compass points
        p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE);
        Path north = new Path(); north.moveTo(cx, cy-r*1.45f); north.lineTo(cx-10, cy-r*1.05f); north.lineTo(cx+10, cy-r*1.05f); north.close(); c.drawPath(north,p);
        Path east = new Path(); east.moveTo(cx+r*1.45f, cy); east.lineTo(cx+r*1.05f, cy-10); east.lineTo(cx+r*1.05f, cy+10); east.close(); c.drawPath(east,p);
        Path west = new Path(); west.moveTo(cx-r*1.45f, cy); west.lineTo(cx-r*1.05f, cy-10); west.lineTo(cx-r*1.05f, cy+10); west.close(); c.drawPath(west,p);
        // face plate
        p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(235, 4, 22, 36)); c.drawCircle(cx,cy,r*0.72f,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(Color.argb(180, Color.red(glow),Color.green(glow),Color.blue(glow)));
        c.drawArc(cx-r*0.55f, cy-r*0.46f, cx-r*0.18f, cy-r*0.15f, 200, 130, false, p);
        c.drawArc(cx+r*0.18f, cy-r*0.46f, cx+r*0.55f, cy-r*0.15f, 210, 130, false, p);
        // eyes
        p.setStyle(Paint.Style.FILL); p.setColor(mood);
        if (profit) {
            c.drawOval(cx-r*0.45f, cy-r*0.20f, cx-r*0.20f, cy-r*0.06f, p);
            c.drawOval(cx+r*0.20f, cy-r*0.20f, cx+r*0.45f, cy-r*0.06f, p);
        } else {
            p.setStrokeWidth(7); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(cx-r*0.45f, cy-r*0.13f, cx-r*0.23f, cy-r*0.05f, p);
            c.drawLine(cx+r*0.23f, cy-r*0.05f, cx+r*0.45f, cy-r*0.13f, p);
        }
        // letter N
        p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)); p.setTextSize(r*0.55f); p.setColor(Color.WHITE); c.drawText("N",cx,cy-r*1.08f,p);
        // mouth
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(8); p.setColor(profit ? Color.WHITE : mood);
        if (profit) {
            RectF m = new RectF(cx-r*0.43f, cy+r*0.02f, cx+r*0.43f, cy+r*0.52f);
            c.drawArc(m, 20, 140, false, p);
            if (pnl > 50) { // big profit teeth
                p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE);
                RectF teeth = new RectF(cx-r*0.32f, cy+r*0.18f, cx+r*0.32f, cy+r*0.36f);
                c.drawRoundRect(teeth, 10,10,p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(Color.argb(180,0,30,50));
                c.drawLine(cx-r*0.12f, cy+r*0.18f, cx-r*0.12f, cy+r*0.36f, p);
                c.drawLine(cx+r*0.12f, cy+r*0.18f, cx+r*0.12f, cy+r*0.36f, p);
            }
        } else {
            RectF m = new RectF(cx-r*0.34f, cy+r*0.18f, cx+r*0.34f, cy+r*0.62f);
            c.drawArc(m, 205, 130, false, p);
            if (pnl < -40) { // tears when loss grows
                p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(100,210,255));
                c.drawOval(cx-r*0.42f, cy-r*0.01f, cx-r*0.32f, cy+r*0.32f, p);
                c.drawOval(cx+r*0.32f, cy-r*0.01f, cx+r*0.42f, cy+r*0.32f, p);
            }
        }
        if (running) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(Color.argb(150,Color.red(mood),Color.green(mood),Color.blue(mood))); c.drawCircle(cx,cy,r*(1.28f + 0.03f*(float)Math.sin(pulse)),p); postInvalidateDelayed(33); }
    }
}
