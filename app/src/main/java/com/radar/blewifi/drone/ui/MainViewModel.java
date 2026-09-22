package com.radar.blewifi.drone.ui;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.radar.blewifi.R;
import com.radar.blewifi.drone.core.TSDRLibrary;
import com.radar.blewifi.drone.dsp.FrameRenderer;
import com.radar.blewifi.drone.rtlsdr.RtlSdrManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Survives rotation; owns TSDRLibrary and RtlSdrManager lifecycle.
 */
public class MainViewModel extends AndroidViewModel {

    private static final String TAG = "MainViewModel";

    // ── LiveData observed by MainActivity ─────────────────────────────
    public final MutableLiveData<Bitmap>  frameLive   = new MutableLiveData<>();
    public final MutableLiveData<Long>    frequencyLive = new MutableLiveData<>(620_000_000L);
    public final MutableLiveData<String>  statusLive  = new MutableLiveData<>();
    public final MutableLiveData<Boolean> runningLive = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> sweepLive   = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> droneScanLive = new MutableLiveData<>(false);
    public final MutableLiveData<Integer> scanProgressLive = new MutableLiveData<>(0);
    public final MutableLiveData<Integer> scanTotalLive = new MutableLiveData<>(0);
    public final MutableLiveData<float[]> autocorrLive= new MutableLiveData<>();
    public final MutableLiveData<Float>   signalLive  = new MutableLiveData<>(0f);
    public final MutableLiveData<Boolean> turboLockLive = new MutableLiveData<>(false);
    public final MutableLiveData<String>  errorLive   = new MutableLiveData<>();
    public final MutableLiveData<Boolean> fullscreenLive = new MutableLiveData<>(false);
    public final MutableLiveData<Float>   thresholdLive = new MutableLiveData<>(7.5f);

    // ── SDR / DSP objects ─────────────────────────────────────────────
    private final TSDRLibrary   tsdr;
    private final RtlSdrManager rtlMgr;
    private final MutableLiveData<Integer> primaryColorLive = new MutableLiveData<>(0xFF00FF00);
    private final MutableLiveData<Integer> accentColorLive  = new MutableLiveData<>(0xFF00FF00);
    private final MutableLiveData<Integer> bgColorLive      = new MutableLiveData<>(0xFF000000);

    public LiveData<Integer> primaryColorLive() { return primaryColorLive; }
    public LiveData<Integer> accentColorLive()  { return accentColorLive; }
    public LiveData<Integer> bgColorLive()      { return bgColorLive; }

    public void setThemeColors(int primary, int accent, int bg) {
        primaryColorLive.postValue(primary);
        accentColorLive.postValue(accent);
        bgColorLive.postValue(bg);
    }

    private final FrameRenderer renderer = new FrameRenderer();

    // ── Current settings (survive rotation) ───────────────────────────
    private long   frequencyHz  = 620_000_000L;  // 620 MHz default
    private double gainDb       = 30.0;
    private int    sampleRate   = 2_500_000;
    private int    videoHeight  = 628;
    private double videoRefresh = 60.0;
    private double gainOffset   = 0.0;
    private double shift        = 0.0;
    private boolean sweepEnabled = false;
    private boolean fullScanEnabled = false;
    private boolean droneScanEnabled = false;
    private boolean harmonicsEnabled = false;
    private boolean stopOnSignal    = true;
    private boolean lowPassEnabled  = false;
    private boolean autoGainEnabled = false;
    private long sweepStartHz    = 0;
    private long sweepEndHz      = 0;
    private long sweepStepHz     = 2_000_000;
    private long lastSweepUpdate = 0;
    private int  dronePresetIndex = 0;
    private int  framesSinceFreqChange = 0;
    private final List<Long> droneFrequencies = new ArrayList<>();

    private float detectionThreshold = 7.5f;
    private int   consecutiveDetections = 0;
    private int   turboLockCounter = 0;
    private static final int MIN_CONSECUTIVE = 3;
    private static final int TURBO_LOCK_GRACE = 5;

    private static final String PREFS_NAME = "tsdr_presets";
    private static final String PREF_CUSTOM_PRESETS = "custom_presets";
    private static final String PREF_FREQ = "frequency_hz";
    private static final String PREF_GAIN = "gain_db";
    private static final String PREF_SAMPLE_RATE = "sample_rate";
    private static final String PREF_VIDEO_HEIGHT = "video_height";
    private static final String PREF_VIDEO_REFRESH = "video_refresh";
    private static final String PREF_GAIN_OFFSET = "gain_offset";
    private static final String PREF_SHIFT = "shift";
    private static final String PREF_HARMONICS = "harmonics_enabled";
    private static final String PREF_STOP_ON_SIGNAL = "stop_on_signal";
    private static final String PREF_THRESHOLD = "detection_threshold";
    private static final String PREF_LOWPASS = "lowpass_enabled";
    private static final String PREF_AUTOGAIN = "autogain_enabled";

    public static class Preset {
        public String name;
        public long frequencyHz;
        public boolean isDeletable;
        public String hardwareInfo;
        public String antennaInfo;
        public String vendorInfo;
        public String components;
        public boolean unencrypted;

        public Preset(String name, long frequencyHz, boolean isDeletable) {
            this(name, frequencyHz, isDeletable, "RTL-SDR / HackRF / SDR++", "Standard 50 Ohm Omni or Directed Patch", "Varies", "Unknown/Commercial", true);
        }

        public Preset(String name, long frequencyHz, boolean isDeletable, String hw, String ant, String vendor, String components, boolean unencrypted) {
            this.name = name;
            this.frequencyHz = frequencyHz;
            this.isDeletable = isDeletable;
            this.hardwareInfo = hw;
            this.antennaInfo = ant;
            this.vendorInfo = vendor;
            this.components = components;
            this.unencrypted = unencrypted;
        }

        @Override @NonNull public String toString() { return name; }
    }

    public final MutableLiveData<List<Preset>> presetsLive = new MutableLiveData<>();

    public MainViewModel(@NonNull Application app) {
        super(app);
        statusLive.setValue(app.getString(R.string.drone_status_ready));
        rtlMgr = new RtlSdrManager(app);
        rtlMgr.registerReceiver();
        tsdr   = new TSDRLibrary();
        setupCallbacks();
        loadPresets();
    }

