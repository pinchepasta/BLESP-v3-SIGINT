package com.radar.blewifi.drone.core;

import android.util.Log;

/**
 * Java wrapper for the TempestSDR native library.
 *
 * Mirrors the original TSDRLibrary.java from martinmarinov/TempestSDR
 * but adapted for Android JNI (no CLASSPATH loading; uses System.loadLibrary).
 *
 * Call order:
 *   create() → connectFd(fd) → setResolution() → start() → ... → stop() → disconnect() → destroy()
 */
public class TSDRLibrary {

    private static final String TAG = "TSDRLibrary";

    static {
        System.loadLibrary("tempestsdr_jni");
    }

    // ── DSP parameter IDs (mirror C defines) ──────────────────────────
    public static final int PARAM_AUTOSHIFT                 = 0;
    public static final int PARAM_PLLFRAMERATE              = 1;
    public static final int PARAM_AUTOCORR_PLOTS_RESET      = 2;
    public static final int PARAM_AUTOCORR_PLOTS_OFF        = 3;
    public static final int PARAM_SUPERRESOLUTION           = 4;
    public static final int PARAM_NEAREST_NEIGHBOUR_RESAMP  = 5;
    public static final int PARAM_LOW_PASS_BEFORE_SYNC      = 6;
    public static final int PARAM_AUTOGAIN_AFTER_PROCESSING = 7;

    // ── Common video modes (height, refresh) ──────────────────────────
    public enum VideoMode {
        NTSC_CCTV(525,  59.94, "Analog CCTV (NTSC 525 lines @ 59.94 Hz)"),
        PAL_CCTV (625,  50.0,  "Analog CCTV (PAL 625 lines @ 50 Hz)"),
        VGA_60   (628,  60.0,  "800×600 @ 60 Hz"),
        VGA_75   (625,  75.0,  "800×600 @ 75 Hz"),
        SVGA_60  (800,  60.0,  "1024×768 @ 60 Hz"),
        FHD_60   (1125, 60.0,  "1920×1080 @ 60 Hz"),
        HD_60    (750,  60.0,  "1280×720 @ 60 Hz"),
        CUSTOM   (0,    0.0,   "Custom");

        public final int    totalHeight;
        public final double refreshHz;
        public final String label;

        VideoMode(int h, double r, String l) {
            totalHeight = h; refreshHz = r; label = l;
        }

        @Override public String toString() { return label; }
    }

    // ── Callbacks ─────────────────────────────────────────────────────
    public interface FrameReadyCallback {
        /** Called on a background thread for each reconstructed video frame. */
        void onFrameReady(float[] pixels, int width, int height);
    }

    public interface ErrorCallback {
        void onError(int code, String message);
    }

    // ── Internal state ─────────────────────────────────────────────────
    private long             nativePtr;
    private FrameReadyCallback frameListener;
    private ErrorCallback      errorListener;
    private boolean            running;

    // ── Lifecycle ──────────────────────────────────────────────────────
    public TSDRLibrary() {
        nativePtr = nativeCreate();
        Log.i(TAG, "created nativePtr=" + nativePtr);
    }

    public void destroy() {
        if (nativePtr != 0) {
            nativeDestroy(nativePtr);
            nativePtr = 0;
        }
    }

    // ── Connection ─────────────────────────────────────────────────────
    /**
     * Connect to an RTL-SDR dongle via the Android USB Host file descriptor.
     * Obtain fd from: UsbDeviceConnection.getFileDescriptor()
     */
    public int connectFd(int usbFd) {
        return nativeConnectFd(nativePtr, usbFd);
    }

    public int disconnect() {
        return nativeDisconnect(nativePtr);
    }

    // ── SDR parameters ─────────────────────────────────────────────────
    public synchronized int setFrequency(long freqHz) {
        if (nativePtr == 0) return -1;
        nativeClearBuffers(nativePtr); // Clear jitter/noise from previous frequency
        return nativeSetFreq(nativePtr, freqHz);
    }

    public synchronized int setGain(double gainDb) {
        if (nativePtr == 0) return -1;
        return nativeSetGain(nativePtr, gainDb);
    }

    public synchronized int setSampleRate(int rate) {
        if (nativePtr == 0) return -1;
        return nativeSetSampleRate(nativePtr, rate);
    }

    // ── Video parameters ────────────────────────────────────────────────
    public synchronized int setResolution(int totalHeight, double refreshHz) {
        if (nativePtr == 0) return -1;
        return nativeSetResolution(nativePtr, totalHeight, refreshHz);
    }

    public int setResolution(VideoMode mode) {
        return setResolution(mode.totalHeight, mode.refreshHz);
    }

    // ── Processing control ──────────────────────────────────────────────
    public void setFrameReadyCallback(FrameReadyCallback cb) { frameListener = cb; }
    public void setErrorCallback(ErrorCallback cb)           { errorListener = cb; }

    public synchronized int start() {
        if (nativePtr == 0) return -1;
        int rc = nativeStart(nativePtr);
        if (rc == 0) running = true;
        return rc;
    }

    public synchronized int stop() {
        if (nativePtr == 0) return -1;
        running = false;
        return nativeStop(nativePtr);
    }

    public synchronized boolean isRunning() {
        if (nativePtr == 0) return false;
        return nativeIsRunning(nativePtr);
    }

    // ── DSP parameters ──────────────────────────────────────────────────
    public synchronized int setParam(int paramId, double value) {
        if (nativePtr == 0) return -1;
        return nativeSetParam(nativePtr, paramId, value);
    }

    public synchronized int setGainOffset(double offset) {
        if (nativePtr == 0) return -1;
        return nativeSetGainOffset(nativePtr, offset);
    }

    public synchronized int setShift(double shift) {
        if (nativePtr == 0) return -1;
        return nativeSetShift(nativePtr, shift);
    }

    public synchronized float[] getAutocorr() {
        if (nativePtr == 0) return null;
        return nativeGetAutocorr(nativePtr);
    }

    // ── Called by JNI on frame callback thread ───────────────────────
    @SuppressWarnings("unused")
    private void onFrame(float[] pixels, int width, int height) {
        FrameReadyCallback cb = frameListener;
        if (cb != null) cb.onFrameReady(pixels, width, height);
    }

    @SuppressWarnings("unused")
    private void onError(int code, String message) {
        Log.e(TAG, "Native error " + code + ": " + message);
        ErrorCallback cb = errorListener;
        if (cb != null) cb.onError(code, message);
    }

    // ── Native declarations ──────────────────────────────────────────
    private native long        nativeCreate();
    private native void        nativeDestroy(long ptr);
    private native int         nativeConnectFd(long ptr, int fd);
    private native int         nativeDisconnect(long ptr);
    private native int         nativeSetFreq(long ptr, long freq);
    private native int         nativeClearBuffers(long ptr);
    private native int         nativeSetGain(long ptr, double gain);
    private native int         nativeSetSampleRate(long ptr, int rate);
    private native int         nativeSetResolution(long ptr, int height, double refresh);
    private native int         nativeStart(long ptr);
    private native int         nativeStop(long ptr);
    private native boolean     nativeIsRunning(long ptr);
    private native int         nativeSetParam(long ptr, int id, double val);
    private native int         nativeSetGainOffset(long ptr, double offset);
    private native int         nativeSetShift(long ptr, double shift);
    private native float[]     nativeGetAutocorr(long ptr);
}
