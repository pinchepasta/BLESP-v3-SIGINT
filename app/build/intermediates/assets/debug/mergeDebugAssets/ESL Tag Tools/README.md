# ESL Tag Tools // by mostlyAWESOME Media

**Native Android port of the ESL Tag Tools by mostlyAWESOME Media.**

---

## Requirements

- Android Studio Ladybug (2024.2.1) or newer
- Android SDK 34 (Compile & Target)
- Kotlin 2.2.10
- Gradle 9.3.1
- AGP 9.1.1
- **Min SDK: API 26 (Android 8.0)**

---

## Project Structure

```
TagTinker/
├── app/src/main/
│   ├── java/com/mostlyawesome/tagtinker/
│   │   ├── MainActivity.kt              ← Entry point + navigation
│   │   ├── MainViewModel.kt             ← App state + business logic
│   │   ├── data/
│   │   │   └── EslProtocol.kt           ← PLID/CRC/IR encoding + data models
│   │   ├── utils/
│   │   │   └── ImageProcessor.kt        ← Dithering, XBM export, IR payload
│   │   └── ui/
│   │       ├── theme/Theme.kt           ← Dark/light design tokens
│   │       ├── components/Components.kt ← Reusable Compose widgets
│   │       └── screens/
│   │           ├── MainScreen.kt        ← Two-column layout + tab host
│   │           ├── TargetEslSection.kt  ← Section 01: ESL target config
│   │           ├── DeploySection.kt     ← Section 02: Deploy to MULTIPASS
│   │           ├── BarcodeScannerScreen.kt ← ML Kit CameraX scanner
│   │           ├── IrFileTab.kt         ← IR file builder tab
│   │           ├── ImageTab.kt          ← Image import + dither tab
│   │           └── XbmAndProtocolTabs.kt← XBM export + protocol info tabs
│   └── res/
│       ├── values/strings.xml, themes.xml, colors.xml
│       └── xml/file_paths.xml           ← FileProvider config
├── build.gradle                         ← Root build
├── app/build.gradle                     ← App build + deps
└── settings.gradle
```

---

## Setup Instructions

1. **Open in Android Studio**
   ```
   File → Open → select the TagTinker/ folder
   ```

2. **Sync Gradle**
   Android Studio will prompt you — click "Sync Now".

3. **Run on device or emulator**
   - Physical device recommended for camera/barcode scanning
   - Use API 26+ emulator for everything except the camera scanner

4. **Grant permissions**
   - Camera (for barcode scanner)
   - Storage/Media Images (for image picker)

---

## What's New vs Web Version

###  New Branding & UX
- **Official mostlyAWESOME Branding** — Integrated the new logo into the app's header.
- **Dedicated Splash Screen** — Professional brand-aligned loading screen on startup.
- **Adaptive UI** — Fixed layout constraints to ensure smooth, crash-free scrolling across all functional tabs.

###  Better Barcode Scanner
- **ML Kit** (Google's on-device barcode engine) replaces ZXing — faster, hardware-accelerated
- **CameraX** provides stable high-res camera access with proper lifecycle management
- **Zoom controls** — tap 1×/1.5×/2×/3× chips OR use the continuous zoom slider
- **Torch toggle** — flashlight for dark environments (common in stock rooms)
- **ESL-biased detection** — prefers long numeric barcodes typical of ESL tags
- **3-frame confirmation** before accepting a scan to avoid misfires
- **Haptic feedback** on successful scan
- **Animated scan line** + color-coded corner brackets (white → orange confirming → green success)
- **Smooth slide-up transition** from main screen

###  Native Android Advantages
- Works fully offline — no web server, no JS loading
- Persistent state across app lifecycle
- Native share sheet for exporting `.ir` files and `esl_tools.js`
- System image picker with full media access
- Dark/light mode synced to app toggle (persists in ViewModel)

---

## All Functionality Preserved

| Feature | Web | Android |
|---------|-----|---------|
| Barcode / Tag ID input 
| Barcode camera scanner ZXing  ML Kit + CameraX |
| ESL width/height config 
| TX Repeats slider 
| Inter-frame Delay slider
| PLID calculation
| CRC-8 calculation 
| IR file generation 
| IR file download/share  (native share) |
| Image import + dithering 
| Floyd-Steinberg dither 
| Bayer 4×4 dither 
| Invert + fit options
| ESL preview Canvas Compose Image 
| XBM export 
| MULTIPASS JS snippet 
| esl_tools.js download  (native share) |
| Protocol reference tab 
| Dark/Light mode 
| "Research use only" warning 

---

## ESL Protocol Reference

| Param | Value |
|-------|-------|
| IR Carrier | 1.255 MHz |
| Modulation | 38 kHz |
| Encoding | Manchester / NRZ |
| Packet | `[SYNC 9ms][PLID 7B][CMD 1B][PAYLOAD NB][CRC-8]` |
| CRC | XOR of all packet bytes |

**Commands:** `0x10` Text · `0x20` Image · `0x30` Test · `0x40` LED · `0x50` Clear

---

## Credits & License

- **Android port:** mostlyAWESOME Media
- **License:** GPL-3.0

>  **Research Use Only.** Only use on hardware you personally own or have explicit authorization to test. Do not use in stores or on deployed systems.
