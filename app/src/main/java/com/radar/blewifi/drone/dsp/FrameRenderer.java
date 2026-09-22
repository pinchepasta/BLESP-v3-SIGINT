package com.radar.blewifi.drone.dsp;

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

    public synchronized Bitmap render(float[] pixels, int width, int height, int primaryColor, int backgroundColor) {
        if (width <= 0 || height <= 0 || pixels == null) return null;

        int total = width * height;
        // Re-allocate only when dimensions change
        if (bitmaps[0] == null || lastWidth != width || lastHeight != height) {
            if (bitmaps[0] != null) bitmaps[0].recycle();
            if (bitmaps[1] != null) bitmaps[1].recycle();
            bitmaps[0] = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmaps[1] = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            argbBuf    = new int[total];
            lastWidth  = width;
            lastHeight = height;
        }

        // Swap buffers
        idx = (idx + 1) % 2;
        Bitmap bitmap = bitmaps[idx];

        // Find min/max for contrast stretch
        float mn =  Float.MAX_VALUE;
        float mx = -Float.MAX_VALUE;
        for (int i = 0; i < total; i++) {
            float v = pixels[i];
            if (v < mn) mn = v;
            if (v > mx) mx = v;
        }
        
        float range = mx - mn;
        float scale = (range < 1e-6f) ? 1.0f : 1.0f / range;

        int br = (backgroundColor >> 16) & 0xFF;
        int bg = (backgroundColor >> 8) & 0xFF;
        int bb = backgroundColor & 0xFF;

        int pr = (primaryColor >> 16) & 0xFF;
        int pg = (primaryColor >> 8) & 0xFF;
        int pb = primaryColor & 0xFF;

        int dr = pr - br;
        int dg = pg - bg;
        int db = pb - bb;

        // Convert to colored frame based on primary and background
        for (int i = 0; i < total; i++) {
            float norm = (pixels[i] - mn) * scale;
            if (norm > 1.0f) norm = 1.0f; else if (norm < 0) norm = 0;
            
            int r = br + (int)(norm * dr);
            int g = bg + (int)(norm * dg);
            int b = bb + (int)(norm * db);
            
            argbBuf[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
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
