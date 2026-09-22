package com.radar.blewifi.drone.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.radar.blewifi.R;

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
        
        linePaint.setStrokeWidth(3f); 
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        peakPaint.setStrokeWidth(4f);
        peakPaint.setStyle(Paint.Style.STROKE);
        peakPaint.setStrokeJoin(Paint.Join.ROUND);

        axisPaint.setStrokeWidth(1f);
        
        // Default colors
        updateColors(0xFF00FF00, 0xFF00FF00, 0xFF000000, 0xFF003300);
    }

    public void updateColors(int primary, int secondary, int background, int axis) {
        linePaint.setColor(primary);
        peakPaint.setColor(secondary);
        bgPaint.setColor(background);
        axisPaint.setColor(axis);
        invalidate();
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
