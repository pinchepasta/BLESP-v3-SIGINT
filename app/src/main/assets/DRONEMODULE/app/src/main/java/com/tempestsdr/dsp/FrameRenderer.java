package com.tempestsdr.dsp;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Converts TempestSDR float greyscale pixel arrays to Android Bitmaps.
 * Uses double buffering to ensure ImageView always sees a "new" Bitmap object.
 */
public class FrameRenderer {

    private Bitmap[]  bitmaps = new Bitmap[2];
    private int       idx = 0;
    private int[]     argbBuf;
    private int       lastWidth;
    private int       lastHeight;

    public synchronized Bitmap render(float[] pixels, int width, int height) {
        if (width <= 0 || height <= 0 || pixels == null) return null;

        // Re-allocate only when dimensions change
        if (bitmaps[0] == null || lastWidth != width || lastHeight != height) {
            if (bitmaps[0] != null) bitmaps[0].recycle();
            if (bitmaps[1] != null) bitmaps[1].recycle();
            bitmaps[0] = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmaps[1] = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            argbBuf    = new int[width * height];
            lastWidth  = width;
            lastHeight = height;
        }

        // Swap buffers
        idx = (idx + 1) % 2;
        Bitmap bitmap = bitmaps[idx];

        // Find min/max for contrast stretch
        float mn =  Float.MAX_VALUE;
        float mx = -Float.MAX_VALUE;
        for (float v : pixels) {
            if (v < mn) mn = v;
            if (v > mx) mx = v;
        }
        float range = mx - mn;
        if (range < 1e-6f) range = 1.0f;

        // Convert to ARGB
        int total = width * height;
        for (int i = 0; i < total; i++) {
            int g = Math.min(255, Math.max(0, (int)(((pixels[i] - mn) / range) * 255f)));
            argbBuf[i] = 0xFF000000 | (g << 16) | (g << 8) | g;
        }

        bitmap.setPixels(argbBuf, 0, width, 0, 0, width, height);
        return bitmap;
    }

    public void release() {
        if (bitmaps[0] != null) { bitmaps[0].recycle(); bitmaps[0] = null; }
        if (bitmaps[1] != null) { bitmaps[1].recycle(); bitmaps[1] = null; }
        argbBuf = null;
    }
}
