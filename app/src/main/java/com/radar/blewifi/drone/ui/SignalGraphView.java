package com.radar.blewifi.drone.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import java.util.LinkedList;

/**
 * A small sparkline-style graph to show signal strength history.
 */
public class SignalGraphView extends View {
    private final LinkedList<Float> history = new LinkedList<>();
    private final int MAX_HISTORY = 40;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int primaryColor;
    private int secondaryColor;
    public SignalGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.FILL);
        bgPaint.setStyle(Paint.Style.FILL);
        updateColors(0xFF00FF00, 0xFF00FF00, Color.argb(34, 0, 255, 0));
    }

    public void updateColors(int primary, int secondary, int background) {
        this.primaryColor = primary;
        this.secondaryColor = secondary;
        this.bgPaint.setColor(background);
        invalidate();
    }

    public void addValue(float val) {
        history.add(val);
        if (history.size() > MAX_HISTORY) {
            history.removeFirst();
        }
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float spacing = 4f;
        float barWidth = (w - (MAX_HISTORY - 1) * spacing) / MAX_HISTORY;
        
        for (int i = 0; i < MAX_HISTORY; i++) {
            float x = i * (barWidth + spacing);
            
            // Draw background bar
            canvas.drawRect(x, 0, x + barWidth, h, bgPaint);
            
            if (i < history.size()) {
                float val = history.get(i);
                float barHeight = (val / 100f) * h;
                
                // Color gradient based on height
                int c;
                if (val < 50) c = primaryColor;
                else c = secondaryColor;
                
                paint.setColor(c);
                canvas.drawRect(x, h - barHeight, x + barWidth, h, paint);
            }
        }
    }
}
