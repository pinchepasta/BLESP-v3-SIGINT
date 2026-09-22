# RF VIDEO Receiver (formerly TempestSDR Android)

An Android port of [martinmarinov/TempestSDR](https://github.com/martinmarinov/TempestSDR) — remote video eavesdropping via unintentional electromagnetic emissions, running on Android 9+ devices with an RTL-SDR dongle over USB OTG.

---

## What it does

TempestSDR reconstructs the image being displayed on a nearby monitor by picking up the RF emissions that leak from the video cable and electronics. This Android port brings the same technique to a phone or tablet:

- RTL-SDR dongle plugged in via a **USB OTG** (USB-A → USB-C/micro-USB) cable
- No root, no special kernel modules — uses the standard Android USB Host API
- Same DSP pipeline as the original: IQ → magnitude → line sync → frame accumulation
- Live autocorrelation view for tuning the framerate estimate
- All controls accessible via the Settings bottom-sheet

---

## Hardware requirements

| Item | Details |
|------|---------|
| Android device | Android 9 (API 28) or newer, USB Host capable |
| USB OTG cable  | USB-A (male) → USB-C or Micro-USB (male), powered preferred |
| RTL-SDR dongle | Any RTL2832U-based stick (RTL-SDR Blog V3/V4 recommended) |
| Antenna | Wideband or frequency-appropriate; a simple telescopic works |

The RTL-SDR Blog V3/V4 is the most tested dongle (VID `0x0BDA` / PID `0x2838`). A powered USB OTG hub prevents the dongle from browning out the phone's port.

---

## Building

### Prerequisites

```
Android Studio Hedgehog (2023.1) or newer
Android NDK r25c or newer (install via SDK Manager → SDK Tools → NDK)
CMake 3.22.1 (install via SDK Manager → SDK Tools → CMake)
```

### 1. Clone librtlsdr and libusb (Android ports)

The native build pulls in librtlsdr and a libusb Android port. Fetch them into the JNI directory:

```bash
cd app/src/main/jni

# libusb Android port (provides USB access without root)
git clone https://github.com/libusb/libusb.git libusb

# librtlsdr (standard upstream)
git clone https://github.com/osmocom/rtl-sdr.git rtlsdr
# Copy the needed source files:
cp rtlsdr/src/rtl-sdr.c   rtlsdr/
cp rtlsdr/src/tuner_*.c   rtlsdr/
cp rtlsdr/include/rtl-sdr.h       rtlsdr/
cp rtlsdr/include/rtl-sdr_export.h rtlsdr/
```

The CMakeLists.txt expects this layout:
```
jni/
  libusb/        ← libusb source (core.c, io.c, os/linux_usbfs.c …)
  rtlsdr/        ← librtlsdr source (rtl-sdr.c, tuner_r82xx.c …)
  tsdr/          ← TempestSDR C engine (already in this repo)
  jni_bridge.c   ← JNI glue (already in this repo)
  rtlsdr_plugin.c
  CMakeLists.txt
```

### 2. Build

```bash
# Open the project in Android Studio and click Build → Make Project
# OR from the command line:
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

### 3. Install

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Usage

1. **Connect** the RTL-SDR via USB OTG. Android will prompt to open TempestSDR — tap Yes, or tap **Connect** in the app.
2. Open **Settings** and set the **center frequency** to the harmonic of the target monitor's pixel clock. Common starting points:
   - HDMI 1080p60: pixel clock ~148.5 MHz → try 148 MHz, 297 MHz, 445 MHz
   - DVI 1280×1024@60: pixel clock ~108 MHz → try 108 MHz, 216 MHz
   - VGA 800×600@60: pixel clock ~40 MHz → try 40 MHz, 80 MHz, 120 MHz
3. Set **Video Mode** to match the target (or use Custom height/refresh).
4. Tap ▶ (**Start**). Frames appear in the display area once the signal is acquired.
5. Adjust **Horizontal Shift** until the image is horizontally aligned.
6. The **autocorrelation** graph at the bottom shows framerate peaks — use them to fine-tune the refresh rate.

### Tips

- Use a directional antenna (yagi or log-periodic) for better signal rejection.
- Higher gain isn't always better — reduce it if the image looks washed out.
- Enable **Low-pass before sync** if the signal is noisy.
- Enable **Auto-gain after processing** for automatic contrast normalisation.
- The RTL-SDR Blog V3 bias-T (if you have an active antenna) can be enabled with `rtlsdr_set_bias_tee()` — add a button in the UI as needed.

---

## Project structure

```
TempestSDR-Android/
├── app/src/main/
│   ├── java/com/tempestsdr/
│   │   ├── MainActivity.java          ← Entry point, USB intent handling
│   │   ├── core/TSDRLibrary.java      ← Java wrapper over JNI
│   │   ├── dsp/FrameRenderer.java     ← float[] → Bitmap
│   │   ├── rtlsdr/RtlSdrManager.java  ← USB Host + permission flow
│   │   └── ui/
│   │       ├── MainViewModel.java     ← MVVM state owner
│   │       ├── SettingsDialog.java    ← Bottom-sheet settings
│   │       └── AutocorrView.java      ← Autocorrelation canvas
│   ├── jni/
│   │   ├── CMakeLists.txt             ← NDK build definition
│   │   ├── jni_bridge.c               ← JNI ↔ C glue
│   │   ├── rtlsdr_plugin.c            ← librtlsdr async reader
│   │   └── tsdr/
│   │       ├── include/TSDRLibrary.h  ← Public C API
│   │       ├── TSDRLibrary.c          ← DSP pipeline
│   │       ├── dsp.c / dsp.h          ← Filters
│   │       ├── circbuff.c / .h        ← IQ ring buffer
│   │       └── syncdetector.c / .h    ← Autocorrelation sync
│   ├── res/
│   │   ├── layout/activity_main.xml
│   │   ├── layout/dialog_settings.xml
│   │   └── xml/usb_device_filter.xml  ← RTL-SDR VID/PID list
│   └── AndroidManifest.xml
```

---

## Differences from the desktop version

| Feature | Desktop (original) | Android port |
|---------|-------------------|--------------|
| SDR front-end | Plugin DLL/SO loaded at runtime | librtlsdr linked statically, `rtlsdr_open_fd()` for USB Host |
| GUI framework | Java Swing | Android Views + Material 3 |
| Threading | Java threads | POSIX pthreads (NDK) + Android Looper for UI |
| Multi-dongle super-bandwidth | Supported | Stubbed out (single dongle) |
| ExtIO / UHD / SDRPlay | Supported via plugins | Not supported (RTL-SDR only) |
| Auto-resolution detection | Full | Autocorrelation chart exposed; manual tuning |

---

## Legal

This software is for **authorised security research and educational use only**. Intercepting video signals from displays you do not own or have explicit permission to test may violate laws in your jurisdiction (e.g. the Computer Fraud and Abuse Act in the US, the Computer Misuse Act in the UK). Use responsibly.

Licensed under **GNU GPL v3** — same as the upstream TempestSDR project.
