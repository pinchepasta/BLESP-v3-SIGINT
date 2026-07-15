<img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/blesplogo.png" alt="BLESP v3 Logo" width="25%" height="25%">

Advanced signal surveillance platform.

Rather than listing anonymous signals, BLESP v3 interprets patterns and protocols, to give you a structured overview of the wireless ecosystem, for example from everyday personal devices to complex infrastructure systems, all in one device.

<b>What I'm working on:</b> <a href="https://github.com/pinchepasta/Flipper-Zero-Firmware-on-Cardputer-ADV">mostly-a-Flipper</a>  integration and an iOS version of the app.


---
## Screenshots

<img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/1.jpg" alt="BLESP v3" width="25%" height="25%"> <img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/2.jpg" alt="BLESP v3" width="25%" height="25%"> <img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/3.jpg" alt="BLESP v3" width="25%" height="25%"> <img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/4.jpg" alt="BLESP v3" width="25%" height="25%">
---

## Features
Realtime BLE scanning — discovers all nearby Bluetooth Low Energy devices
Realtime WiFi scanning— discovers all 2.4GHz / 5GHz access points
Live distance estimation — RSSI-based path-loss model, updates every second
Tap a blip - opens detail screen (name, MAC, RSSI, distance, security, UUIDs…)


---

## Build Instructions (Android Studio)

### Requirements

Android Studio  Hedgehog 2023.1+ or newer 
JDK 17 (bundled with Android Studio) 
Android SDK  API 34 (compile), API 26 min 
Kotlin  1.9.0 
Gradle  8.0 

### Steps

1. **Install Android Studio**  
   Download from https://developer.android.com/studio

2. **Open the project**  
   - Launch Android Studio  
   - Choose **"Open"** and select the `BLEWifiRadar/` folder  
   - Wait for Gradle sync to finish (~2 min first time)

3. **Set SDK path**  
   - Copy `local.properties.template` → `local.properties`  
   - Set `sdk.dir` to your Android SDK location  
   - Android Studio usually does this automatically on first open

4. **Enable Developer Mode on your S10**  
   - Settings → About phone - Software information  
   - Tap **Build number** 7 times  
   - Settings → Developer options → Enable **USB debugging**

5. **Connect & Run**  
   - Plug in your S10 via USB  
   - Allow USB debugging on the phone  
   - In Android Studio: press: Run  
   - Select your S10 from the device list  
   - App installs and launches automatically

---

## Build via Command Line (optional)

```bash
# Make gradlew executable
chmod +x gradlew

# Copy and configure local.properties
cp local.properties.template local.properties
# Edit local.properties → set sdk.dir=/home/user/Android/Sdk  (your path)

# Build debug APK
./gradlew assembleDebug

# APK output location:
# app/build/outputs/apk/debug/app-debug.apk

# Install directly to connected device
./gradlew installDebug
```

---

## Install Pre-built APK (if you build it yourself)

```bash
# After building:
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or transfer the APK to your phone and open it (requires "Install unknown apps" enabled).

---

## Permissions Required

The app will ask for these on first launch:

`ACCESS_FINE_LOCATION`  Required by Android for BLE + WiFi scanning 
`BLUETOOTH_SCAN`  Scan for BLE devices (Android 12+) 
`BLUETOOTH_CONNECT`  Connect to BLE devices 
`ACCESS_WIFI_STATE`  Read WiFi scan results 
`CHANGE_WIFI_STATE`  Trigger WiFi scans 

> **Note:** Android requires Location permission for BLE/WiFi scanning even if you don't use GPS. This is an Android OS requirement, not optional.

---

## How Distance Works

Distance is estimated using the **Free-Space Path Loss** model:

```
distance = 10 ^ ((TxPower - RSSI) / (10 × n))
```

- `TxPower` = reference RSSI at 1 metre (−59 dBm BLE, −50 dBm WiFi)
- `RSSI` = measured signal strength in dBm
- `n` = path loss exponent (2.0 = open space)

This is an **estimate** — walls, interference, and antenna orientation affect accuracy. Treat it as a rough guide (±30–50%).