    private void loadPresets() {
        List<Preset> list = new ArrayList<>();
        list.add(new Preset(getApplication().getString(R.string.drone_preset_placeholder), 0, false));
        // --- RUSSIAN DRONES ---
        list.add(new Preset("RUS: Orlan-10 (Analog Video 915MHz)", 915_000_000L, false, 
            "RTL-SDR / HackRF / Airspy", "Kometa-M-VT CRPA / 915MHz Yagi", "STC (Special Technology Center)", 
            "U-blox NEO-M6N, ST STM32F103 MCU, Qorvo SPF5122Z LNA / RF3194 PA, TDK MPU-6000, Semtech SX12BC, NXP MPX4115, Saito FG-40 Engine", true));
        
        list.add(new Preset("RUS: Shahed-136 / Geran-2 (Video 1.2GHz)", 1200_000_000L, false,
            "RTL-SDR / HackRF", "ЬІ-8465 CRPA Antenna / 1.2GHz RHCP Patch", "Shahed Aviation / Russia", 
            "Altera Cyclone FPGA, TI TMS320 F28335 DSP, STM32H743 MCU, GigaDevice GD25Q128, ADXL357B Accel, Microchip KSZ8041, U-blox GNSS, Maxim RS-485", true));

        list.add(new Preset("RUS: Geran-4 / Gerbera (Video 1.4GHz)", 1400_000_000L, false,
            "HackRF / SDR++", "1.4GHz Directional", "Russia", 
            "STM32F407 MCU, ATMLH417 Memory, NEO-M8N GNSS, HMC5883L Compass, UC9810 GNSS, LM5176, Telefly LX-WP-160 Jet", true));

        list.add(new Preset("RUS: Orlan-10 (Analog Video 1.02GHz)", 1020_000_000L, false,
            "RTL-SDR / HackRF / Airspy", "Kometa-M-VT CRPA / 1.0-1.1GHz Patch", "STC (Special Technology Center)", 
            "U-blox NEO-M6N, ST STM32F103, Qorvo RF path, Canon EOS Camera", true));

        list.add(new Preset("RUS: Orlan-10 (Analog Video 1.5GHz)", 1500_000_000L, false,
            "HackRF / LimeSDR", "1.5GHz RHCP Helix", "STC (Special Technology Center)", 
            "High-band variant: Analog Devices & TI components", true));

        list.add(new Preset("RUS: Lancet-3 (Video 2.31GHz)", 2310_000_000L, false, 
            "HackRF / LimeSDR / BladeRF", "2.3-2.4GHz High-Gain Grid Antenna", "ZALA Aero Group", 
            "NVIDIA Jetson TX2 AI, NXP i.MX/LPC2368, Xilinx Artix-7 FPGA, ST STM32L433, AD ADUM, U-blox LEA-M8S, Analog Devices AD7606 ADC", true));
        list.add(new Preset("RUS: Lancet-3 (Video 2.34GHz)", 2340_000_000L, false,
            "HackRF / LimeSDR / BladeRF", "2.3-2.4GHz High-Gain Grid Antenna", "ZALA Aero Group", 
            "NVIDIA Jetson TX2, NXP i.MX, Xilinx Artix-7, TI ADC/DAC, Skyworks RF switches", true));
        list.add(new Preset("RUS: Lancet-3 (Video 2.37GHz)", 2370_000_000L, false,
            "HackRF / LimeSDR / BladeRF", "2.3-2.4GHz High-Gain Grid Antenna", "ZALA Aero Group", 
            "NVIDIA Jetson TX2, NXP i.MX, Xilinx Artix-7, TI ADC/DAC, Vicor Power Modules", true));
        list.add(new Preset("RUS: Zala (Analog Video 1.11GHz)", 1115_000_000L, false,
            "RTL-SDR / HackRF", "1.1-1.2GHz RHCP Cloverleaf", "ZALA Aero Group", 
            "STM32F103 MCU, UM5010 Power Module, U-blox GNSS, TI Power Management, Sony Visuals, HMC5883L Magnetometer", true));
        list.add(new Preset("RUS: Zala (Analog Video 1.15GHz)", 1150_000_000L, false,
            "RTL-SDR / HackRF", "1.1-1.2GHz RHCP Cloverleaf", "ZALA Aero Group", 
            "U-blox GNSS, STMicroelectronics MCU, TI TPS series Power Management, Sony Visuals", true));
        list.add(new Preset("RUS: Zala (Analog Video 1.2GHz)", 1200_000_000L, false,
            "RTL-SDR / HackRF", "1.1-1.2GHz RHCP Cloverleaf", "ZALA Aero Group", 
            "U-blox GNSS, STMicroelectronics MCU, TI Power Management, Sony Visuals", true));
        list.add(new Preset("RUS: Eleron-3SV (Video 925MHz)", 925_000_000L, false,
            "RTL-SDR / Airspy", "925MHz Omni / Dipole", "ENICS", 
            "U-blox GPS, ST MCU, Generic Analog VTX modules, Hitec Servos", true));
        list.add(new Preset("RUS: Supercam (Video 1.05GHz)", 1050_000_000L, false,
            "RTL-SDR / HackRF", "1.0-1.1GHz Patch", "Unmanned Systems Group", 
            "Tiger Motor Engine, MDIN-i550 ISP, LPC4337/STM32 MCU, RM3100 Magnetometer, U-blox NEO-M8, Sony/Bridge sensors, RF Design 900+ Radio", true));
        list.add(new Preset("RUS: Supercam (Video 2.4GHz)", 2400_000_000L, false,
            "HackRF / SDR++", "2.4GHz RHCP Triple Feed Patch", "Unmanned Systems Group", 
            "Tiger Motor, MDIN-i550 ISP, LPC4337/STM32 MCU, RM3100 Magnetometer, U-blox NEO-M8, Sony/Bridge sensors", true));
        list.add(new Preset("RUS: Mohajer-6 (Video 1.45GHz)", 1450_000_000L, false,
            "RTL-SDR / HackRF", "1.4-1.5GHz High Gain Helix", "Qods Aviation / Iran", 
            "ATMEGA2560/ATMEGA128A MCU, Exar XR16V798 UART, Analog Devices MAX3378E Translator, TI TLC2741 Op-Amp, ST 78M05/78M06 Regulators, Vishay 40CPQ045 Diode, IRF4905 MOSFET, Rotax 912 Engine, GMSL Camera Link", true));
        list.add(new Preset("RUS: Kartograf / Ptero-G0 (Video 1.15GHz)", 1150_000_000L, false,
            "RTL-SDR / HackRF", "1.1-1.2GHz RHCP Patch", "AFM-Servers / Russia", 
            "U-blox NEO-M8 GNSS, STM32F4/F1 MCU, Proprietary 80MP Camera, Advanced Western avionics, ЬІ-8465 CRPA (variant)", true));
        list.add(new Preset("RUS: Kartograf / Ptero-G0 (Video 1.25GHz)", 1250_000_000L, false,
            "RTL-SDR / HackRF", "1.2GHz RHCP Patch", "AFM-Servers / Russia", 
            "U-blox NEO-M8, STM32F4/F1 MCU, Proprietary 80MP Camera System", true));
        list.add(new Preset("RUS: Forpost (Video 1.41GHz)", 1410_000_000L, false,
            "RTL-SDR / HackRF", "1.4GHz RHCP Patch", "UZGA", 
            "Xilinx Spartan-6 FPGA, TI/AD transceivers, Advanced Israeli-derived avionics", true));
        list.add(new Preset("RUS: Orion (Video 2.4GHz)", 2400_000_000L, false,
            "HackRF", "2.4GHz Tracking Antenna / Grid", "Kronshtadt Group", 
            "Military grade Xilinx/Intel FPGAs, TI/AD transceivers, Rotax 914 engine", true));
        list.add(new Preset("RUS: Zala T-20 (Digital Video 2.4GHz)", 2415_000_000L, false,
            "HackRF / BladeRF", "2.4GHz Grid", "ZALA Aero Group", 
            "Xilinx Spartan-6, NXP LPC2368 MCU, ADXRS453/646 Gyros, ST LIS3LV02 IMU, TI VP232 CAN, Microchip KSZ8081 Ethernet", true));
        list.add(new Preset("RUS: Scalpel Loitering Munition", 910_000_000L, false,
            "RTL-SDR", "900MHz Yagi", "Russia", 
            "Atmel Tiny25, ST 78M05, ON Semi Optocouplers, IR/Infineon MOSFETs", true));
        list.add(new Preset("RUS: Shahed-107 (ISR/Strike)", 1250_000_000L, false,
            "HackRF", "1.2GHz Patch", "Shahed Aviation", 
            "TI TMS320 DSP, TI VP230 CAN, AD8422 Amp, Fairchild H11G1, OMRON Relays, DLE 111 Engine", true));
        list.add(new Preset("RUS: Geran-3 UAV (C2/Video)", 2440_000_000L, false,
            "HackRF", "2.4GHz Omni", "Russia", 
            "Altera Cyclone, TI TMS320 DSP, TI VP230/LM5117, Infineon MOSFETs, Unicore UC9810 GNSS", false));
        list.add(new Preset("RUS: Corsair UAV (ISR)", 2350_000_000L, false,
            "HackRF / LimeSDR", "2.3GHz Parabolic", "Luch Design Bureau", 
            "Altera Cyclone V, Xilinx Spartan-6, NXP 74HC, Micron DDR3, SK Hynix NAND, AD LTC2296 ADC", true));
        list.add(new Preset("RUS: Knyaz Vandal (Fiber-optic UAV)", 0L, false,
            "None (Wired)", "Fiber Optic Spool", "Russia", 
            "Non-RF control, immune to EW. Uses standard COTS camera/processing hardware.", false));
        list.add(new Preset("RUS: FPV Video (720MHz)", 720_000_000L, false,
            "RTL-SDR v3/v4", "700-800MHz Linear Whip", "Generic FPV / DIY", "Commercial grade components", true));
        list.add(new Preset("RUS: FPV Video (900MHz)", 900_000_000L, false,
            "RTL-SDR v3/v4", "900MHz Omni / Moxon", "Generic FPV / DIY", "Commercial grade components", true));

        // --- UKRAINIAN DRONES ---
        list.add(new Preset("UKR: Shark (Video 2.45GHz)", 2450_000_000L, false,
            "HackRF / SDR++ / Airspy", "2.4GHz RHCP Triple Feed Patch", "Ukrspecsystems", 
            "Sony Camera, Commercial datalink", true));
        list.add(new Preset("UKR: Leleka-100 (Video 2.3GHz)", 2300_000_000L, false,
            "HackRF / LimeSDR", "2.3GHz High Gain Directional", "DeViro", 
            "Commercial Grade electronics", true));
        list.add(new Preset("UKR: Leleka-100 (Video 2.35GHz)", 2350_000_000L, false,
            "HackRF / LimeSDR", "2.3GHz High Gain Directional", "DeViro", 
            "Commercial Grade electronics", true));
        list.add(new Preset("UKR: PD-2 (Video 1.4GHz)", 1400_000_000L, false,
            "RTL-SDR / HackRF", "1.4GHz RHCP Patch", "Ukrspecsystems", 
            "Commercial Grade electronics", true));
        list.add(new Preset("UKR: Fury (Video 5.8GHz)", 5800_000_000L, false,
            "HackRF w/ Downconverter", "5.8GHz Helical / Patch", "Athlon Avia", 
            "Commercial Grade electronics", true));
        list.add(new Preset("UKR: Valkyrie (Video 2.41GHz)", 2412_000_000L, false,
            "HackRF", "2.4GHz RHCP Triple Feed Patch", "Warbirds of Ukraine", 
            "Commercial Grade electronics", true));
        list.add(new Preset("UKR: Spectator-M1 (Video 2.48GHz)", 2480_000_000L, false,
            "HackRF", "2.4GHz Directional", "Polytechnic / Merit", 
            "Commercial Grade electronics", true));
        list.add(new Preset("UKR: Punisher (Video 2.43GHz)", 2430_000_000L, false,
            "HackRF", "2.4GHz RHCP Patch", "UA Dynamics", 
            "Commercial Grade electronics", true));
        list.add(new Preset("UKR: Mara (Video 2.46GHz)", 2460_000_000L, false,
            "HackRF", "2.4GHz Directional", "Mara Drone Team", 
            "Commercial Grade electronics", true));
        list.add(new Preset("UKR: Bober (Control 433MHz)", 433_920_000L, false,
            "RTL-SDR", "433MHz Moxon / Yagi", "Generic / DIY", 
            "Commercial Grade electronics", false));
        list.add(new Preset("UKR: Vector (Analog Video 2.44GHz)", 2440_000_000L, false,
            "HackRF / SDR++", "2.4GHz RHCP Patch", "Quantum-Systems", 
            "Advanced Western components", true));
        list.add(new Preset("UKR: FPV Video (770MHz)", 770_000_000L, false,
            "RTL-SDR v3/v4", "700-800MHz Linear Whip", "Generic FPV / DIY", 
            "Ready-made FPV modules", true));
        list.add(new Preset("UKR: FPV Video (930MHz)", 930_000_000L, false,
            "RTL-SDR v3/v4", "900-950MHz Omni / Moxon", "Generic FPV / DIY", 
            "Ready-made FPV modules", true));

        // --- FPV BANDS 1.2GHz ---
        list.add(new Preset("FPV 1.2G: Ch 4 (1120MHz)", 1120_000_000L, false,
            "RTL-SDR / HackRF", "1.2GHz RHCP Cloverleaf", "Generic / Foxeer / Matek", 
            "Foxeer/Matek VTX", true));
        list.add(new Preset("FPV 1.2G: Ch 6 (1200MHz)", 1200_000_000L, false,
            "RTL-SDR / HackRF", "1.2GHz RHCP Cloverleaf", "Generic / Foxeer / Matek", 
            "Foxeer/Matek VTX", true));
        list.add(new Preset("FPV 1.2G: Ch 8 (1280MHz)", 1280_000_000L, false,
            "RTL-SDR / HackRF", "1.2GHz RHCP Patch", "Generic / Foxeer / Matek", 
            "Foxeer/Matek VTX", true));

        // --- FPV BANDS 5.8GHz ---
        list.add(new Preset("FPV 5.8G: R1 (5658MHz)", 5658_000_000L, false,
            "HackRF w/ Downconverter", "5.8GHz RHCP Patch / Helical", "TBS / ImmersionRC / Foxeer", 
            "TBS/ImmersionRC VTX", true));
        list.add(new Preset("FPV 5.8G: R8 (5917MHz)", 5917_000_000L, false,
            "HackRF w/ Downconverter", "5.8GHz RHCP Patch / Helical", "TBS / ImmersionRC / Foxeer", 
            "TBS/ImmersionRC VTX", true));

        // --- OTHER WAR DRONES / ISR ---
        list.add(new Preset("US: ScanEagle (Analog Video 2.42GHz)", 2420_000_000L, false,
            "HackRF / Airspy", "2.4GHz High Gain Dish / Grid", "Insitu / Boeing", 
            "Military Grade avionics", true));
        list.add(new Preset("US: RQ-11B Raven (Analog Video 2.39GHz)", 2390_000_000L, false,
            "HackRF", "2.3-2.4GHz Omni Whip", "AeroVironment", 
            "Military Grade avionics", true));
        list.add(new Preset("NATO: Black Hornet (Video 2.405GHz)", 2405_000_000L, false,
            "HackRF", "2.4GHz Omni Whip", "Teledyne FLIR", 
            "Highly miniaturized proprietary electronics", true));
        list.add(new Preset("ISR: Aerostar (Video 1.42GHz)", 1420_000_000L, false,
            "RTL-SDR / HackRF", "1.4GHz RHCP Patch", "Aeronautics Defense Systems", 
            "Advanced Israeli avionics", true));
        list.add(new Preset("ISR: Hermes 450 (Video 1.35GHz)", 1350_000_000L, false,
            "RTL-SDR / HackRF", "1.3-1.4GHz RHCP Patch", "Elbit Systems", 
            "Advanced Israeli avionics", true));
        list.add(new Preset("ISR: Searcher Mk II (Video 1.4GHz)", 1410_000_000L, false,
            "RTL-SDR / HackRF", "1.4GHz RHCP Patch", "IAI (Israel Aerospace Industries)", 
            "Advanced Israeli avionics", true));
        list.add(new Preset("ISR: Bayraktar TB2 (Control 433MHz Backup)", 433_000_000L, false,
            "RTL-SDR", "433MHz Yagi", "Baykar Tech", 
            "Turkish and Western components", false));

        // --- CCTV / WIRELESS SECURITY CAMERAS ---
        // 0.9 GHz Band
        list.add(new Preset("CCTV 900M: Ch 1 (910MHz NTSC/PAL)", 910_000_000L, false,
            "RTL-SDR v3/v4", "900MHz Directional Patch / Yagi", "Generic Wireless CCTV", "0.9GHz Analog Cam Transceiver", true));
        list.add(new Preset("CCTV 900M: Ch 2 (980MHz NTSC/PAL)", 980_000_000L, false,
            "RTL-SDR v3/v4", "900MHz Directional Patch / Yagi", "Generic Wireless CCTV", "0.9GHz Analog Cam Transceiver", true));
        list.add(new Preset("CCTV 900M: Ch 3 (1010MHz NTSC/PAL)", 1010_000_000L, false,
            "RTL-SDR v3/v4", "1.0GHz Patch", "Generic Wireless CCTV", "0.9GHz Analog Cam Transceiver", true));
        list.add(new Preset("CCTV 900M: Ch 4 (1040MHz NTSC/PAL)", 1040_000_000L, false,
            "RTL-SDR v3/v4", "1.0GHz Patch", "Generic Wireless CCTV", "0.9GHz Analog Cam Transceiver", true));

        // 1.2 GHz / 1.3 GHz Band
        list.add(new Preset("CCTV 1.2G: Ch 1 (1080MHz NTSC/PAL)", 1080_000_000L, false,
            "RTL-SDR / HackRF", "1.2GHz Dipole / Cloverleaf", "LawMate / Foxeer", "1.2GHz Wireless Security Cam", true));
        list.add(new Preset("CCTV 1.2G: Ch 2 (1120MHz NTSC/PAL)", 1120_000_000L, false,
            "RTL-SDR / HackRF", "1.2GHz Dipole / Cloverleaf", "LawMate / Foxeer", "1.2GHz Wireless Security Cam", true));
        list.add(new Preset("CCTV 1.2G: Ch 3 (1160MHz NTSC/PAL)", 1160_000_000L, false,
            "RTL-SDR / HackRF", "1.2GHz Dipole / Cloverleaf", "LawMate / Foxeer", "1.2GHz Wireless Security Cam", true));
        list.add(new Preset("CCTV 1.2G: Ch 4 (1200MHz NTSC/PAL)", 1200_000_000L, false,
            "RTL-SDR / HackRF", "1.2GHz Dipole / Cloverleaf", "LawMate / Foxeer", "1.2GHz Wireless Security Cam", true));
        list.add(new Preset("CCTV 1.2G: Ch 5 (1240MHz NTSC/PAL)", 1240_000_000L, false,
            "RTL-SDR / HackRF", "1.2GHz Dipole / Cloverleaf", "LawMate / Foxeer", "1.2GHz Wireless Security Cam", true));
        list.add(new Preset("CCTV 1.2G: Ch 6 (1280MHz NTSC/PAL)", 1280_000_000L, false,
            "RTL-SDR / HackRF", "1.2GHz Dipole / Cloverleaf", "LawMate / Foxeer", "1.2GHz Wireless Security Cam", true));
        list.add(new Preset("CCTV 1.2G: Ch 7 (1320MHz NTSC/PAL)", 1320_000_000L, false,
            "RTL-SDR / HackRF", "1.2GHz Dipole / Cloverleaf", "LawMate / Foxeer", "1.2GHz Wireless Security Cam", true));
        list.add(new Preset("CCTV 1.2G: Ch 8 (1360MHz NTSC/PAL)", 1360_000_000L, false,
            "RTL-SDR / HackRF", "1.2GHz Dipole / Cloverleaf", "LawMate / Foxeer", "1.2GHz Wireless Security Cam", true));

        // 2.4 GHz Band
        list.add(new Preset("CCTV 2.4G: Ch 1 (2414MHz NTSC/PAL)", 2414_000_000L, false,
            "HackRF / Airspy", "2.4GHz Directional Patch / Grid", "Generic OEM / Night Owl", "2.4GHz Wireless Security Cam", true));
        list.add(new Preset("CCTV 2.4G: Ch 2 (2432MHz NTSC/PAL)", 2432_000_000L, false,
            "HackRF / Airspy", "2.4GHz Directional Patch / Grid", "Generic OEM / Night Owl", "2.4GHz Wireless Security Cam", true));
        list.add(new Preset("CCTV 2.4G: Ch 3 (2450MHz NTSC/PAL)", 2450_000_000L, false,
            "HackRF / Airspy", "2.4GHz Directional Patch / Grid", "Generic OEM / Night Owl", "2.4GHz Wireless Security Cam", true));
        list.add(new Preset("CCTV 2.4G: Ch 4 (2468MHz NTSC/PAL)", 2468_000_000L, false,
            "HackRF / Airspy", "2.4GHz Directional Patch / Grid", "Generic OEM / Night Owl", "2.4GHz Wireless Security Cam", true));
        list.add(new Preset("CCTV 2.4G: Ch 5 (2370MHz NTSC/PAL)", 2370_000_000L, false,
            "HackRF / Airspy", "2.4GHz Directional Patch / Grid", "Generic OEM / Swann", "2.4GHz Wireless Security Cam", true));
        list.add(new Preset("CCTV 2.4G: Ch 6 (2390MHz NTSC/PAL)", 2390_000_000L, false,
            "HackRF / Airspy", "2.4GHz Directional Patch / Grid", "Generic OEM / Swann", "2.4GHz Wireless Security Cam", true));

        // 5.8 GHz Band
        list.add(new Preset("CCTV 5.8G: Band A Ch 1 (5705MHz)", 5705_000_000L, false,
            "HackRF w/ Downconverter", "5.8GHz Helical / Grid", "Boscam / Foxeer / Skyzone", "5.8GHz Wireless CCTV Transmitter", true));
        list.add(new Preset("CCTV 5.8G: Band A Ch 3 (5745MHz)", 5745_000_000L, false,
            "HackRF w/ Downconverter", "5.8GHz Helical / Grid", "Boscam / Foxeer / Skyzone", "5.8GHz Wireless CCTV Transmitter", true));
        list.add(new Preset("CCTV 5.8G: Band A Ch 5 (5785MHz)", 5785_000_000L, false,
            "HackRF w/ Downconverter", "5.8GHz Helical / Grid", "Boscam / Foxeer / Skyzone", "5.8GHz Wireless CCTV Transmitter", true));
        list.add(new Preset("CCTV 5.8G: Band A Ch 7 (5825MHz)", 5825_000_000L, false,
            "HackRF w/ Downconverter", "5.8GHz Helical / Grid", "Boscam / Foxeer / Skyzone", "5.8GHz Wireless CCTV Transmitter", true));

        // UHF Security / TV Band
        list.add(new Preset("CCTV UHF: Ch 14 (471.25MHz Analog)", 471_250_000L, false,
            "RTL-SDR", "UHF Dipole / Yagi", "Analog Modulator", "Analog Security Camera TV Modulator", true));
        list.add(new Preset("CCTV UHF: Ch 20 (507.25MHz Analog)", 507_250_000L, false,
            "RTL-SDR", "UHF Dipole / Yagi", "Analog Modulator", "Analog Security Camera TV Modulator", true));
        list.add(new Preset("CCTV UHF: Ch 32 (579.25MHz Analog)", 579_250_000L, false,
            "RTL-SDR", "UHF Dipole / Yagi", "Analog Modulator", "Analog Security Camera TV Modulator", true));
        list.add(new Preset("CCTV UHF: Ch 38 (615.25MHz Analog)", 615_250_000L, false,
            "RTL-SDR", "UHF Dipole / Yagi", "Analog Modulator", "Analog Security Camera TV Modulator", true));
        list.add(new Preset("TEMPEST: Monitor Leakage (400MHz)", 400_000_000L, false,
            "RTL-SDR", "400MHz Wideband Dipole", "N/A (TEMPEST)", "Stray Video RF Emissions from Displays", true));

        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        this.frequencyHz = prefs.getLong(PREF_FREQ, 620_000_000L);
        this.frequencyLive.setValue(this.frequencyHz);
        this.gainDb = prefs.getFloat(PREF_GAIN, 30.0f);
        this.sampleRate = prefs.getInt(PREF_SAMPLE_RATE, 2_500_000);
        this.videoHeight = prefs.getInt(PREF_VIDEO_HEIGHT, 628);
        this.videoRefresh = prefs.getFloat(PREF_VIDEO_REFRESH, 60.0f);
        this.gainOffset = prefs.getFloat(PREF_GAIN_OFFSET, 0.0f);
        this.shift = prefs.getFloat(PREF_SHIFT, 0.0f);
        this.harmonicsEnabled = prefs.getBoolean(PREF_HARMONICS, false);
        this.stopOnSignal = prefs.getBoolean(PREF_STOP_ON_SIGNAL, true);
        this.detectionThreshold = prefs.getFloat(PREF_THRESHOLD, 7.5f);
        this.thresholdLive.setValue(this.detectionThreshold);
        this.lowPassEnabled = prefs.getBoolean(PREF_LOWPASS, false);
        this.autoGainEnabled = prefs.getBoolean(PREF_AUTOGAIN, false);

        String custom = prefs.getString(PREF_CUSTOM_PRESETS, "");
        if (!custom.isEmpty()) {
            for (String p : custom.split("\\|")) {
                String[] parts = p.split(":");
                if (parts.length == 2) {
                    try {
                        list.add(new Preset(parts[0], Long.parseLong(parts[1]), true));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        presetsLive.setValue(list);
    }

    public Preset getCurrentPreset() {
        List<Preset> list = presetsLive.getValue();
        if (list == null) return null;
        long currentFreq = getFrequencyHz();
        for (Preset p : list) {
            if (p.frequencyHz > 0 && Math.abs(p.frequencyHz - currentFreq) < 5000) {
                return p;
            }
        }
        return null;
    }

    public void addPreset(String name, long freqHz) {
        List<Preset> list = presetsLive.getValue();
        if (list == null) return;
        list.add(new Preset(name, freqHz, true, "User Defined", "User Defined", "User Defined", "Unknown", true));
        saveCustomPresets(list);
        presetsLive.setValue(list);
    }

    public void deletePreset(int index) {
        List<Preset> list = presetsLive.getValue();
        if (list == null || index < 0 || index >= list.size()) return;
        if (list.get(index).isDeletable) {
            list.remove(index);
            saveCustomPresets(list);
            presetsLive.setValue(list);
        }
    }

    private void saveCustomPresets(List<Preset> list) {
        StringBuilder sb = new StringBuilder();
        for (Preset p : list) {
            if (p.isDeletable) {
                if (sb.length() > 0) sb.append("|");
                sb.append(p.name).append(":").append(p.frequencyHz);
            }
        }
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(PREF_CUSTOM_PRESETS, sb.toString()).apply();
    }

    private void setupCallbacks() {
        tsdr.setFrameReadyCallback((pixels, w, h) -> {
            framesSinceFreqChange++;
            Integer primary = primaryColorLive.getValue();
            Integer bg = bgColorLive.getValue();
            Bitmap bmp = renderer.render(pixels, w, h, primary != null ? primary : 0xFF00FF00, bg != null ? bg : 0xFF000000);
            frameLive.postValue(bmp);

        // Handle Band Sweep / Full Scan / Drone Scan
        if (sweepEnabled || fullScanEnabled || droneScanEnabled) {
            boolean signalDetected = false;

            // Only check for signal after tuner and DSP buffers settle (minimum 3 frames after freq change)
            if (framesSinceFreqChange >= 3) {
                float[] ac = tsdr.getAutocorr();
                if (ac != null && ac.length > 10) {
                    float max = 0;
                    float sum = 0;
                    for (int i = 5; i < ac.length; i++) { // Skip low lag noise
                        if (ac[i] > max) max = ac[i];
                        sum += ac[i];
                    }
                    float avg = sum / ac.length;

                    // Ensure valid signal energy level (not transient noise or zeros)
                    if (avg > 0.001f) {
                        int requiredConsecutive = droneScanEnabled ? 2 : MIN_CONSECUTIVE;
                        boolean isTurbo = (max > avg * detectionThreshold * 3.0f);

                        if (isTurbo) {
                            turboLockCounter = TURBO_LOCK_GRACE;
                            turboLockLive.postValue(true);
                            signalDetected = true; // Turbo lock: Extreme signal
                        } else if (max > avg * detectionThreshold) {
                            consecutiveDetections++;
                            if (turboLockCounter > 0) turboLockCounter--;
                        } else {
                            consecutiveDetections = 0;
                            if (turboLockCounter > 0) {
                                turboLockCounter--;
                            } else {
                                turboLockLive.postValue(false);
                            }
                        }

                        if (consecutiveDetections >= requiredConsecutive && stopOnSignal) {
                            signalDetected = true;
                        }
                    }
                } else {
                    if (turboLockCounter > 0) {
                        turboLockCounter--;
                    } else {
                        turboLockLive.postValue(false);
                    }
                }
            }

            if (signalDetected) {
                sweepEnabled = false;
                sweepLive.postValue(false);
                fullScanEnabled = false;
                droneScanEnabled = false;
                droneScanLive.postValue(false);
                consecutiveDetections = 0;
                updateStatusWithFreq();
                getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putLong(PREF_FREQ, frequencyHz).apply();
            } else {
                long now = System.currentTimeMillis();
                long stepDelay = droneScanEnabled ? 200 : 500; // 200ms per freq for stability

                if (now - lastSweepUpdate > stepDelay) {
                    lastSweepUpdate = now;
                    if (droneScanEnabled && !droneFrequencies.isEmpty()) {
                        dronePresetIndex = (dronePresetIndex + 1) % droneFrequencies.size();
                        setFrequency(droneFrequencies.get(dronePresetIndex), false);
                        scanProgressLive.postValue(dronePresetIndex + 1);
                    } else {
                        long nextFreq = frequencyHz + sweepStepHz;
                        if (nextFreq > sweepEndHz) nextFreq = sweepStartHz;
                        setFrequency(nextFreq, false);
                    }
                    consecutiveDetections = 0; // Reset for new frequency
                }
            }
        }

            // Periodically post autocorr data
            float[] ac = tsdr.getAutocorr();
            if (ac != null && ac.length > 0) {
                autocorrLive.postValue(ac);
                
                // Calculate signal strength for the bar
                float max = 0;
                float sum = 0;
                for (int i = 5; i < ac.length; i++) {
                    if (ac[i] > max) max = ac[i];
                    sum += ac[i];
                }
                float avg = sum / ac.length;
                float strength = (max / (avg + 1e-9f));
                // Normalize to something like 0-100 for a bar
                float normalized = Math.min(100f, strength * 10f);
                signalLive.postValue(normalized);
            }
        });

        tsdr.setErrorCallback((code, msg) -> {
            Log.e(TAG, "TSDRError " + code + ": " + msg);
            errorLive.postValue("Error " + code + ": " + msg);
            runningLive.postValue(false);
        });
    }

    // ── USB device connection ─────────────────────────────────────────
    public void connectDevice(int usbFd) {
        statusLive.postValue(getApplication().getString(R.string.drone_status_connecting));
        int rc = tsdr.connectFd(usbFd);
        if (rc != 0) {
            errorLive.postValue(getApplication().getString(R.string.drone_error_sdr_open, rc));
            return;
        }
        tsdr.setFrequency(frequencyHz);
        tsdr.setGain(gainDb);
        tsdr.setSampleRate(sampleRate);
        tsdr.setResolution(videoHeight, videoRefresh);
        tsdr.setGainOffset(gainOffset);
        tsdr.setShift(shift);
        tsdr.setParam(TSDRLibrary.PARAM_LOW_PASS_BEFORE_SYNC, lowPassEnabled ? 1 : 0);
        tsdr.setParam(TSDRLibrary.PARAM_AUTOGAIN_AFTER_PROCESSING, autoGainEnabled ? 1 : 0);
        statusLive.postValue(getApplication().getString(R.string.drone_status_connected));
    }

    // ── Processing ────────────────────────────────────────────────────
    public void startProcessing() {
        autocorrLive.postValue(null);
        int rc = tsdr.start();
        if (rc == 0) {
            runningLive.postValue(true);
            updateStatusWithFreq();
        } else {
            errorLive.postValue(getApplication().getString(R.string.drone_error_start_failed, rc));
        }
    }

    public void stopProcessing() {
        if (Boolean.TRUE.equals(runningLive.getValue())) {
            tsdr.stop();
            runningLive.postValue(false);
            sweepLive.postValue(false);
            sweepEnabled = false;
            fullScanEnabled = false;
            autocorrLive.postValue(null);
            statusLive.postValue(getApplication().getString(R.string.drone_status_stopped));
        }
    }

    private void updateStatusWithFreq() {
        String suffix = "";
        if (droneScanEnabled) {
            suffix = " [" + getApplication().getString(R.string.drone_status_drone_scan) + "]";
            if (!droneFrequencies.isEmpty()) {
                suffix += " (" + (dronePresetIndex + 1) + "/" + droneFrequencies.size() + ")";
            }
        } else if (sweepEnabled) {
            suffix = " [" + getApplication().getString(R.string.drone_status_sweeping) + "]";
        }
        
        String label = "";
        Preset p = getCurrentPreset();
        if (p != null) {
            label = p.name + "\n";
        }
        
        statusLive.postValue(String.format(Locale.US, "%s%.3f MHz%s", label, frequencyHz / 1_000_000.0, suffix));
    }

    // ── Settings setters (apply immediately if running) ───────────────
    public void setFrequency(long hz) {
        setFrequency(hz, false);
    }

    public void setFrequency(long hz, boolean userInitiated) {
        framesSinceFreqChange = 0;
        if (userInitiated) {
            this.sweepEnabled = false;
            this.sweepLive.postValue(false);
            this.fullScanEnabled = false;
            this.droneScanEnabled = false;
            this.droneScanLive.postValue(false);
        }

        long targetHz = hz;
        if (targetHz > 1_766_000_000L) {
            if (harmonicsEnabled) {
                while (targetHz > 1_766_000_000L && targetHz > 0) {
                    targetHz /= 2;
                }
            } else {
                targetHz = 1_766_000_000L;
            }
        }
        if (targetHz < 24_000_000L && targetHz > 0) {
            targetHz = 24_000_000L;
        }

        if (targetHz > 0) {
            tsdr.setFrequency(targetHz);
        }
        frequencyHz = hz;
        frequencyLive.postValue(hz);
        if (Boolean.TRUE.equals(runningLive.getValue()))
            updateStatusWithFreq();

        if (userInitiated) {
            getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putLong(PREF_FREQ, hz).apply();
        }
    }

    public void setGain(double db) {
        gainDb = db;
        tsdr.setGain(db);
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putFloat(PREF_GAIN, (float)db).apply();
    }

    public void setSampleRate(int rate) {
        sampleRate = rate;
        tsdr.setSampleRate(rate);
        tsdr.setResolution(videoHeight, videoRefresh); // recompute derived width
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(PREF_SAMPLE_RATE, rate).apply();
    }

    public void setVideoMode(TSDRLibrary.VideoMode mode) {
        videoHeight  = mode.totalHeight;
        videoRefresh = mode.refreshHz;
        tsdr.setResolution(mode);
        saveVideoSettings();
    }

    public void setVideoResolution(int height, double refresh) {
        videoHeight  = height;
        videoRefresh = refresh;
        tsdr.setResolution(height, refresh);
        saveVideoSettings();
    }

    private void saveVideoSettings() {
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(PREF_VIDEO_HEIGHT, videoHeight)
                .putFloat(PREF_VIDEO_REFRESH, (float) videoRefresh)
                .apply();
    }

    public void setGainOffset(double offset) {
        gainOffset = offset;
        tsdr.setGainOffset(offset);
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putFloat(PREF_GAIN_OFFSET, (float)offset).apply();
    }

    public void setShift(double s) {
        shift = s;
        tsdr.setShift(s);
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putFloat(PREF_SHIFT, (float)s).apply();
    }

    public void setLowPassEnabled(boolean enabled) {
        this.lowPassEnabled = enabled;
        tsdr.setParam(TSDRLibrary.PARAM_LOW_PASS_BEFORE_SYNC, enabled ? 1 : 0);
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_LOWPASS, enabled).apply();
    }

    public void setAutoGainEnabled(boolean enabled) {
        this.autoGainEnabled = enabled;
        tsdr.setParam(TSDRLibrary.PARAM_AUTOGAIN_AFTER_PROCESSING, enabled ? 1 : 0);
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_AUTOGAIN, enabled).apply();
    }

    public boolean isLowPassEnabled() { return lowPassEnabled; }
    public boolean isAutoGainEnabled() { return autoGainEnabled; }

    public void setParam(int id, double val) {
        tsdr.setParam(id, val);
    }

    public void syncToPeak(int index) {
        if (index <= 0) return;
        // The autocorrelation index corresponds to a lag in samples.
        // refreshRate = sampleRate / (lagSamples / totalHeight)
        // Actually, the peak at index 'L' means the frame repeats every L samples.
        // So refreshRate = sampleRate / L.
        // In TempestSDR, totalHeight is often used to define the "lines",
        // but the core sync is sampleRate / (pixels_per_line * totalHeight).
        // If we adjust videoRefresh while keeping totalHeight, it changes the line duration.

        double newRefresh = (double) sampleRate / index;
        if (newRefresh > 1.0 && newRefresh < 200.0) {
            setVideoResolution(videoHeight, newRefresh);
            statusLive.postValue(getApplication().getString(R.string.drone_status_synced, newRefresh));
        }
    }

    public void resetAutocorr() {
        tsdr.setParam(TSDRLibrary.PARAM_AUTOCORR_PLOTS_RESET, 1.0);
    }

    public void setSweepEnabled(boolean enabled) {
        this.sweepEnabled = enabled;
        this.sweepLive.postValue(enabled);
        if (enabled) {
            this.fullScanEnabled = false;
            this.droneScanEnabled = false;
            this.droneScanLive.postValue(false);
            // Default sweep range around current freq if not set
            sweepStartHz = Math.max(24_000_000L, frequencyHz - 50_000_000L);
            sweepEndHz   = Math.min(1700_000_000L, frequencyHz + 50_000_000L);
            sweepStepHz  = 2_000_000L;
            setFrequency(sweepStartHz, false);
            lastSweepUpdate = System.currentTimeMillis();
            
            // Auto-start if not running
            if (!Boolean.TRUE.equals(runningLive.getValue())) {
                startProcessing();
            }
        }
    }

    public void setFullScanEnabled(boolean enabled) {
        this.fullScanEnabled = enabled;
        if (enabled) {
            this.sweepEnabled = false;
            this.sweepLive.postValue(false);
            this.droneScanEnabled = false;
            this.droneScanLive.postValue(false);
            sweepStartHz = 24_000_000L;
            sweepEndHz   = harmonicsEnabled ? 3_400_000_000L : 1_700_000_000L;
            sweepStepHz  = 2_000_000L;
            setFrequency(sweepStartHz, false);
            lastSweepUpdate = System.currentTimeMillis();
        }
    }

    public void setDroneScanEnabled(boolean enabled) {
        this.droneScanEnabled = enabled;
        this.droneScanLive.postValue(enabled);
        if (enabled) {
            this.sweepEnabled = false;
            this.sweepLive.postValue(false);
            this.fullScanEnabled = false;
            
            // Build list of drone frequencies with jitter/offsets for better coverage
            Set<Long> freqSet = new TreeSet<>();
            List<Preset> all = presetsLive.getValue();
            if (all != null) {
                for (Preset p : all) {
                    String n = p.name.toUpperCase();
                    if (p.frequencyHz > 0 && (n.contains("RUS:") || n.contains("UKR:") || n.contains("FPV:") || 
                            n.contains("DRONE") || n.contains("ISR:") || n.contains("UAV") || n.contains("QUAD") ||
                            n.contains("CCTV") || n.contains("CAM") || n.contains("SECURITY"))) {
                        // Wide search zone: ±2MHz and ±4MHz coverage
                        freqSet.add(p.frequencyHz - 4_000_000L);
                        freqSet.add(p.frequencyHz - 2_000_000L);
                        freqSet.add(p.frequencyHz);
                        freqSet.add(p.frequencyHz + 2_000_000L);
                        freqSet.add(p.frequencyHz + 4_000_000L);
                    }
                }
            }
            droneFrequencies.clear();
            droneFrequencies.addAll(freqSet);
            scanTotalLive.postValue(droneFrequencies.size());
            
            if (!droneFrequencies.isEmpty()) {
                dronePresetIndex = 0;
                setFrequency(droneFrequencies.get(0));
                lastSweepUpdate = System.currentTimeMillis();
                if (!Boolean.TRUE.equals(runningLive.getValue())) {
                    startProcessing();
                }
            } else {
                this.droneScanEnabled = false;
                this.droneScanLive.postValue(false);
            }
        }
    }

    public void setHarmonicsEnabled(boolean enabled) {
        this.harmonicsEnabled = enabled;
        setFrequency(frequencyHz);
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_HARMONICS, enabled).apply();
    }

    public boolean isSweepEnabled() { return sweepEnabled; }
    public boolean isFullScanEnabled() { return fullScanEnabled; }
    public boolean isDroneScanEnabled() { return droneScanEnabled; }
    public boolean isHarmonicsEnabled() { return harmonicsEnabled; }
    public boolean isStopOnSignal() { return stopOnSignal; }


    public void setStopOnSignal(boolean stop) {
        this.stopOnSignal = stop;
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_STOP_ON_SIGNAL, stop).apply();
    }

    public void setDetectionThreshold(float threshold) {
        this.detectionThreshold = threshold;
        this.thresholdLive.postValue(threshold);
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putFloat(PREF_THRESHOLD, threshold).apply();
    }

    public float getDetectionThreshold() {
        return detectionThreshold;
    }

    // ── Getters ───────────────────────────────────────────────────────
    public long   getFrequencyHz()  { return frequencyHz; }
    public double getGainDb()       { return gainDb; }
    public int    getSampleRate()   { return sampleRate; }
    public int    getVideoHeight()  { return videoHeight; }
    public double getVideoRefresh() { return videoRefresh; }
    public double getGainOffset()   { return gainOffset; }
    public double getShift()        { return shift; }
    public RtlSdrManager getRtlMgr(){ return rtlMgr; }

    @Override
    protected void onCleared() {
        stopProcessing();
        tsdr.disconnect();
        tsdr.destroy();
        renderer.release();
        rtlMgr.unregisterReceiver();
        rtlMgr.closeConnection();
        super.onCleared();
    }
}
