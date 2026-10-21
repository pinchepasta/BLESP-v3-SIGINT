# SDR Neon Remote

Android remote control for [mostly-a-SDR](https://github.com/pinchepasta/mostly-a-SDR),
talking to its `/api/v1` HTTP/JSON API on the Pi.

Neon orange / teal on black. Kotlin, no third-party libs beyond AndroidX + Material.

## Build

    ./build-apk.sh                 # or: open the folder in Android Studio

## First run

1. On the Pi: `sudo ./mostly-a-sdr.sh` (the API installer) — note the token it prints.
2. In the app: LINK tab -> host (IP or `mostly-a-sdr.local`), port 8765, token -> CONNECT
   (or DISCOVER to find the Pi over mDNS).
3. TX tab: pick a `.sub` from the dropdown and hit TRANSMIT, or open any mode.

## Screens

* **TX** — `.sub` dropdown (playback once/loop, repeat, duration), frequency setter, full mode catalogue
* **FILES** — browse sub / media / iq, upload, download, delete, one-tap transmit
* **TOOLS** — raw shell, sdrtool.py, server config, service restart
* **LOG** — server + tx.log tail
* **SYS** — host, temperature, load, disk, tool availability
* **LINK** — connection settings + mDNS discovery

The mode catalogue is fetched from `GET /api/v1/modes`, so every mode the Pi
exposes appears in the app automatically.
