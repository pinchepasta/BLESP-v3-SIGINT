package com.tempestsdr.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.tempestsdr.R;

/**
 * Renders the autocorrelation spectrum used for estimating the monitor
 * framerate — matches the equivalent chart in the original Java desktop GUI.
 */
public class AutocorrView extends View {

    public interface OnTapListener {
        void onPeakSelected(int index);
    }

    private float[] data;
    private final Paint linePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint peakPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint    = new Paint();

    private int peakIndex = -1;
    private OnTapListener tapListener;
    private float idlePhase = 0;

    public AutocorrView(Context ctx) { this(ctx, null); }
    public AutocorrView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        
        TypedValue typedValue = new TypedValue();
        android.content.res.Resources.Theme theme = ctx.getTheme();
        
        int colorPrimary = Color.parseColor("#00E5FF");
        if (theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)) {
            colorPrimary = typedValue.data;
        }

        int colorSecondary = Color.parseColor("#FF1744");
        if (theme.resolveAttribute(com.google.android.material.R.attr.colorSecondary, typedValue, true)) {
            colorSecondary = typedValue.data;
        }

        int bgColor = Color.parseColor("#1A1A2E");
        if (theme.resolveAttribute(android.R.attr.windowBackground, typedValue, true)) {
            bgColor = typedValue.data;
        }

        linePaint.setColor(colorPrimary);
        linePaint.setStrokeWidth(3f); // Thicker for Cyberpunk glow effect
        linePaint.setStyle(Paint.Style.STROKE);

        peakPaint.setColor(colorSecondary);
        peakPaint.setStrokeWidth(4f);
        peakPaint.setStyle(Paint.Style.STROKE);

        axisPaint.setColor(Color.parseColor("#444444"));
        axisPaint.setStrokeWidth(1f);

        bgPaint.setColor(bgColor);
    }

    public void setOnTapListener(OnTapListener listener) {
        this.tapListener = listener;
    }

    public void setData(float[] autocorr) {
        if (autocorr == null) {
            this.data = null;
            this.peakIndex = -1;
            postInvalidate();
            return;
        }
        // Find peak (ignore lag 0)
        float peak = 0;
        int   idx  = 0;
        for (int i = 1; i < autocorr.length; i++) {
            if (autocorr[i] > peak) { peak = autocorr[i]; idx = i; }
        }
        this.data      = autocorr.clone();
        this.peakIndex = idx;
        postInvalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            performClick();
            if (data != null && data.length > 0 && tapListener != null) {
                float x = event.getX();
                int index = (int) (x * data.length / getWidth());
                if (index >= 0 && index < data.length) {
                    tapListener.onPeakSelected(index);
                    return true;
                }
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // Draw background
        canvas.drawRect(0, 0, w, h, bgPaint);

        // Find max for normalization and check if data is meaningful
        float mx = 0;
        if (data != null && data.length > 0) {
            for (float v : data) if (v > mx) mx = v;
        }

        if (data == null || data.length == 0 || mx < 0.000001f) {
            drawIdleSignal(canvas, w, h);
            return;
        }

        // Move the baseline up slightly (e.g., 4dp from bottom) for better ergonomics
        float baselineY = h - 8; 
        canvas.drawLine(0, baselineY, w, baselineY, axisPaint); // X-axis

        int n = data.length;
        float xStep = (float) w / n;

        float prevX = 0, prevY = baselineY;
        for (int i = 0; i < n; i++) {
            float x = i * xStep;
            // Scale data to fit between top and baseline
            float y = baselineY - (data[i] / mx) * (baselineY - 8);
            if (i > 0) {
                canvas.drawLine(prevX, prevY, x, y,
                                i == peakIndex ? peakPaint : linePaint);
            }
            prevX = x; prevY = y;
        }
    }

    private void drawIdleSignal(Canvas canvas, int w, int h) {
        float baselineY = h - 8;
        canvas.drawLine(0, baselineY, w, baselineY, axisPaint);

        int n = 100;
        float xStep = (float) w / n;
        float prevX = 0, prevY = baselineY;
        
        idlePhase += 0.2f;
        for (int i = 0; i <= n; i++) {
            float x = i * xStep;
            float y = baselineY - (float) (Math.abs(Math.sin(i * 0.2 + idlePhase)) * 10 + Math.random() * 5);
            if (i > 0) {
                canvas.drawLine(prevX, prevY, x, y, linePaint);
            }
            prevX = x;
            prevY = y;
        }
        postInvalidateOnAnimation();
    }
}
