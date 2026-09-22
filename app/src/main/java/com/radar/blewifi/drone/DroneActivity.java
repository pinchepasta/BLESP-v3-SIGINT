package com.radar.blewifi.drone;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.radar.blewifi.R;
import com.radar.blewifi.RadarView;
import com.radar.blewifi.ThemeUtils;
import com.radar.blewifi.drone.rtlsdr.RtlSdrManager;
import com.radar.blewifi.drone.ui.AutocorrView;
import com.radar.blewifi.drone.ui.MainViewModel;
import com.radar.blewifi.drone.ui.SettingsDialog;
import com.radar.blewifi.drone.ui.SignalGraphView;

import java.util.Locale;

public class DroneActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private MainViewModel vm;
    private ThemeUtils.ThemeColors currentThemeColors;

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
    private MaterialButton btnBack;
    private ProgressBar   pbScan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.drone_activity_main);

        vm = new ViewModelProvider(this).get(MainViewModel.class);

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
        btnBack       = findViewById(R.id.btn_back);
        pbScan        = findViewById(R.id.pb_scan);

        applyTheme();

        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        // ── Observe LiveData ──────────────────────────────────────────
        vm.frameLive.observe(this, this::onFrame);
        vm.statusLive.observe(this, s -> {
            tvStatus.setText(s);
            String mode;
            if (s.contains("[")) {
                mode = s.substring(s.indexOf("[") + 1, s.indexOf("]"));
            } else {
                mode = getString(R.string.drone_hud_mode_default);
            }
            tvHudMode.setText(getString(R.string.drone_hud_mode_prefix, mode));
        });
        vm.frequencyLive.observe(this, f -> {
            tvHudFreq.setText(getString(R.string.drone_hud_freq_format, String.format(Locale.US, "%.3f", f / 1_000_000.0)));
            MainViewModel.Preset p = vm.getCurrentPreset();
            String target = (p != null) ? p.name.toUpperCase() : getString(R.string.drone_hud_target_no_lock);
            tvHudTarget.setText(getString(R.string.drone_hud_target_prefix, target));
        });
        vm.runningLive.observe(this, running -> {
            fabStart.setImageResource(running
                    ? android.R.drawable.ic_media_pause
                    : android.R.drawable.ic_media_play);
            tvHudMode.setText(getString(R.string.drone_hud_mode_prefix, 
                running ? getString(R.string.drone_hud_mode_active) : getString(R.string.drone_hud_mode_standby)));
        });
        vm.errorLive.observe(this, msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
        vm.autocorrLive.observe(this, data -> autocorrView.setData(data));
        vm.signalLive.observe(this, strength -> {
            tvHudSnr.setText(getString(R.string.drone_hud_snr_format, String.format(Locale.US, "%.1f", strength / 10.0))); // Rough estimate
            signalGraph.addValue(strength);
        });
        vm.turboLockLive.observe(this, isLocked -> {
            if (isLocked) {
                tvHudTarget.setText(R.string.drone_hud_turbo_lock);
                tvHudTarget.setTextColor(currentThemeColors != null ? currentThemeColors.getAccent() : 0xFF00FF00);
                // Simple flashing effect
                tvHudTarget.clearAnimation();
                AlphaAnimation blink = new AlphaAnimation(0.0f, 1.0f);
                blink.setDuration(100);
                blink.setRepeatMode(Animation.REVERSE);
                blink.setRepeatCount(Animation.INFINITE);
                tvHudTarget.startAnimation(blink);
            } else {
                tvHudTarget.clearAnimation();
                // Reset will be handled by presetsLive observer or manual update
                MainViewModel.Preset p = vm.getCurrentPreset();
                String target = (p != null) ? p.name.toUpperCase() : getString(R.string.drone_hud_target_no_lock);
                tvHudTarget.setText(getString(R.string.drone_hud_target_prefix, target));
                tvHudTarget.setTextColor(currentThemeColors != null ? currentThemeColors.getPrimary() : 0xFF888888);
            }
        });

        vm.sweepLive.observe(this, sweeping -> {
            btnScan.setText(sweeping ? "Stop" : "Scan");
            if (currentThemeColors != null) {
                int color = sweeping ? currentThemeColors.getAccent() : currentThemeColors.getPrimary();
                btnScan.setBackgroundTintList(ColorStateList.valueOf(color));
                btnScan.setTextColor(currentThemeColors.getBackground());
            }
        });
        vm.droneScanLive.observe(this, scanning -> {
            btnDroneScan.setText(scanning ? "Stop" : "Drone");
            if (currentThemeColors != null) {
                int color = scanning ? currentThemeColors.getAccent() : currentThemeColors.getPrimary();
                btnDroneScan.setBackgroundTintList(ColorStateList.valueOf(color));
                btnDroneScan.setTextColor(currentThemeColors.getBackground());
            }
            pbScan.setVisibility(scanning ? View.VISIBLE : View.GONE);
        });
        vm.scanProgressLive.observe(this, progress -> pbScan.setProgress(progress, true));
        vm.scanTotalLive.observe(this, total -> pbScan.setMax(total));

        autocorrView.setOnTapListener(vm::syncToPeak);

        vm.presetsLive.observe(this, presets -> {
            MainViewModel.Preset p = vm.getCurrentPreset();
            // Button is now always visible for manual intel access
            String target = (p != null) ? p.name.toUpperCase() : getString(R.string.drone_hud_target_no_lock);
            tvHudTarget.setText(getString(R.string.drone_hud_target_prefix, target));
            tvHudTarget.setTextColor(currentThemeColors != null ? currentThemeColors.getPrimary() : 0xFF888888);
        });

        // ── Fine Tune Logic ──────────────────────────────────────────
        sbFineTune.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private long baseFreq;
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    long offset = (progress - 1000) * 1000L; // ±1 MHz range, 1kHz steps
                    vm.setFrequency(baseFreq + offset, true);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                baseFreq = vm.getFrequencyHz();
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(1000); // Reset to center
            }
        });

        // ── Button listeners ─────────────────────────────────────────
        btnScan.setOnClickListener(v -> vm.setSweepEnabled(!vm.isSweepEnabled()));
        btnDroneScan.setOnClickListener(v -> vm.setDroneScanEnabled(!vm.isDroneScanEnabled()));
        fabStart.setOnClickListener(v -> onStartStopClicked());
        btnSettings.setOnClickListener(v ->
                new SettingsDialog().show(getSupportFragmentManager(), "settings"));

        // ── Immersive Mode ────────────────────────────────────────────
        setupImmersiveMode();

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
    protected void onResume() {
        super.onResume();
        applyTheme();
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

    private void requestAndConnect(UsbDevice device) {
        vm.getRtlMgr().requestPermission(device, new RtlSdrManager.Callback() {
            @Override public void onDeviceFound(UsbDevice d) {}
            @Override public void onPermissionGranted(UsbDevice d, int fd) {
                runOnUiThread(() -> vm.connectDevice(fd));
            }
            @Override public void onPermissionDenied(UsbDevice d) {
                runOnUiThread(() ->
                        Toast.makeText(DroneActivity.this, R.string.drone_permission_denied, Toast.LENGTH_SHORT).show());
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

    private void onFrame(Bitmap bmp) {
        if (bmp != null) {
            // Use a new BitmapDrawable to bypass ImageView's "same bitmap" optimization
            // and force it to redraw the pixels in the reused Bitmap object.
            ivFrame.setImageDrawable(new android.graphics.drawable.BitmapDrawable(getResources(), bmp));
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setupImmersiveMode();
        }
    }

    private void setupImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private void applyTheme() {
        android.content.SharedPreferences prefs = getSharedPreferences("settings", android.content.Context.MODE_PRIVATE);
        String themeName = prefs.getString("theme_name", RadarView.Theme.DEFAULT.name());
        RadarView.Theme currentTheme;
        try {
            currentTheme = RadarView.Theme.valueOf(themeName);
        } catch (Exception e) {
            currentTheme = RadarView.Theme.DEFAULT;
        }

        currentThemeColors = ThemeUtils.INSTANCE.getThemeColors(currentTheme);
        ThemeUtils.ThemeColors colors = currentThemeColors;
        
        vm.setThemeColors(colors.getPrimary(), colors.getAccent(), colors.getBackground());
        
        // Root background
        findViewById(android.R.id.content).setBackgroundColor(colors.getBackground());

        if (autocorrView != null) {
            autocorrView.updateColors(colors.getPrimary(), colors.getAccent(), colors.getBackground(), colors.getPrimary());
        }
        if (signalGraph != null) {
            signalGraph.updateColors(colors.getPrimary(), colors.getAccent(), colors.getBackground());
        }

        // HUD Text Colors
        if (tvHudFreq != null) tvHudFreq.setTextColor(colors.getPrimary());
        if (tvHudSnr != null) tvHudSnr.setTextColor(colors.getPrimary());
        if (tvHudMode != null) tvHudMode.setTextColor(colors.getPrimary());
        if (tvHudTarget != null) {
            MainViewModel.Preset p = vm.getCurrentPreset();
            tvHudTarget.setTextColor(colors.getPrimary());
        }

        // Buttons
        if (btnScan != null) {
            btnScan.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colors.getPrimary()));
            btnScan.setTextColor(colors.getBackground());
            btnScan.setRippleColor(android.content.res.ColorStateList.valueOf(colors.getAccent()));
        }
        if (btnDroneScan != null) {
            btnDroneScan.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colors.getPrimary()));
            btnDroneScan.setTextColor(colors.getBackground());
            btnDroneScan.setRippleColor(android.content.res.ColorStateList.valueOf(colors.getAccent()));
        }
        if (btnBack != null) {
            btnBack.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            btnBack.setStrokeWidth(0);
            btnBack.setTextColor(colors.getPrimary());
            btnBack.setRippleColor(android.content.res.ColorStateList.valueOf((colors.getPrimary() & 0x00FFFFFF) | 0x33000000));
        }
        if (btnSettings != null) {
            btnSettings.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colors.getPrimary()));
            btnSettings.setIconTint(android.content.res.ColorStateList.valueOf(colors.getBackground()));
        }
        
        if (fabStart != null) {
            fabStart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colors.getAccent()));
            fabStart.setSupportImageTintList(android.content.res.ColorStateList.valueOf(colors.getBackground()));
        }

        View bottomPanel = findViewById(R.id.bottom_panel);
        if (bottomPanel instanceof com.google.android.material.card.MaterialCardView card) {
            card.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(colors.getBackground()));
            card.setStrokeColor(android.content.res.ColorStateList.valueOf(colors.getPrimary()));
        }

        if (tvStatus != null) {
            tvStatus.setTextColor(colors.getPrimary());
        }

        if (pbScan != null) {
            pbScan.setProgressTintList(android.content.res.ColorStateList.valueOf(colors.getAccent()));
            pbScan.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(colors.getPrimary()));
        }

        // Tint HUD elements (views in ConstraintLayout hud_overlay)
        View hudOverlay = findViewById(R.id.hud_overlay);
        if (hudOverlay instanceof androidx.constraintlayout.widget.ConstraintLayout) {
            // Find crosshair views (they don't have IDs but we can find them by background color or layout)
            for (int i = 0; i < ((androidx.constraintlayout.widget.ConstraintLayout) hudOverlay).getChildCount(); i++) {
                View child = ((androidx.constraintlayout.widget.ConstraintLayout) hudOverlay).getChildAt(i);
                if (child.getId() == View.NO_ID && child.getBackground() != null) {
                    child.setBackgroundColor((colors.getPrimary() & 0x00FFFFFF) | 0x44000000);
                }
            }
        }
    }

    private int getThemeColor(int attr) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
