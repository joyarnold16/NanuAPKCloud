package com.nanu.aitradingbot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import java.util.Random;

public class SparkLineView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean positive = true;
    public SparkLineView(Context c) { super(c); }
    public void setPositive(boolean b) { positive = b; invalidate(); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(positive ? Color.rgb(79,255,141) : Color.rgb(255,73,73));
        Path path = new Path(); Random r = new Random(positive ? 4 : 9);
        for (int i=0;i<18;i++) {
            float x = i*w/17f; float base = positive ? h*.75f - i*h*.022f : h*.35f + i*h*.025f; float y = base + (r.nextFloat()-.5f)*h*.28f;
            if (i==0) path.moveTo(x,y); else path.lineTo(x,y);
        }
        c.drawPath(path,p);
    }
}
