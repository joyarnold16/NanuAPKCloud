package com.nanu.godmode;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

public class SparkView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    public ArrayList<Double> data = new ArrayList<>();
    public boolean profit = true;
    public SparkView(Context c) { super(c); line.setStrokeWidth(4); line.setStyle(Paint.Style.STROKE); fill.setStyle(Paint.Style.FILL); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        int color = profit ? Color.rgb(70,255,136) : Color.rgb(255,70,70);
        line.setColor(color); fill.setColor(Color.argb(25, Color.red(color), Color.green(color), Color.blue(color)));
        if (data.size() < 2) return;
        double min = Collections.min(data), max = Collections.max(data);
        if (Math.abs(max-min) < 0.001) { max += 1; min -= 1; }
        Path p = new Path();
        Path area = new Path(); area.moveTo(0,h);
        for (int i=0;i<data.size();i++) {
            float x = (float)i/(data.size()-1) * w;
            float y = (float)(h - ((data.get(i)-min)/(max-min))*(h-10) - 5);
            if (i==0) p.moveTo(x,y); else p.lineTo(x,y);
            area.lineTo(x,y);
        }
        area.lineTo(w,h); area.close();
        canvas.drawPath(area, fill);
        canvas.drawPath(p, line);
    }
}
