package com.radar.blewifi.drone.ui;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.radar.blewifi.R;
import com.radar.blewifi.drone.core.TSDRLibrary;

import java.util.Locale;

/**
 * Settings bottom-sheet: frequency, gain, sample rate, video mode, DSP params.
 */
public class SettingsDialog extends BottomSheetDialogFragment {

    private MainViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.drone_dialog_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        // ── Frequency ──────────────────────────────────────────────────
        EditText etFreq = view.findViewById(R.id.et_freq_mhz);
        etFreq.setText(String.format(Locale.US, "%.3f", vm.getFrequencyHz() / 1_000_000.0));
        etFreq.setOnEditorActionListener((v, actionId, event) -> {
            applyFreqFromEditText(etFreq);
            return false;
        });
        etFreq.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                applyFreqFromEditText(etFreq);
            }
        });

        view.findViewById(R.id.btn_freq_minus_coarse).setOnClickListener(v -> {
            adjustFreq(etFreq, -1.0);
        });

        view.findViewById(R.id.btn_freq_minus).setOnClickListener(v -> {
            adjustFreq(etFreq, -0.01);
        });

        view.findViewById(R.id.btn_freq_plus).setOnClickListener(v -> {
            adjustFreq(etFreq, 0.01);
        });

        view.findViewById(R.id.btn_freq_plus_coarse).setOnClickListener(v -> {
            adjustFreq(etFreq, 1.0);
        });

        // ── Frequency Presets ──────────────────────────────────────────
        Spinner spPresets = view.findViewById(R.id.sp_freq_presets);
        View btnDelete = view.findViewById(R.id.btn_delete_preset);
        View btnInfo = view.findViewById(R.id.btn_info_preset);
        EditText etNewName = view.findViewById(R.id.et_new_preset_name);
        Button btnAdd = view.findViewById(R.id.btn_add_preset);

        final boolean[] isInitializingSpinner = {true};

        vm.presetsLive.observe(getViewLifecycleOwner(), presets -> {
            isInitializingSpinner[0] = true;
            ArrayAdapter<MainViewModel.Preset> adapter = new ArrayAdapter<>(requireContext(),
                    R.layout.drone_simple_spinner_item, presets);
            adapter.setDropDownViewResource(R.layout.drone_simple_spinner_dropdown_item);
            spPresets.setAdapter(adapter);
            isInitializingSpinner[0] = false;
        });

        spPresets.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                if (isInitializingSpinner[0]) return;
                MainViewModel.Preset p = (MainViewModel.Preset) a.getItemAtPosition(pos);
                if (p != null && p.frequencyHz > 0) {
                    etFreq.setText(String.format(Locale.US, "%.3f", p.frequencyHz / 1_000_000.0));
                    vm.setFrequency(p.frequencyHz, true);

                    // Auto-configure video decoder mode for CCTV / Analog video feeds
                    String name = p.name.toUpperCase();
                    if (name.contains("CCTV") || name.contains("CAM") || name.contains("ANALOG") || name.contains("FPV")) {
                        if (name.contains("PAL")) {
                            vm.setVideoMode(TSDRLibrary.VideoMode.PAL_CCTV);
                        } else {
                            vm.setVideoMode(TSDRLibrary.VideoMode.NTSC_CCTV);
                        }
                    }
                }
                btnDelete.setVisibility(p != null && p.isDeletable ? View.VISIBLE : View.GONE);
                btnInfo.setVisibility(p != null && p.frequencyHz > 0 ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> a) {}
        });

        btnInfo.setOnClickListener(v -> {
            MainViewModel.Preset p = (MainViewModel.Preset) spPresets.getSelectedItem();
            if (p != null) {
                new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(p.name)
                    .setMessage(getString(R.string.drone_preset_info_format,
                        p.frequencyHz / 1_000_000.0,
                        p.hardwareInfo,
                        p.antennaInfo,
                        p.vendorInfo,
                        p.components,
                        p.unencrypted ? getString(R.string.drone_signal_unencrypted) : getString(R.string.drone_signal_encrypted)))
                    .setPositiveButton(R.string.drone_ok, null)
                    .show();
            }
        });

        btnDelete.setOnClickListener(v -> {
            int pos = spPresets.getSelectedItemPosition();
            vm.deletePreset(pos);
        });

        btnAdd.setOnClickListener(v -> {
            String name = etNewName.getText().toString().trim();
            if (!name.isEmpty()) {
                vm.addPreset(name, vm.getFrequencyHz());
                etNewName.setText("");
            }
        });

        // ── Gain ───────────────────────────────────────────────────────

        // ── Gain ───────────────────────────────────────────────────────
        SeekBar sbGain   = view.findViewById(R.id.sb_gain);
        TextView tvGain  = view.findViewById(R.id.tv_gain_val);
        int gainInt = (int) vm.getGainDb();
        sbGain.setProgress(gainInt);
        tvGain.setText(gainInt + " dB");
        sbGain.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean u) {
                tvGain.setText(p + " dB");
                vm.setGain(p);
            }
        });

        // ── Gain offset ────────────────────────────────────────────────
        SeekBar sbOffset  = view.findViewById(R.id.sb_gain_offset);
        TextView tvOffset = view.findViewById(R.id.tv_gain_offset_val);
        int offsetInt = (int)((vm.getGainOffset() + 1.0) * 50); // map [-1,1] → [0,100]
        sbOffset.setProgress(offsetInt);
        tvOffset.setText(String.format("%.2f", vm.getGainOffset()));
        sbOffset.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean u) {
                double off = (p / 50.0) - 1.0;
                tvOffset.setText(String.format("%.2f", off));
                vm.setGainOffset(off);
            }
        });

        // ── Shift ──────────────────────────────────────────────────────
        SeekBar sbShift  = view.findViewById(R.id.sb_shift);
        TextView tvShift = view.findViewById(R.id.tv_shift_val);
        int shiftInt = (int)(vm.getShift() + 500);  // range [-500, 500]
        sbShift.setProgress(Math.max(0, Math.min(1000, shiftInt)));
        tvShift.setText(String.valueOf((int)vm.getShift()));
        sbShift.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean u) {
                int s = p - 500;
                tvShift.setText(String.valueOf(s));
                vm.setShift(s);
            }
        });

        // ── Sample rate ────────────────────────────────────────────────
        Spinner spRate = view.findViewById(R.id.sp_sample_rate);
        String[] rates = {"1.024 MS/s", "1.4 MS/s", "1.8 MS/s", "2.048 MS/s",
                          "2.4 MS/s",   "2.56 MS/s", "2.8 MS/s", "3.2 MS/s"};
        int[]    rateVals = {1024000, 1400000, 1800000, 2048000,
                             2400000, 2560000, 2800000, 3200000};
        ArrayAdapter<String> rateAdapt = new ArrayAdapter<>(requireContext(),
                R.layout.drone_simple_spinner_item, rates);
        rateAdapt.setDropDownViewResource(R.layout.drone_simple_spinner_dropdown_item);
        spRate.setAdapter(rateAdapt);
        for (int i = 0; i < rateVals.length; i++) {
            if (rateVals[i] == vm.getSampleRate()) { spRate.setSelection(i); break; }
        }
        spRate.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                vm.setSampleRate(rateVals[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> a) {}
        });

        // ── Video mode ─────────────────────────────────────────────────
        Spinner spVideo = view.findViewById(R.id.sp_video_mode);
        TSDRLibrary.VideoMode[] modes = TSDRLibrary.VideoMode.values();
        String[] modeLabels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) modeLabels[i] = modes[i].toString();
        ArrayAdapter<String> modeAdapt = new ArrayAdapter<>(requireContext(),
                R.layout.drone_simple_spinner_item, modeLabels);
        modeAdapt.setDropDownViewResource(R.layout.drone_simple_spinner_dropdown_item);
        spVideo.setAdapter(modeAdapt);
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].totalHeight == vm.getVideoHeight() &&
                    Math.abs(modes[i].refreshHz - vm.getVideoRefresh()) < 0.01) {
                spVideo.setSelection(i);
                break;
            }
        }
        spVideo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                TSDRLibrary.VideoMode m = modes[pos];
                if (m != TSDRLibrary.VideoMode.CUSTOM)
                    vm.setVideoMode(m);
            }
            @Override public void onNothingSelected(AdapterView<?> a) {}
        });

        // ── Custom height / refresh ────────────────────────────────────
        EditText etH = view.findViewById(R.id.et_custom_height);
        EditText etR = view.findViewById(R.id.et_custom_refresh);
        etH.setText(String.valueOf(vm.getVideoHeight()));
        etR.setText(String.valueOf((int) vm.getVideoRefresh()));
        Button btnApply = view.findViewById(R.id.btn_apply_custom);
        btnApply.setOnClickListener(v2 -> {
            try {
                int h    = Integer.parseInt(etH.getText().toString());
                double r = Double.parseDouble(etR.getText().toString());
                vm.setVideoResolution(h, r);
            } catch (NumberFormatException ignored) {}
        });

        // ── DSP checkboxes ─────────────────────────────────────────────
        CheckBox cbSweep    = view.findViewById(R.id.cb_sweep);
        CheckBox cbFullScan = view.findViewById(R.id.cb_full_scan);
        CheckBox cbStopOnSignal = view.findViewById(R.id.cb_stop_on_signal);
        CheckBox cbLowPass  = view.findViewById(R.id.cb_lowpass);
        CheckBox cbAutoGain = view.findViewById(R.id.cb_autogain);

        cbSweep.setChecked(vm.isSweepEnabled());
        cbSweep.setOnCheckedChangeListener((b, c) -> {
            vm.setSweepEnabled(c);
            if (c) cbFullScan.setChecked(false);
        });

        cbFullScan.setChecked(vm.isFullScanEnabled());
        cbFullScan.setOnCheckedChangeListener((b, c) -> {
            vm.setFullScanEnabled(c);
            if (c) cbSweep.setChecked(false);
        });

        CheckBox cbHarmonics = view.findViewById(R.id.cb_harmonics);
        cbHarmonics.setChecked(vm.isHarmonicsEnabled());
        cbHarmonics.setOnCheckedChangeListener((b, c) -> vm.setHarmonicsEnabled(c));

        cbStopOnSignal.setChecked(vm.isStopOnSignal());
        cbStopOnSignal.setOnCheckedChangeListener((b, c) -> vm.setStopOnSignal(c));

        cbLowPass.setChecked(vm.isLowPassEnabled());
        cbLowPass.setOnCheckedChangeListener((b, c) -> vm.setLowPassEnabled(c));

        cbAutoGain.setChecked(vm.isAutoGainEnabled());
        cbAutoGain.setOnCheckedChangeListener((b, c) -> vm.setAutoGainEnabled(c));


        // ── Threshold ──
        SeekBar sbThreshold = view.findViewById(R.id.sb_threshold);
        TextView tvThreshold = view.findViewById(R.id.tv_threshold_val);
        int threshInt = (int)(vm.getDetectionThreshold() * 10);
        sbThreshold.setProgress(threshInt);
        tvThreshold.setText(String.format(Locale.US, "%.1f x", vm.getDetectionThreshold()));
        sbThreshold.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean u) {
                float val = p / 10.0f;
                tvThreshold.setText(String.format(Locale.US, "%.1f x", val));
                vm.setDetectionThreshold(val);
            }
        });

        // ── Reset autocorr ─────────────────────────────────────────────
        Button btnReset = view.findViewById(R.id.btn_reset_autocorr);
        btnReset.setOnClickListener(v2 -> vm.resetAutocorr());

        // ── External Link ─────────────────────────────────────────────
        TextView tvLink = view.findViewById(R.id.tv_link);
        tvLink.setOnClickListener(v2 -> {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://dev.mostlyawesome.de"));
            startActivity(intent);
        });
    }

    private void adjustFreq(EditText et, double delta) {
        try {
            String val = et.getText().toString().replace(',', '.');
            double mhz = Double.parseDouble(val);
            mhz += delta;
            et.setText(String.format(Locale.US, "%.3f", mhz));
            vm.setFrequency(Math.round(mhz * 1_000_000.0), true);
        } catch (NumberFormatException ignored) {}
    }

    private void applyFreqFromEditText(EditText et) {
        try {
            String val = et.getText().toString().replace(',', '.');
            double mhz = Double.parseDouble(val);
            long hz = Math.round(mhz * 1_000_000.0);
            if (hz >= 24_000_000L && hz <= 6_000_000_000L) {
                vm.setFrequency(hz, true);
            }
        } catch (NumberFormatException ignored) {}
    }

    // ── Simple helper stubs ───────────────────────────────────────────
    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
        @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar b) {}
        @Override public void onStopTrackingTouch(SeekBar b) {}
    }
}
