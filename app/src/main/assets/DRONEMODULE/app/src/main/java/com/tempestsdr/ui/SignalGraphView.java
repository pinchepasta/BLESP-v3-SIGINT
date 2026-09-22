package com.tempestsdr.ui;

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
        
        TypedValue typedValue = new TypedValue();
        android.content.res.Resources.Theme theme = context.getTheme();
        
        primaryColor = Color.parseColor("#00E5FF");
        if (theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)) {
            primaryColor = typedValue.data;
        }

        secondaryColor = Color.parseColor("#FF5252");
        if (theme.resolveAttribute(com.google.android.material.R.attr.colorSecondary, typedValue, true)) {
            secondaryColor = typedValue.data;
        }

        bgPaint.setColor(Color.argb(34, 255, 255, 255));
        bgPaint.setStyle(Paint.Style.FILL);
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
                if (val < 50) paint.setColor(primaryColor);
                else paint.setColor(secondaryColor);
                
                canvas.drawRect(x, h - barHeight, x + barWidth, h, paint);
            }
        }
    }
}
