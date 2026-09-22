package com.tempestsdr;

import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.tempestsdr.rtlsdr.RtlSdrManager;
import com.tempestsdr.ui.AutocorrView;
import com.tempestsdr.ui.MainViewModel;
import com.tempestsdr.ui.SettingsDialog;
import com.tempestsdr.ui.SignalGraphView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private MainViewModel vm;

    // ── Views ─────────────────────────────────────────────────────────
    private ImageView     ivFrame;
    private AutocorrView  autocorrView;
    private SignalGraphView signalGraph;
    private TextView      tvStatus;
    private TextView      tvHudFreq;
    private TextView      tvHudSnr;
    private TextView      tvHudMode;
    private TextView      tvHudTarget;
    private SeekBar       sbFineTune;
    private FloatingActionButton fabStart;
    private MaterialButton btnScan;
    private MaterialButton btnDroneScan;
    private MaterialButton btnSettings;
    private ProgressBar   pbScan;
    private View          loadingOverlay;
    private View          loadingGlitchWrapper;
    private ImageView     ivLoadingLogo;
    private ImageView     ivLoadingStatic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        vm = new ViewModelProvider(this).get(MainViewModel.class);
        if (vm.isCyberpunkMode()) {
            setTheme(R.style.Theme_RFVideoReceiver_Cyberpunk);
        } else {
            setTheme(R.style.Theme_RFVideoReceiver);
        }

        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        ivFrame       = findViewById(R.id.iv_frame);
        autocorrView  = findViewById(R.id.autocorr_view);
        signalGraph   = findViewById(R.id.signal_graph);
        tvStatus      = findViewById(R.id.tv_status);
        tvHudFreq     = findViewById(R.id.tv_hud_freq);
        tvHudSnr      = findViewById(R.id.tv_hud_snr);
        tvHudMode     = findViewById(R.id.tv_hud_mode);
        tvHudTarget   = findViewById(R.id.tv_hud_target);
        sbFineTune    = findViewById(R.id.sb_fine_tune);
        fabStart      = findViewById(R.id.fab_start);
        btnScan       = findViewById(R.id.btn_scan);
        btnDroneScan  = findViewById(R.id.btn_drone_scan);
        btnSettings   = findViewById(R.id.btn_settings);
        pbScan        = findViewById(R.id.pb_scan);
        loadingOverlay= findViewById(R.id.loading_overlay);
        loadingGlitchWrapper = findViewById(R.id.loading_glitch_wrapper);
        ivLoadingLogo = findViewById(R.id.iv_loading_logo);
        ivLoadingStatic = findViewById(R.id.iv_loading_static);

        // ── Initial State: Full Black Screen ──
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingOverlay.setAlpha(1f);
        loadingOverlay.setScaleX(1f);
        loadingOverlay.setScaleY(1f);
        
        // Collapse the content for the "TV On" start
        loadingGlitchWrapper.setScaleX(0f);
        loadingGlitchWrapper.setScaleY(0.002f);
        loadingGlitchWrapper.setVisibility(View.INVISIBLE);

        // ── CRT Animation Sequence ──
        // 1. Show Black Screen for 400ms
        // 2. Start "TV On" Animation
        loadingOverlay.postDelayed(() -> {
            loadingGlitchWrapper.setVisibility(View.VISIBLE);
            
            // Start glitching as soon as the beam appears
            applyCrtGlitch(loadingGlitchWrapper, 1200);

                    loadingGlitchWrapper.animate()
                            .scaleX(1f)
                            .setDuration(200)
                            .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                            .withEndAction(() -> {
                                loadingGlitchWrapper.animate()
                                        .scaleY(1f)
                                        .setDuration(250)
                                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                        .start();
                            })
                            .start();
        }, 400);

        // ── Start Static Animation ──
        startStaticAnimation();

        // ── Transition to App (Instant Reveal) ──
        loadingOverlay.postDelayed(() -> {
            if (loadingOverlay.getVisibility() == View.VISIBLE) {
                loadingOverlay.setVisibility(View.GONE);
            }
        }, 2500);

        // ── Observe LiveData ──────────────────────────────────────────
        vm.frameLive.observe(this, this::onFrame);
        vm.statusLive.observe(this, s -> {
            tvStatus.setText(s);
            String mode;
            if (s.contains("[")) {
                mode = s.substring(s.indexOf("[") + 1, s.indexOf("]"));
            } else {
                mode = getString(R.string.hud_mode_default);
            }
            tvHudMode.setText(getString(R.string.hud_mode_prefix, mode));
        });
        vm.frequencyLive.observe(this, f -> {
            tvHudFreq.setText(getString(R.string.hud_freq_format, String.format(Locale.US, "%.3f", f / 1_000_000.0)));
        });
        vm.runningLive.observe(this, running -> {
            fabStart.setImageResource(running
                    ? android.R.drawable.ic_media_pause
                    : android.R.drawable.ic_media_play);
            tvHudMode.setText(getString(R.string.hud_mode_prefix, 
                running ? getString(R.string.hud_mode_active) : getString(R.string.hud_mode_standby)));
        });
        vm.errorLive.observe(this, msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
        vm.autocorrLive.observe(this, data -> autocorrView.setData(data));
        vm.signalLive.observe(this, strength -> {
            tvHudSnr.setText(getString(R.string.hud_snr_format, String.format(Locale.US, "%.1f", strength / 10.0))); // Rough estimate
            signalGraph.addValue(strength);
        });
        vm.fullscreenLive.observe(this, this::setFullscreenMode);
        vm.sweepLive.observe(this, sweeping -> {
            btnScan.setText(sweeping ? "Stop" : "Scan");
            int color;
            if (vm.isCyberpunkMode()) {
                color = 0xFFFF00FF; // Always pink in Cyberpunk
            } else {
                color = sweeping ? 0xFFFF3D00 : 0xFFD84315;
            }
            btnScan.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        });
        vm.droneScanLive.observe(this, scanning -> {
            btnDroneScan.setText(scanning ? "Stop" : "Drone");
            int color;
            if (vm.isCyberpunkMode()) {
                color = 0xFFFF00FF; // Always pink in Cyberpunk
            } else {
                color = scanning ? 0xFFFF3D00 : 0xFFEF6C00;
            }
            btnDroneScan.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
            pbScan.setVisibility(scanning ? View.VISIBLE : View.GONE);
        });
        vm.scanProgressLive.observe(this, progress -> {
            pbScan.setProgress(progress, true);
        });
        vm.scanTotalLive.observe(this, total -> pbScan.setMax(total));

        autocorrView.setOnTapListener(index -> {
            vm.syncToPeak(index);
        });

        // ── Fullscreen Toggle on Double Tap ──────────────────────────
        ivFrame.setOnClickListener(new View.OnClickListener() {
            private long lastClickTime = 0;
            @Override public void onClick(View v) {
                long now = System.currentTimeMillis();
                if (now - lastClickTime < 300) {
                    Boolean current = vm.fullscreenLive.getValue();
                    vm.fullscreenLive.setValue(current == null || !current);
                }
                lastClickTime = now;
            }
        });

        // ── Fine Tune Logic ──────────────────────────────────────────
        sbFineTune.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            private long baseFreq;
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    long offset = (progress - 1000) * 1000L; // ±1 MHz range, 1kHz steps
                    vm.setFrequency(baseFreq + offset);
                }
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
                baseFreq = vm.getFrequencyHz();
            }
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
                seekBar.setProgress(1000); // Reset to center
            }
        });

        // ── Button listeners ─────────────────────────────────────────
        btnScan.setOnClickListener(v -> vm.setSweepEnabled(!vm.isSweepEnabled()));
        btnDroneScan.setOnClickListener(v -> vm.setDroneScanEnabled(!vm.isDroneScanEnabled()));
        fabStart.setOnClickListener(v -> onStartStopClicked());
        btnSettings.setOnClickListener(v ->
                new SettingsDialog().show(getSupportFragmentManager(), "settings"));

        View btnQuickInfo = findViewById(R.id.btn_quick_info);
        btnQuickInfo.setVisibility(View.VISIBLE); // Always show tactical info button
        btnQuickInfo.setOnClickListener(v -> {
            MainViewModel.Preset p = vm.getCurrentPreset();
            String title, hardware, antenna, vendor, components;
            double freq;
            boolean unencrypted;

            if (p != null && p.frequencyHz > 0) {
                title = p.name;
                freq = p.frequencyHz / 1_000_000.0;
                hardware = p.hardwareInfo;
                antenna = p.antennaInfo;
                vendor = p.vendorInfo;
                components = p.components;
                unencrypted = p.unencrypted;
            } else {
                title = "MANUAL INTERCEPT / UNKNOWN";
                freq = vm.getFrequencyHz() / 1_000_000.0;
                hardware = "Tactical SDR Suite (RTL-SDR/HackRF)";
                antenna = "Wideband Acquisition Antenna";
                vendor = "N/A";
                components = "No matching ELINT signature in database";
                unencrypted = true;
            }

            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(getString(R.string.preset_info_format,
                    freq, hardware, antenna, vendor, components,
                    unencrypted ? getString(R.string.signal_unencrypted) : getString(R.string.signal_encrypted)))
                .setPositiveButton(android.R.string.ok, null)
                .show();
        });

        vm.presetsLive.observe(this, presets -> {
            MainViewModel.Preset p = vm.getCurrentPreset();
            // Button is now always visible for manual intel access
            String target = (p != null) ? p.name.toUpperCase() : getString(R.string.hud_target_no_lock);
            tvHudTarget.setText(getString(R.string.hud_target_prefix, target));
            tvHudTarget.setTextColor(p != null ? getThemeColor(com.google.android.material.R.attr.colorPrimary) : 0xFF888888);
        });

        // ── Handle USB device attached via intent ─────────────────────
        handleUsbIntent(getIntent());

        // ── Check for already connected device ────────────────────────
        checkExistingDevice();
    }

    private void checkExistingDevice() {
        java.util.List<UsbDevice> devices = vm.getRtlMgr().findDevices();
        if (!devices.isEmpty()) {
            requestAndConnect(devices.get(0));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleUsbIntent(intent);
    }

    private void handleUsbIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Log.i(TAG, "handleUsbIntent: action=" + action);

        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            UsbDevice device;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
            } else {
                device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            }

            if (device != null) {
                Log.i(TAG, "USB device attached via intent: " + device.getDeviceName());
                requestAndConnect(device);
            } else {
                Log.w(TAG, "USB_DEVICE_ATTACHED intent but device is null");
            }
        }
    }

    private void onConnectClicked() {
        vm.getRtlMgr().requestFirstDevice(new RtlSdrManager.Callback() {
            @Override public void onDeviceFound(UsbDevice device) {
                runOnUiThread(() -> tvStatus.setText(R.string.status_finding_device));
            }
            @Override public void onPermissionGranted(UsbDevice device, int usbFd) {
                runOnUiThread(() -> {
                    tvStatus.setText(R.string.status_requesting_permission);
                    vm.connectDevice(usbFd);
                });
            }
            @Override public void onPermissionDenied(UsbDevice device) {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, R.string.permission_denied, Toast.LENGTH_SHORT).show());
            }
            @Override public void onNoDevice() {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, R.string.no_device, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void requestAndConnect(UsbDevice device) {
        vm.getRtlMgr().requestPermission(device, new RtlSdrManager.Callback() {
            @Override public void onDeviceFound(UsbDevice d) {}
            @Override public void onPermissionGranted(UsbDevice d, int fd) {
                runOnUiThread(() -> vm.connectDevice(fd));
            }
            @Override public void onPermissionDenied(UsbDevice d) {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, R.string.permission_denied, Toast.LENGTH_SHORT).show());
            }
            @Override public void onNoDevice() {}
        });
    }

    private void onStartStopClicked() {
        if (Boolean.TRUE.equals(vm.runningLive.getValue())) {
            vm.stopProcessing();
        } else {
            vm.startProcessing();
        }
    }

    private void startStaticAnimation() {
        if (ivLoadingStatic == null) return;
        
        final int w = 256;
        final int h = 256;
        final Bitmap staticBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        final java.util.Random rnd = new java.util.Random();
        final int[] pixels = new int[w * h];

        Runnable updateStatic = new Runnable() {
            @Override
            public void run() {
                if (loadingOverlay.getVisibility() != View.VISIBLE) return;
                
                for (int i = 0; i < pixels.length; i++) {
                    int val = rnd.nextInt(256);
                    pixels[i] = 0xFF000000 | (val << 16) | (val << 8) | val;
                }
                staticBmp.setPixels(pixels, 0, w, 0, 0, w, h);
                ivLoadingStatic.setImageBitmap(staticBmp);
                ivLoadingStatic.postDelayed(this, 50); // 20 FPS static
            }
        };
        ivLoadingStatic.post(updateStatic);
    }

    private void applyCrtGlitch(final View view, long duration) {
        if (view == null) return;
        final long startTime = System.currentTimeMillis();
        final java.util.Random rnd = new java.util.Random();
        
        view.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < duration && loadingOverlay.getVisibility() == View.VISIBLE) {
                    // Reduced CRT Jitter
                    float jitterX = (rnd.nextFloat() - 0.5f) * 15f;
                    if (rnd.nextFloat() > 0.95f) jitterX *= 2.0f; // Rare small jump
                    view.setTranslationX(jitterX);
                    
                    if (rnd.nextFloat() > 0.95f) {
                        view.setTranslationY((rnd.nextFloat() - 0.5f) * 5f);
                    } else {
                        view.setTranslationY(0);
                    }
                    
                    // Subtle Skew/Squeeze
                    if (rnd.nextFloat() > 0.8f) {
                        view.setScaleX(1.0f + (rnd.nextFloat() - 0.5f) * 0.1f);
                        view.setScaleY(1.0f + (rnd.nextFloat() - 0.5f) * 0.05f);
                    } else {
                        view.setScaleX(1.0f);
                        view.setScaleY(1.0f);
                    }
                    
                    // Minimal Rotation
                    if (rnd.nextFloat() > 0.9f) {
                        view.setRotation((rnd.nextFloat() - 0.5f) * 1.5f);
                    } else {
                        view.setRotation(0);
                    }

                    // Brightness/Alpha flicker
                    if (rnd.nextFloat() > 0.7f) {
                        view.setAlpha(0.5f + rnd.nextFloat() * 0.5f);
                    } else {
                        view.setAlpha(1.0f);
                    }

                    view.postDelayed(this, 10 + rnd.nextInt(30));
                } else {
                    view.setTranslationX(0);
                    view.setTranslationY(0);
                    view.setRotation(0);
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                    view.setAlpha(1f);
                }
            }
        });
    }

    private void onFrame(Bitmap bmp) {
        if (bmp != null) {
            // Use a new BitmapDrawable to bypass ImageView's "same bitmap" optimization
            // and force it to redraw the pixels in the reused Bitmap object.
            ivFrame.setImageDrawable(new android.graphics.drawable.BitmapDrawable(getResources(), bmp));
        }
    }

    private void setFullscreenMode(boolean fullscreen) {
        View bottomPanel = findViewById(R.id.bottom_panel);
        bottomPanel.setVisibility(fullscreen ? View.GONE : View.VISIBLE);

        if (fullscreen) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (getSupportActionBar() != null) getSupportActionBar().hide();
            // Optional: Hide navigation bar as well for true immersive
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (getSupportActionBar() != null) getSupportActionBar().show();
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private int getThemeColor(int attr) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }
}
