<img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/blesplogo.png" alt="BLESP v3 Logo" width="25%" height="25%">

BLESP is a hacking multitool!

Imagine having all your hacking and pentesting devices combined and controlled through your phone, all on a live radar map.
That's BLESP v3.

Rather than listing anonymous signals, BLESP v3 interprets patterns and protocols, to give you a structured overview of the wireless ecosystem surrounding you, and it helps you to find a way into those systems, and puts together a strategy. From everyday personal devices like smartphones to complex infrastructure systems or IOT devices, all in your hand, with BLESP v3 you got it all.

By the way: The whole app was developed and used as a movie prop, but it was too good to keep it locked away.
<br>


<h2>How does it work?</h2>

<img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/calc.jpg" alt="BLESP v3" width="25%" height="25%"> <img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/auth.jpg" alt="BLESP v3" width="25%" height="25%"> <img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/rdy.jpg" alt="BLESP v3" width="25%" height="25%"> 

The app disguises as a calculator, like an actual spy app, and it only lets you in if you enter the right code, which can also consist of any math operation you like, 36+45/8 for example.

<br>


<h2>What's the PIN / Password?</h2>

<br>

<b>123456</b>

(After entering the right pin code, you can spot "change password" for a brief moment, use this to change the password)

___


<br>
<br>


<h2>What does it detect/manipulate?:</h2>

2.4GHz and 5GHz Wifi, Bluetooth and BLE, ZigBee and Matter, Cars, Drones, RC and Nintendo RC, IMSI Catchers, ADS-B Planes, 433MHz Key fobs, CCTV / Surveillance cameras, Computers, Headphones, martphones and last but not least: Rockets and Military Drones ( it can display their video feed with additional rtlsdr component

<br>
<br>


<h2>The Radar Map:</h2>

<img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/radar1.jpg" alt="BLESP v3" width="25%" height="25%"> <img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/2.jpg" alt="BLESP v3" width="25%" height="25%"> 
<img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/list.jpg" alt="BLESP v3" width="25%" height="25%"><img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/detail.jpg" alt="BLESP v3" width="25%" height="25%"> 

Now it shows you a radar map with all the devices around you, it shows differences in protocol and frequency with different colors. 
The List icon in the top bar opens a detail view of the device you're looking at, you can start a proximity alarm to set off an alarm tone if the subject moves, or you enable the ping tone to give an accoustic hint how close you are to the device. It also shows you all possible ways to hack the target.

If you want to <b>enter the settings</b> you just have to click the little cog icon.
From there you can choose a different color theme, setup the immersion option, and you can see the values that start an audio or video recording when entered in calculator. The recording even works when the screen is completely off.

<br>
<br>

<img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/stat.jpg" alt="BLESP v3" width="25%" height="25%"> <img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/arch.jpg" alt="BLESP v3" width="25%" height="25%"> 

The little icon right next to the settings icon opens the networks statistics screen which summarizes whatever you managed to scan since you got the app. You can search this data, and also export and share it, you just need to click the archive icon right next to network statistics. The share button is at the top of the page.

<br>
<br>

<h2>Does it allow you to interact with other devices or protocols?</h2>

<br>

<img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/fastpair.jpg" alt="BLESP v3" width="25%" height="25%"> <img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/esl.jpg" alt="BLESP v3" width="25%" height="25%"> <img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/drn2.jpg" alt="BLESP v3" width="25%" height="25%"> 

<b>BLE Headphones:</b> Yes it does! Just click the bluetooth icon in the top bar to open the fastpair vulnerability scanner and exploitation tool. It gives you the option to pair your device with vulnerable BLE headphones or headsets, and listen to their microphone, and record it.

<b>Electronic shelf tags:</b> Also yes, you can scan the barcode that's on the ESL tag, then you have the option to choose and image, or text, or other payloads like blinking status LEDs, and export the script, to upload it to the tag with a Flipper, or a Bruce device.

<b>FPV & War Drones:</b> Click the drone icon in the latest 3.2 update and you'll be able to connect an rtlsdr or hackrf device to your phone to receive the live video feed of many drone models out there. I will add more decoders, but this needs a little more time.


<br>
<br>

<h2>Can it connect to external hardware?</h2>

<br>

<img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/ext.jpg" alt="BLESP v3" width="25%" height="25%"> <img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/ssh.jpg" alt="BLESP v3" width="25%" height="25%"> <img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/dvice.jpg" alt="BLESP v3" width="45%" height="45%">  

By clicking on the [ext] button, you can initiate a connection to a device like mostly-a-Flipper, Bruce, Flipper Zero, Cardputer ADV and so on, wirelessly. Now you can forward SubGHz or Infrared payloads in .sub and .ir format, for example if you want to flash an ESL Tag via .ir payload.

If you need more power, or a very specific function, just click [ssh], it starts a terminal window and lets you ssh into your favorite hacking devices like a Raspberry Pi or just a plain old Linux computer, for example to jam a radio signal on 433.92MHz via RPITX, or you include openclaw for automated hacking tasks.
Btw, I'm already working on a deeper integration of agents like hermes or openclaw.

<br>
<br>

<h2>Can it send and receive files?</h2>

<br>

<img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/qrmen.jpg" alt="BLESP v3" width="25%" height="25%"> <img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/qrs.jpg" alt="BLESP v3" width="25%" height="25%"> <img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/qrr.jpg" alt="BLESP v3" width="25%" height="25%">  

Yes! Imagine you need your teammate to have a file but you know you're being watched, and your opponent has access to an sdr, so you cannot emmit any kinda radio emmissions, not even nfc. This is where the qr file sharing system comes in handy. I've read about it a while ago and tried to build my own version and integrate it here.

<br>
<br>

<h2>Does it come in many colors?</h2>

<img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/default.jpg" alt="BLESP v3" width="25%" height="25%"> <img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/bbgum.jpg" alt="BLESP v3" width="25%" height="25%"> <img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/summertime.jpg" alt="BLESP v3" width="25%" height="25%"> <img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/morio.jpg" alt="BLESP v3" width="25%" height="25%"> 
<img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/redn.jpg" alt="BLESP v3" width="25%" height="25%"> 
<img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/pink.jpg" alt="BLESP v3" width="25%" height="25%"> 
<img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/naranja.jpg" alt="BLESP v3" width="25%" height="25%"> 
<img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/ylw.jpg" alt="BLESP v3" width="25%" height="25%"> 
<img src="https://github.com/pinchepasta/BLESP-v3-SIGINT/blob/main/booklet/highcontrast.jpg" alt="BLESP v3" width="25%" height="25%"> 

<br>
<b>Of course it does! And they're all OLED optimized.</b>

<br>
<br>

<h2>And that's what I'm currently working on:</h2>

- Write ESL Shelf Tags via NFC/Bluetooth
- ADS-B Transmitter integration
- DeAuth Attack Detector
- Better Messenger UI
- LoRa integration
- Data Transfer through QR

  
<br>
<br>

<h2>And now the fun part:</h2>

<br>

<b> Build Instructions (Android Studio)</b>

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
   - Select your device from the device list  
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


