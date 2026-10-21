# Bruce Remote — Android USB-Serial Companion App

A native Android (Kotlin + Jetpack Compose) app that talks to a [Bruce](https://github.com/BruceDevices/firmware)
device over **USB serial** and gives you a real GUI on your phone: it mirrors the
device's actual screen live and lets you drive it with an on-screen D-pad, plus a
raw CLI terminal and one-tap quick commands.

It does **not** reimplement Bruce's menus by guesswork. Bruce ships its own
serial protocol and a reference web tool (`sd_files/esp32_serial_navigator.html`,
built on the Web Serial API) that streams the device's framebuffer as binary draw
packets and accepts `nav <direction>` commands back. This app is a line-for-line
Kotlin port of that same protocol, so what you see on your phone is literally
what's on the device's screen — it stays correct across firmware menu changes
instead of drifting out of sync with a hardcoded UI.

## How it works

- **Transport**: USB CDC-ACM, 115200 baud, 8N1 — via
  [`usb-serial-for-android`](https://github.com/mik3y/usb-serial-for-android)
  (Android has no built-in USB-serial API, so this library handles the
  CH340/CP210x/FTDI/native-USB chip variants Bruce boards use).
- **Screen mirror**: the device streams binary packets framed as
  `0xAA <size> <functionId> <payload>` (rects, circles, triangles, arcs, text,
  etc., colors in RGB565). `BruceDisplayRenderer.kt` decodes these directly onto
  an Android `Bitmap`, exactly mirroring the `renderTFT()` switch-statement in
  Bruce's own HTML tool.
- **Input**: button presses are sent as plain text lines, e.g. `nav up`,
  `nav sel`, `nav esc` — the same commands the device's own web navigator sends.
- **Fallback full-refresh**: `display dump` asks the device to re-send the whole
  framebuffer as hex text (terminated by `[End of Dump]`) instead of binary —
  useful right after connecting, before any new draw packets have arrived.
- **Terminal tab**: sends any text command and logs the device's text replies,
  for the broader documented CLI (`info`, `uptime`, `free`, `settings`, `ls`,
  `power reboot/off/sleep`, etc.).

## Project layout

```
app/src/main/java/com/bruceremote/app/
├── MainActivity.kt
├── serial/
│   ├── UsbSerialManager.kt       USB permission + CDC-ACM connection
│   ├── SerialStreamParser.kt     binary-packet vs text-stream splitting
│   ├── BruceDisplayRenderer.kt   draw-packet → Bitmap decoder
│   └── ConnectionModels.kt
├── viewmodel/
│   └── BruceViewModel.kt         wires the above together for Compose
└── ui/
    ├── AppScaffold.kt            top bar, device picker, tabs
    ├── NavigatorScreen.kt        live screen mirror + D-pad
    ├── TerminalScreen.kt         raw CLI log + input
    ├── QuickActionsScreen.kt     one-tap common commands
    └── theme/                    dark/cyan theme matching Bruce's own tools
```

## Building

1. Open the project root in Android Studio (Koala or newer), or from the
   command line:
   ```
   ./gradlew assembleDebug
   ```
2. Requires a phone with **USB host (OTG)** support and a USB-C/USB-A OTG
   cable/adapter to connect your Bruce device.
3. On first connect, Android will show a system "Allow app to access USB
   device?" dialog — accept it. The app also declares an intent filter so it
   can be offered automatically when you plug the device in with the app
   installed.

minSdk 26, compileSdk/targetSdk 34, Kotlin 2.0, Gradle 8.7 (wrapper included).

## Using the app

- **Navigator tab**: shows the live device screen. Tap ▲▼◀▶ to move, ● to
  select, ⟲ to go back, M for the long-press/menu action, Pg▲/Pg▼ to page.
  "Refresh" forces a full redraw via `display dump` if the mirror ever looks
  stale (e.g. right after connecting).
- **Terminal tab**: a plain serial console — type any command Bruce's CLI
  understands and see the response.
- **Quick Actions tab**: one-tap buttons for `info`, `uptime`, `free`,
  `i2c`, `settings`, `help`, `reboot`, `sleep`, `power off`.

## A note on scope and use

Bruce is red-team/offensive-security firmware (WiFi deauth, BLE spam, BadUSB,
RF/RFID replay, evil portal, etc.) — this app is a **generic remote-control
and screen-mirroring client**, exactly analogous to the HTML tool shipped in
Bruce's own repo. It doesn't add, automate, or hide any capability the device
didn't already have; it just gives you a native way to see and drive the
screen you already have physical access to. As with the firmware itself,
only use this against devices/networks you own or are explicitly authorized
to test.
