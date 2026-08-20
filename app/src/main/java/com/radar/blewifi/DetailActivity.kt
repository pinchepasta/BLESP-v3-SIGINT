package com.radar.blewifi

import android.content.Intent
import android.net.Uri
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.media.ToneGenerator
import android.media.AudioManager
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import com.radar.blewifi.databinding.ActivityDetailBinding

class DetailActivity : AppCompatActivity(), ScannerManager.ScanListener {

    companion object {
        const val EXTRA_DEVICE = "extra_device"
    }

    private lateinit var binding: ActivityDetailBinding
    private lateinit var scanner: ScannerManager
    private lateinit var currentDevice: ScanDevice

    private var GREEN  = Color.parseColor("#00FF41")
    private var CYAN   = Color.parseColor("#00FFFF")
    private var AMBER  = Color.parseColor("#FFB300")
    private var PINK   = Color.parseColor("#FF00FF")

    private var currentTheme = RadarView.Theme.DEFAULT

    private val handler = Handler(Looper.getMainLooper())
    private var blinkOn = true

    private var toneGenerator: ToneGenerator? = null
    private var lastBeepTime = 0L
    private var isSoundEnabled = false

    private val buttonAnimators = mutableMapOf<View, android.animation.ObjectAnimator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val themeName = prefs.getString("theme_name", RadarView.Theme.DEFAULT.name)
        currentTheme = try { RadarView.Theme.valueOf(themeName!!) } catch (e: Exception) { RadarView.Theme.DEFAULT }

        updateThemeColors()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyThemeToUI()

        @Suppress("DEPRECATION")
        val device = intent.getParcelableExtra<ScanDevice>(EXTRA_DEVICE) ?: run {
            finish(); return
        }
        currentDevice = device

        scanner = ScannerManager(this)
        scanner.addListener(this)
        
        displayDevice(device)
        updateAlarmButton()
        updateSoundButton()
        applyGlow(binding.btnBack, true)

        binding.btnBack.setOnClickListener { finish() }

        binding.tvAddress.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("MAC Address", currentDevice.address)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(this, "[ ADDR COPIED ]", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        binding.btnAlarm.setOnClickListener {
            val enabled = scanner.isAlarmEnabled(currentDevice.id)
            scanner.setAlarm(currentDevice.id, !enabled)
            updateAlarmButton()
        }

        binding.btnSound.setOnClickListener {
            isSoundEnabled = !isSoundEnabled
            updateSoundButton()
        }

        binding.btnEsl.setOnClickListener {
            val packageName = "com.mostlyawesome.tagtinker"
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/pinchepasta/ESL-Tag-Tools"))
                startActivity(browserIntent)
            }
        }

        binding.btnBeepAirTag.setOnClickListener {
            if (currentDevice.isAirTag) {
                binding.btnBeepAirTag.text = "► 03 BEEPING..."
                toneGenerator?.startTone(ToneGenerator.TONE_DTMF_D, 1000)
                handler.postDelayed({
                    binding.btnBeepAirTag.text = "► 03 TRIGGER BEEP"
                }, 3000)
            }
        }

        val exploits = when (currentDevice.type) {
            DeviceType.WIFI -> arrayOf(
                "WPA2 Krack (CVE-2017-13077)",
                "PMKID Attack (CVE-2018-15320)",
                "WPS Pixie-Dust (CVE-2014-9130)",
                "Deauth Flood (802.11 DoS)",
                "Evil Twin (MITM Phishing)",
                "FragAttacks (CVE-2020-24588)"
            )
            DeviceType.LTE, DeviceType.FIVE_G -> arrayOf(
                "Stingray (IMSI Catcher)",
                "Silent SMS Tracking (Type 0)",
                "SS7 Intercept (MITM)",
                "Baseband RCE (CVE-2023-24033)",
                "Cipher Downgrade (A5/1)"
            )
            DeviceType.CAR -> arrayOf(
                "Relay Attack (Passive Entry)",
                "CAN Bus Injection (CVE-2017-14932)",
                "RollJam (Replay Attack)",
                "TPMS Spoofing (CVE-2016-1000326)",
                "GPS Jamming (Tracker DoS)",
                "Infotainment RCE (CVE-2020-11500)"
            )
            DeviceType.ESCOOTER -> arrayOf(
                "BLE Lock Bypass (CVE-2019-10887)",
                "Firmware Downgrade (Rollback)",
                "Speed Limit Override (UDS)",
                "DoS Advertising Flood",
                "Battery BMS Intercept",
                "Inertial Sensor Spoofing"
            )
            DeviceType.AIRCRAFT -> arrayOf(
                "ADS-B Ghosting (SDR)",
                "Mode S Interrogation",
                "TCAS Resolution Inhibit",
                "ACARS Injection (Uplink)",
                "Squawk Manipulation",
                "GPS Spoofing (Position)"
            )
            DeviceType.BLE -> arrayOf(
                "BlueHydra (Discovery)",
                "GATTacker (MITM)",
                "KNOB Attack (CVE-2019-9506)",
                "BIAS (CVE-2020-10135)",
                "BlueFrag RCE (CVE-2020-0022)",
                "BleedingTooth (CVE-2020-12351)"
            )
            DeviceType.TV -> arrayOf(
                "HID Remote Hijack",
                "CallStranger (CVE-2020-12695)",
                "DLNA Media Injection",
                "HbbTV Privacy Leak",
                "Miracast Overlay (MITM)",
                "Browser RCE (CVE-2018-4094)"
            )
            DeviceType.COMPUTER -> arrayOf(
                "MouseJack (CVE-2016-1937)",
                "Logitacker (CVE-2019-13054)",
                "BlueBorne (CVE-2017-1000251)",
                "SMB Relay (Credential Theft)",
                "RDP Downgrade (CVE-2018-0886)",
                "WiFi Direct Tunneling"
            )
            DeviceType.SMARTPHONE -> arrayOf(
                "AirDrop Spoof (CVE-2019-8641)",
                "Contact Beam (NFC/BT)",
                "Nearby Share (CVE-2021-36768)",
                "FORCEDENTRY (CVE-2021-30860)",
                "Stagefright (CVE-2015-3824)",
                "SIMjacker (CVE-2019-16256)"
            )
            DeviceType.PAGER -> arrayOf(
                "POCSAG Intercept (SDR)",
                "FLEX Decryption",
                "Message Injection",
                "Replay Attack",
                "Address Spoofing"
            )
            else -> arrayOf(
                "IMSI Catcher Detect",
                "Cellular Jamming",
                "Frequency Analysis",
                "Signal Triangulation"
            )
        }
        val adapter = object : android.widget.ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, exploits) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                (v as? android.widget.TextView)?.setTextColor(getDeviceColor(currentDevice.type))
                (v as? android.widget.TextView)?.setBackgroundColor(if (currentTheme == RadarView.Theme.HIGH_CONTRAST) Color.WHITE else Color.TRANSPARENT)
                (v as? android.widget.TextView)?.typeface = android.graphics.Typeface.MONOSPACE
                return v
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent)
                (v as? android.widget.TextView)?.setTextColor(getDeviceColor(currentDevice.type))
                (v as? android.widget.TextView)?.setBackgroundColor(
                    when (currentTheme) {
                        RadarView.Theme.HIGH_CONTRAST -> Color.WHITE
                        RadarView.Theme.RED_NIGHT -> Color.BLACK
                        RadarView.Theme.PINK -> Color.BLACK
                        RadarView.Theme.NEON -> Color.BLACK
                        RadarView.Theme.NARANJA -> Color.BLACK
                        RadarView.Theme.BUBBLEGUM -> Color.BLACK
                        RadarView.Theme.SUMMERTIME -> Color.BLACK
                        RadarView.Theme.MORIO -> Color.BLACK
                        RadarView.Theme.DEFAULT -> Color.BLACK
                    }
                )
                (v as? android.widget.TextView)?.typeface = android.graphics.Typeface.MONOSPACE
                return v
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spExploits.adapter = adapter
        
        if (currentTheme == RadarView.Theme.RED_NIGHT) {
            binding.spExploits.background = androidx.appcompat.content.res.AppCompatResources.getDrawable(this, R.drawable.btn_bg_red)
        } else if (currentTheme == RadarView.Theme.MORIO) {
            binding.spExploits.background = androidx.appcompat.content.res.AppCompatResources.getDrawable(this, R.drawable.btn_bg_morio)
        }
        
        binding.spExploits.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.tvExploitDesc.text = when(currentDevice.type) {
                    DeviceType.WIFI -> when(position) {
                        0 -> "Risk: CRITICAL | CVE-2017-13077. The Key Reinstallation Attack (KRACK) targets the 4-way handshake of the WPA2 protocol. By manipulating and replaying cryptographic handshake messages, an attacker can force a victim to reuse encryption keys, enabling decryption of sensitive traffic, TCP stream injection, and session hijacking without the WiFi password. [Tools: krackattacks-scripts, wpa_supplicant]"
                        1 -> "Risk: CRITICAL | CVE-2018-15320. This attack targets the RSN IE (Robust Security Network Information Element) of a single EAPOL frame. Unlike traditional attacks, it doesn't require a client to be connected. The PMKID is hashed with the AP's MAC, allowing for high-speed offline brute-forcing of the WPA2 Pre-Shared Key using tools like Hashcat and hcxdumptool. [Tools: hcxdumptool, Hashcat]"
                        2 -> "Risk: HIGH | CVE-2014-9130. WPS Pixie-Dust exploits the low-entropy generation of E-S1 and E-S2 nonces in certain chipsets (Broadcom, Realtek, Mediatek). By intercepting the M3 message, an attacker can recover the WPS PIN in under a second, granting full WPA2/WPA3 credentials and bypassing complex password security. [Tools: pixiewps, reaver, bully]"
                        3 -> "Risk: MEDIUM. Deauthentication Flooding exploits the lack of encryption in 802.11 management frames. By broadcasting spoofed 'deauth' packets with the target AP's MAC address, an attacker can force all clients to disconnect. This is often used as a precursor to Evil Twin attacks or as a persistent Denial of Service (DoS) attack. [Tools: aireplay-ng, mdk4, Bettercap]"
                        4 -> "Risk: CRITICAL. The Evil Twin attack involves deploying a rogue Access Point with a cloned SSID and MAC address. By deauthenticating users from the legitimate AP, they are forced to connect to the attacker's node, enabling full Man-in-the-Middle (MITM) capabilities, credential harvesting via captive portals, and DNS poisoning. [Tools: Airgeddon, WiFi Pumpkin, Wifiphisher]"
                        5 -> "Risk: HIGH | CVE-2020-24588. FragAttacks (Fragmentation and Aggregation Attacks) exploit design flaws in the Wi-Fi standard. An attacker can inject malicious L2 frames into a protected network by exploiting how the protocol handles fragmented packets, potentially bypassing firewalls and exfiltrating data from internal LAN segments. [Tools: fragattacks (research-scripts)]"
                        else -> ""
                    }
                    DeviceType.LTE, DeviceType.FIVE_G -> when(position) {
                        0 -> "Risk: CRITICAL. A Stingray (IMSI Catcher) simulates a legitimate cell tower with a stronger signal. It forces nearby mobile devices to downgrade to insecure protocols (like GSM), allowing for real-time interception of unencrypted voice calls, SMS messages (including 2FA codes), and precise hardware-level GPS tracking of the subscriber. [Tools: BladeRF, Ettus USRP, srsRAN]"
                        1 -> "Risk: HIGH. Silent SMS (Type 0) messages are invisible to the user but acknowledged by the mobile hardware. By sending a burst of these 'stealth pings,' an attacker can confirm a target is active on the network and use signal triangulation (MLAT) across multiple towers to pinpoint the device's location within meters. [Tools: Sms77, NowSMS, Kali Linux]"
                        2 -> "Risk: CRITICAL. SS7/Diameter Intercept exploits the global signaling system used for roaming. Attackers with access to an SS7 gateway can remotely redirect calls, intercept SMS for bank account takeovers, and track the real-time global location of any phone number by querying the Home Location Register (HLR). [Tools: SigPloit, SS7 Exploit Kits]"
                        3 -> "Risk: CRITICAL | CVE-2023-24033. This Baseband Remote Code Execution vulnerability allows an attacker to compromise the modem processor by sending specifically crafted radio packets over the air. Since the baseband has direct memory access (DMA) to the main CPU, this often leads to a full, undetectable device takeover without any user interaction. [Tools: Firmwire, SDR-injection]"
                        4 -> "Risk: HIGH. Cipher Downgrade attacks force the UE (User Equipment) to use the legacy A5/1 encryption algorithm instead of modern EEA/A5/3. A5/1 can be decrypted in near real-time using pre-computed Rainbow Tables (2TB), allowing an attacker with a passive SDR to eavesdrop on all cellular communications. [Tools: Kraken, A5/1 Rainbow Tables, HackRF]"
                        else -> ""
                    }
                    DeviceType.CAR -> when(position) {
                        0 -> "Risk: CRITICAL. Relay Attacks exploit the Passive Keyless Entry and Start (PKES) system. One attacker stands near the vehicle while another stands near the owner's key fob (e.g., outside their house). They use high-gain antennas to 'relay' the low-frequency challenge-response, tricking the car into thinking the key is present inside the cabin. [Tools: Proxmark3, HackRF One, custom LF-antennas]"
                        1 -> "Risk: HIGH | CVE-2017-14932. CAN Bus Injection involves injecting malicious frames into the vehicle's internal Controller Area Network. Once the gateway is bypassed (via OBD-II or external wiring), an attacker can take control of safety-critical systems including the Electronic Power Steering (EPS), Braking System, and Engine Control Unit (ECU). [Tools: CANBus Triple, SavvyCAN, socketcan]"
                        2 -> "Risk: HIGH. RollJam is a 'Code Grabber' attack that defeats rolling-code security. It jams the car's receiver while sniffing the unlock code. When the user presses it again, it sniffs the second and replays the first. The attacker is left with a valid, unused second code that can be used to unlock the vehicle at any time later. [Tools: Yard Stick One, RFCat, HackRF]"
                        3 -> "Risk: MEDIUM | CVE-2016-1000326. TPMS Spoofing involves broadcasting spoofed 315MHz/433MHz signals to the Tire Pressure Monitoring System. An attacker can trigger 'critically low pressure' alerts or system failures on the dashboard, potentially causing a driver to pull over in a predetermined 'kill zone' for carjacking or theft. [Tools: HackRF, rtl_433, GNU Radio]"
                        4 -> "Risk: MEDIUM. GPS Jamming/Spoofing uses SDRs to drown out or manipulate GNSS signals. Jamming prevents the vehicle's anti-theft telematics (like OnStar or LoJack) from reporting its location during a theft. Spoofing can be used to silently redirect autonomous or semi-autonomous vehicles by providing fake coordinates. [Tools: GPS-SDR-SIM, BladeRF, Jammer-Pro]"
                        5 -> "Risk: HIGH | CVE-2020-11500. Infotainment RCE targets vulnerabilities in the car's multimedia system (often running Linux or QNX). Compromising the browser or Bluetooth stack allows an attacker to pivot to the V-CAN (Vehicle CAN) or exfiltrate private data like call history, contact lists, and integrated GPS home locations. [Tools: Metasploit, ADB, custom QNX-exploit-scripts]"
                        else -> ""
                    }
                    DeviceType.ESCOOTER -> when(position) {
                        0 -> "Risk: CRITICAL | CVE-2019-10887. This vulnerability in the Xiaomi M365 (and clones) allows an attacker to connect to the scooter's BLE interface without a password. They can then send unauthorized commands to lock the wheels mid-ride, deploy the brakes, or permanently brick the controller via a malicious firmware update. [Tools: M365 DownG, Ninebot-Flasher]"
                        1 -> "Risk: HIGH. Firmware Downgrade attacks involve flashing an older, vulnerable version of the scooter's OS. Many manufacturers use signed firmware but fail to implement 'anti-rollback' counters. Once downgraded, an attacker can exploit patched vulnerabilities to gain root access to the motor controller. [Tools: ScooterHacking Utility, ST-Link]"
                        2 -> "Risk: MEDIUM. Speed Limit Override involves using UDS (Unified Diagnostic Services) over the BLE link to modify the scooter's internal configuration. Attackers can remove factory safety limiters, increasing the top speed beyond legal and hardware-safe limits, which can lead to catastrophic motor failure or rider injury. [Tools: nRF Connect, custom Python scripts]"
                        3 -> "Risk: MEDIUM. A DoS Advertising Flood involves saturating the BLE advertisement channels (37, 38, 39). This prevents the owner's smartphone from discovering or establishing a connection with the scooter, effectively locking the owner out of their own vehicle's digital dashboard and control features. [Tools: ESP32-BLE-Payload, mdk4]"
                        4 -> "Risk: HIGH. Battery BMS Intercept targets the communication between the Battery Management System and the ESC. By spoofing I2C/SMBus packets, an attacker can cause the scooter to enter 'Limp Mode,' report 0% charge, or shut down the high-voltage relay while the scooter is moving at high speeds. [Tools: Arduino, Logic Analyzer]"
                        5 -> "Risk: LOW. Inertial Sensor Spoofing uses resonant ultrasonic frequencies to vibrate the MEMS gyroscope and accelerometer. This can trick the scooter's stabilization algorithms or theft-detection logic, causing the motor to cut out or triggering a continuous, un-silenceable alarm. [Tools: Signal Generator, Ultrasonic Transducer]"
                        else -> ""
                    }
                    DeviceType.AIRCRAFT -> when(position) {
                        0 -> "Risk: CRITICAL. ADS-B Ghosting uses a 1090MHz SDR to broadcast fake Automatic Dependent Surveillance-Broadcast messages. An attacker can create 'phantom' aircraft on the Traffic Collision Avoidance System (TCAS) of real planes and on ATC radar screens, leading to dangerous evasive maneuvers or grounding of flights. [Tools: HackRF, adsb-sender, GNU Radio]"
                        1 -> "Risk: HIGH. Mode S Interrogation involves sending pulses that trigger a transponder response. An attacker can map out the airspace by identifying every aircraft's unique 24-bit ICAO address, current altitude, and Flight ID, enabling targeted tracking and intelligence gathering on private or government flights. [Tools: RTL-SDR, dump1090, PlanePlotter]"
                        2 -> "Risk: CRITICAL. TCAS Resolution Inhibit involves spoofing 'Traffic Advisory' and 'Resolution Advisory' messages. By providing fake vertical separation data, an attacker can trick a pilot into climbing or descending into the path of another aircraft, bypassing the automated safety systems designed to prevent mid-air collisions. [Tools: USRP, custom FPGA-SDR]"
                        3 -> "Risk: HIGH. ACARS Injection targets the Aircraft Communications Addressing and Reporting System. An attacker can inject fake VHF/Satcom messages to the Flight Management System (FMS), potentially providing false weather data, altered gate assignments, or misleading system fault reports to the flight crew. [Tools: AcarsDec, Airspy, acars-send]"
                        4 -> "Risk: MEDIUM. Squawk Manipulation exploits the lack of authentication in transponder codes. An attacker can broadcast signals that 'Squawk' emergency codes like 7500 (Hijack), 7600 (Radio Failure), or 7700 (Emergency), causing chaos in ATC sectors and triggering unnecessary scrambles of interceptor aircraft. [Tools: BladeRF, x-plane-sdr-bridge]"
                        5 -> "Risk: CRITICAL. GPS Spoofing for aircraft involves broadcasting high-power GNSS signals that mimic satellite constellations. By slowly shifting the time-of-flight data, an attacker can 'drag' an aircraft off its flight path. This is particularly dangerous during GPS-guided approaches (RNAV) in low-visibility conditions. [Tools: GPS-SDR-SIM, HackRF, Portapack]"
                        else -> ""
                    }
                    DeviceType.BLE -> when(position) {
                        0 -> "Risk: MEDIUM. BlueHydra is a sophisticated discovery tool that uses Ubertooth hardware to track both discoverable and non-discoverable Bluetooth devices. It can fingerprint device manufacturers, identify active services, and track the physical movement of a target by analyzing the RSSI and BLE advertisement patterns. [Tools: BlueHydra, Ubertooth One]"
                        1 -> "Risk: HIGH. GATTacker is a Man-in-the-Middle framework for BLE. It allows an attacker to clone a peripheral device and intercept the connection from the legitimate central app. The attacker can then view and modify GATT attributes (like heart rate, unlock codes, or sensor data) in real-time without either side noticing. [Tools: GATTacker, BLESlow]"
                        2 -> "Risk: CRITICAL | CVE-2019-9506. The KNOB (Key Negotiation of Bluetooth) attack exploits a flaw in the Bluetooth specification that allows two devices to negotiate an encryption key with as little as 1 byte (8 bits) of entropy. An attacker can then brute-force the key in seconds and decrypt all subsequent traffic. [Tools: Internal-Blue, Cybergibbons-KNOB-tool]"
                        3 -> "Risk: HIGH | CVE-2020-10135. BIAS (Bluetooth Is Any Safe) exploits flaws in the secure connection establishment. It allows an attacker to impersonate a previously paired device during the authentication phase, bypassing the need for the shared Long Term Key (LTK) and establishing a trusted, encrypted link. [Tools: BIAS-toolkit, Scapy]"
                        4 -> "Risk: CRITICAL | CVE-2020-0022. BlueFrag is a zero-click Remote Code Execution vulnerability in the Android Bluetooth stack (8.0 to 9.0). By sending specifically crafted L2CAP fragments, an attacker within range can execute arbitrary code with the privileges of the Bluetooth process, leading to full device compromise. [Tools: BlueFrag-exploit-script, Python]"
                        5 -> "Risk: CRITICAL | CVE-2020-12351. BleedingTooth is a set of zero-click vulnerabilities in the Linux kernel's BlueZ stack. It allows an unauthenticated attacker within range to gain kernel-level code execution on any Linux-based system (including servers and IoT devices) by sending malicious L2CAP packets. [Tools: BleedingTooth-POC, Google Security Research scripts]"
                        else -> ""
                    }
                    DeviceType.TV -> when(position) {
                        0 -> "Risk: HIGH. HID Remote Hijack exploits unauthenticated Bluetooth HID profiles. An attacker can 'pair' a virtual keyboard to the TV and send keystrokes to open the browser, download malicious apps, or change DNS settings to redirect the TV's traffic to an attacker-controlled server. [Tools: BTTester, nRF Connect, custom HID-injector]"
                        1 -> "Risk: MEDIUM | CVE-2020-12695. CallStranger is a vulnerability in the UPnP/SSDP protocol used by Smart TVs. It allows an attacker to use the TV as a proxy for a Distributed Denial of Service (DDoS) attack or to scan the internal home network, bypassing traditional firewall protections. [Tools: CallStranger-checker, Mirai-variants]"
                        2 -> "Risk: MEDIUM. DLNA Media Injection allows anyone on the local network to force the TV to display images or play videos. Attackers can use this to display 'Ransomware' messages, show disturbing content, or trick the user into scanning a malicious QR code displayed on the screen. [Tools: BubbleUPnP, Coherence-DLNA]"
                        3 -> "Risk: HIGH. HbbTV Privacy Leak exploits the Hybrid Broadcast Broadband TV standard. Malicious code injected into a broadcast signal (or a local network injector) can force the TV to exfiltrate the user's viewing history, IP address, and browser cookies to a remote tracking server. [Tools: HbbTV-test-suite, custom Red-Button-exploit]"
                        4 -> "Risk: HIGH. Miracast Overlay involves a Man-in-the-Middle attack on a screen-mirroring session. The attacker intercepts the WiFi-Direct stream and overlays their own content (like a fake login prompt or a 'system update' message) on top of what the user is currently seeing on the TV screen. [Tools: Wi-Fi Direct Toolset, Miracast-Player]"
                        5 -> "Risk: CRITICAL | CVE-2018-4094. Browser RCE targets vulnerabilities in the TV's often-outdated WebKit or Chromium-based browser. By tricking the user into visiting a malicious website, an attacker can execute code that escapes the browser sandbox and gains control over the TV's operating system. [Tools: Metasploit, WebKit-exploit-kit]"
                        else -> ""
                    }
                    DeviceType.COMPUTER -> when(position) {
                        0 -> "Risk: HIGH | CVE-2016-1937. MouseJack targets 2.4GHz wireless mice and keyboards. An attacker uses an SDR or a $15 USB dongle to inject unencrypted keystrokes into the victim's computer from up to 100 meters away. This allows the attacker to open a terminal, download a payload, and take full control of the machine. [Tools: JackIt, Bastille-MouseJack, Crazyradio PA]"
                        1 -> "Risk: HIGH | CVE-2019-13054. Logitacker is a specialized attack against Logitech Unifying receivers. It can sniff the encryption key of a wireless keyboard over the air or force-pair a new 'phantom' keyboard to the victim's machine, enabling remote command execution even if the user isn't currently using their mouse. [Tools: Logitacker-firmware, Nordic nRF52840]"
                        2 -> "Risk: CRITICAL | CVE-2017-1000251. BlueBorne is a devastating vulnerability that allows an attacker to take over a computer (Windows, Linux, macOS) via Bluetooth without any user interaction. It exploits memory corruption in the L2CAP and BNEP layers of the Bluetooth stack to gain RCE privileges. [Tools: BlueBorne-POC, Armis-scanner]"
                        3 -> "Risk: CRITICAL. SMB Relay involves intercepting an NTLM authentication request on the local network (via LLMNR/NetBIOS poisoning). The attacker 'relays' the hash to another server on the same network, allowing them to log in as the victim and access files, databases, or administrative shells. [Tools: Responder, ntlmrelayx, Impacket]"
                        4 -> "Risk: HIGH | CVE-2018-0886. RDP Force Downgrade exploits the CredSSP vulnerability. By forcing the Remote Desktop connection to use a weaker version of the protocol, an attacker can perform a Man-in-the-Middle attack, intercepting the user's password and the entire RDP session data. [Tools: Seth, PyRDP, Bettercap]"
                        5 -> "Risk: MEDIUM. WiFi Direct Tunneling exploits the secondary WiFi interface used for features like 'Project to this PC.' An attacker can establish a hidden, high-speed connection to the computer that bypasses Windows Firewall and corporate EDR, creating a perfect tunnel for data exfiltration. [Tools: Wifite2, custom C2-tunnel-scripts]"
                        else -> ""
                    }
                    DeviceType.SMARTPHONE -> when(position) {
                        0 -> "Risk: HIGH | CVE-2019-8641. AirDrop Spoofing exploits the AWDL protocol. An attacker can send a specially crafted 'vCard' or image that triggers a buffer overflow in the sharing daemon. This can lead to remote code execution (RCE) on the iPhone without the user even clicking 'Accept' on the transfer. [Tools: OpenDrop, Owl-project, AirDrop-Pwn]"
                        1 -> "Risk: MEDIUM. Contact Beam targets the NFC/Bluetooth 'Tap to Share' feature. By using a high-power NFC reader near the phone (e.g., in a pocket), an attacker can trigger an automatic 'Contact Exchange' that sends the user's private phone number and email address to the attacker's device. [Tools: Proxmark3, Flipper Zero, NFC-Kill]"
                        2 -> "Risk: HIGH | CVE-2021-36768. Nearby Share Intercept targets the Android peer-to-peer sharing protocol. An attacker can spoof a 'Trusted Contact' and intercept files being sent between two devices, or modify the file in transit to include a malicious payload or APK that the victim then installs. [Tools: Nearby-Pwn, Scapy-Nearby-Share]"
                        3 -> "Risk: CRITICAL | CVE-2021-30860. FORCEDENTRY is a zero-click iMessage exploit. It uses a malicious PDF disguised as a .gif file to exploit a vulnerability in the CoreGraphics rendering engine. This allows an attacker to bypass the 'BlastDoor' sandbox and gain full access to the microphone, camera, and messages. [Tools: NSO-Group-Pegasus-Framework, Citizen-Lab-Analysis-Tools]"
                        4 -> "Risk: CRITICAL | CVE-2015-3824. Stagefright Reborn targets the Android MediaServer. By sending a crafted MMS with a malicious MP4 file, an attacker can trigger a heap overflow that executes code as soon as the phone starts generating a notification thumbnail, requiring zero user interaction. [Tools: Metasploit-Stagefright-module, Python-MMS-sender]"
                        5 -> "Risk: HIGH | CVE-2019-16256. SIMjacker exploits the S@T Browser (SIM Tool Kit) found on many SIM cards. An attacker sends a binary SMS that forces the SIM card to execute 'Proactive Commands,' allowing them to silently track the phone's location, send spoofed SMS, and make outgoing calls. [Tools: Simjacker-tester, binary-SMS-gateway]"
                        else -> ""
                    }
                    DeviceType.PAGER -> when(position) {
                        0 -> "Risk: MEDIUM. POCSAG Intercept involves using an SDR to capture and decode unencrypted pager transmissions on 138-174MHz, 406-512MHz, or 900MHz bands. Since most paging protocols lack encryption, an attacker can read all alphanumeric messages sent to medical, industrial, or government pagers in real-time. [Tools: RTL-SDR, multimon-ng, GQRX]"
                        1 -> "Risk: HIGH. FLEX Decryption targets the Motorola FLEX paging protocol. While some systems use basic encoding, many are completely unencrypted. Intercepting these signals allows for mass harvesting of private data, including patient names, diagnostic codes, and industrial telemetry. [Tools: SDR#, PDW, GNURadio]"
                        2 -> "Risk: CRITICAL. Message Injection involves broadcasting spoofed POCSAG/FLEX signals to a pager's specific address (RIC/CapCode). An attacker can send fake emergency alerts, evacuation orders, or malicious instructions to field personnel, potentially causing widespread panic or operational disruption. [Tools: HackRF, gr-mixalot, rpitx]"
                        3 -> "Risk: MEDIUM. Replay Attacks involve capturing a valid pager transmission and re-broadcasting it at a later time. This can be used to re-trigger automated systems or to confuse personnel by repeating old instructions, such as 'System OK' or 'Maintenance Required' signals. [Tools: Portapack H2, HackRF One]"
                        4 -> "Risk: HIGH. Address Spoofing exploits the lack of source authentication in paging networks. An attacker can impersonate a central dispatch console and send messages that appear to come from a trusted authority, tricking the recipient into revealing sensitive information or performing unauthorized actions. [Tools: BladeRF, custom Python-SDR-scripts]"
                        else -> ""
                    }
                    else -> "Detailed risk analysis for this vector is pending manual evaluation. This device exhibits signals consistent with non-standard RF signatures, requiring further packet-level inspection."
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        
        scanner.startScanning()
        startBlink()
        startBeeping()
        startAlarmNeonAnimation()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupImmersiveMode()
        }
    }

    private fun setupImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    private fun updateThemeColors() {
        when (currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> {
                GREEN = Color.BLACK
                CYAN = Color.BLACK
                AMBER = Color.BLACK
                PINK = Color.BLACK
            }
            RadarView.Theme.RED_NIGHT -> {
                GREEN = Color.RED
                CYAN = Color.RED
                AMBER = Color.RED
                PINK = Color.RED
            }
            RadarView.Theme.DEFAULT -> {
                GREEN = Color.parseColor("#00FF41")
                CYAN = Color.parseColor("#00FFFF")
                AMBER = Color.parseColor("#FFB300")
                PINK = Color.parseColor("#FF00FF")
            }
            RadarView.Theme.PINK -> {
                GREEN = Color.parseColor("#FF00FF")
                CYAN = Color.parseColor("#FF1493")
                AMBER = Color.parseColor("#FF69B4")
                PINK = Color.parseColor("#FF00FF")
            }
            RadarView.Theme.NEON -> {
                GREEN = Color.parseColor("#E6FB04")
                CYAN = Color.parseColor("#E6FB04")
                AMBER = Color.parseColor("#E6FB04")
                PINK = Color.parseColor("#E6FB04")
            }
            RadarView.Theme.NARANJA -> {
                GREEN = Color.parseColor("#FF8C00")
                CYAN = Color.parseColor("#FF8C00")
                AMBER = Color.parseColor("#FF8C00")
                PINK = Color.parseColor("#FF8C00")
            }
            RadarView.Theme.BUBBLEGUM -> {
                GREEN = Color.parseColor("#FF00FF") // Neon Magenta
                CYAN = Color.parseColor("#00FDFF") // Turquoise
                AMBER = Color.parseColor("#00FDFF")
                PINK = Color.parseColor("#FF00FF")
            }
            RadarView.Theme.SUMMERTIME -> {
                GREEN = Color.parseColor("#ff9f6b")
                CYAN = Color.parseColor("#6befff")
                AMBER = Color.parseColor("#6befff")
                PINK = Color.parseColor("#ff9f6b")
            }
            RadarView.Theme.MORIO -> {
                GREEN = Color.parseColor("#c3ac3a")
                CYAN = Color.parseColor("#c8f29e")
                AMBER = Color.parseColor("#c8f29e")
                PINK = Color.parseColor("#c8f29e")
            }
        }
    }

    private fun applyThemeToUI() {
        when (currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> {
                binding.root.setBackgroundColor(Color.WHITE)
                val textColor = Color.BLACK
                binding.tvTitle.setTextColor(textColor)
                binding.tvType.setTextColor(textColor)
                binding.tvName.setTextColor(textColor)
                binding.tvAddress.setTextColor(textColor)
                binding.tvRssi.setTextColor(textColor)
                binding.tvDist.setTextColor(textColor)
                binding.tvExtra1.setTextColor(textColor)
                binding.tvExtra2.setTextColor(textColor)
                binding.tvExtra3.setTextColor(textColor)
                binding.tvExtra4.setTextColor(textColor)
                binding.tvExtra5.setTextColor(textColor)
                binding.tvExtra6.setTextColor(textColor)
                binding.tvFastPair.setTextColor(textColor)
                binding.tvSelectExploitLabel.setTextColor(textColor)
                binding.spExploits.setBackgroundResource(R.drawable.btn_bg_white_dim)
                binding.tvExploitDesc.setBackgroundColor(Color.parseColor("#F0F0F0"))
                binding.tvExploitDesc.setTextColor(textColor)
                
                binding.btnBack.setBackgroundResource(R.drawable.status_box_bg_white_pink)
                binding.btnBack.setTextColor(Color.BLACK)
            }
            RadarView.Theme.RED_NIGHT -> {
                binding.root.setBackgroundColor(Color.BLACK)
                val textColor = Color.RED
                binding.tvTitle.setTextColor(textColor)
                binding.tvType.setTextColor(textColor)
                binding.tvName.setTextColor(textColor)
                binding.tvAddress.setTextColor(Color.parseColor("#990000"))
                binding.tvRssi.setTextColor(Color.parseColor("#990000"))
                binding.tvDist.setTextColor(textColor)
                binding.tvExtra1.setTextColor(Color.parseColor("#990000"))
                binding.tvExtra2.setTextColor(Color.parseColor("#990000"))
                binding.tvExtra3.setTextColor(Color.parseColor("#990000"))
                binding.tvExtra4.setTextColor(Color.parseColor("#990000"))
                binding.tvExtra5.setTextColor(Color.parseColor("#990000"))
                binding.tvExtra6.setTextColor(Color.parseColor("#990000"))
                binding.tvFastPair.setTextColor(Color.parseColor("#990000"))
                binding.tvSelectExploitLabel.setTextColor(textColor)
                binding.spExploits.setBackgroundResource(R.drawable.btn_bg_red)
                binding.tvExploitDesc.setBackgroundColor(Color.parseColor("#1A0000"))
                binding.tvExploitDesc.setTextColor(Color.parseColor("#990000"))
            }
            RadarView.Theme.DEFAULT -> {
                binding.root.setBackgroundColor(Color.BLACK)
                // Default XML colors are mostly fine, but let's be explicit
                binding.tvTitle.setTextColor(GREEN)
                binding.tvType.setTextColor(GREEN)
                binding.tvName.setTextColor(GREEN)
                binding.tvAddress.setTextColor(Color.parseColor("#00AA2A"))
                binding.tvRssi.setTextColor(Color.parseColor("#00AA2A"))
                binding.tvDist.setTextColor(Color.parseColor("#00AA2A"))
                binding.tvSelectExploitLabel.setTextColor(GREEN)
                binding.spExploits.setBackgroundResource(R.drawable.btn_bg_dim)
                binding.tvExploitDesc.setBackgroundColor(Color.parseColor("#1AFFFFFF"))
                binding.tvExploitDesc.setTextColor(Color.parseColor("#888888"))
            }
            RadarView.Theme.PINK -> {
                binding.root.setBackgroundColor(Color.BLACK)
                val textColor = Color.parseColor("#FF00FF")
                binding.tvTitle.setTextColor(textColor)
                binding.tvType.setTextColor(textColor)
                binding.tvName.setTextColor(textColor)
                binding.tvAddress.setTextColor(Color.parseColor("#990099"))
                binding.tvRssi.setTextColor(Color.parseColor("#990099"))
                binding.tvDist.setTextColor(Color.parseColor("#990099"))
                binding.tvSelectExploitLabel.setTextColor(textColor)
                binding.spExploits.setBackgroundResource(R.drawable.btn_bg_dim)
                binding.tvExploitDesc.setBackgroundColor(Color.parseColor("#1A001A"))
                binding.tvExploitDesc.setTextColor(Color.parseColor("#990099"))
            }
            RadarView.Theme.NEON -> {
                binding.root.setBackgroundColor(Color.BLACK)
                val textColor = Color.parseColor("#E6FB04")
                binding.tvTitle.setTextColor(textColor)
                binding.tvType.setTextColor(textColor)
                binding.tvName.setTextColor(textColor)
                binding.tvAddress.setTextColor(Color.parseColor("#B3C403"))
                binding.tvRssi.setTextColor(Color.parseColor("#B3C403"))
                binding.tvDist.setTextColor(Color.parseColor("#B3C403"))
                binding.tvSelectExploitLabel.setTextColor(textColor)
                binding.spExploits.setBackgroundResource(R.drawable.btn_bg_neon)
                binding.tvExploitDesc.setBackgroundColor(Color.parseColor("#1A1A00"))
                binding.tvExploitDesc.setTextColor(Color.parseColor("#B3C403"))
            }
            RadarView.Theme.NARANJA -> {
                binding.root.setBackgroundColor(Color.BLACK)
                val textColor = Color.parseColor("#FF8C00")
                binding.tvTitle.setTextColor(textColor)
                binding.tvType.setTextColor(textColor)
                binding.tvName.setTextColor(textColor)
                binding.tvAddress.setTextColor(Color.parseColor("#995400"))
                binding.tvRssi.setTextColor(Color.parseColor("#995400"))
                binding.tvDist.setTextColor(Color.parseColor("#995400"))
                binding.tvSelectExploitLabel.setTextColor(textColor)
                binding.spExploits.setBackgroundResource(R.drawable.btn_bg_naranja)
                binding.tvExploitDesc.setBackgroundColor(Color.parseColor("#1A0F00"))
                binding.tvExploitDesc.setTextColor(Color.parseColor("#995400"))
            }
            RadarView.Theme.BUBBLEGUM -> {
                binding.root.setBackgroundColor(Color.BLACK)
                val textColor = Color.parseColor("#FF00FF") // Neon Magenta
                val accentColor = Color.parseColor("#00FDFF") // Turquoise
                binding.tvTitle.setTextColor(textColor)
                binding.tvType.setTextColor(textColor)
                binding.tvName.setTextColor(textColor)
                binding.tvAddress.setTextColor(accentColor)
                binding.tvRssi.setTextColor(accentColor)
                binding.tvDist.setTextColor(accentColor)
                binding.tvSelectExploitLabel.setTextColor(textColor)
                binding.spExploits.setBackgroundResource(R.drawable.btn_bg_bubblegum)
                binding.tvExploitDesc.setBackgroundColor(Color.parseColor("#1A001A"))
                binding.tvExploitDesc.setTextColor(accentColor)
            }
            RadarView.Theme.SUMMERTIME -> {
                binding.root.setBackgroundColor(Color.BLACK)
                val textColor = Color.parseColor("#ff9f6b") // Peach
                val accentColor = Color.parseColor("#6befff") // Cyan
                val secondaryColor = Color.parseColor("#996befff") // Dim Cyan

                binding.tvTitle.setTextColor(textColor)
                binding.tvType.setTextColor(textColor)
                binding.tvName.setTextColor(textColor)
                binding.tvAddress.setTextColor(secondaryColor)
                binding.tvRssi.setTextColor(secondaryColor)
                binding.tvDist.setTextColor(textColor)

                binding.tvExtra1.setTextColor(secondaryColor)
                binding.tvExtra2.setTextColor(secondaryColor)
                binding.tvExtra3.setTextColor(secondaryColor)
                binding.tvExtra4.setTextColor(secondaryColor)
                binding.tvExtra5.setTextColor(secondaryColor)
                binding.tvExtra6.setTextColor(secondaryColor)
                binding.tvFastPair.setTextColor(secondaryColor)

                binding.tvSelectExploitLabel.setTextColor(textColor)
                binding.spExploits.setBackgroundResource(R.drawable.btn_bg_summertime)
                binding.tvExploitDesc.setBackgroundColor(Color.parseColor("#2A1F1A"))
                binding.tvExploitDesc.setTextColor(accentColor)
            }
            RadarView.Theme.MORIO -> {
                binding.root.setBackgroundColor(Color.BLACK)
                val textColor = Color.parseColor("#c3ac3a") // Main Green
                val accentColor = Color.parseColor("#c8f29e") // Cyanish accent
                val secondaryColor = Color.parseColor("#99c8f29e") // Dim Cyan

                binding.tvTitle.setTextColor(textColor)
                binding.tvType.setTextColor(textColor)
                binding.tvName.setTextColor(textColor)
                binding.tvAddress.setTextColor(secondaryColor)
                binding.tvRssi.setTextColor(secondaryColor)
                binding.tvDist.setTextColor(textColor)

                binding.tvExtra1.setTextColor(secondaryColor)
                binding.tvExtra2.setTextColor(secondaryColor)
                binding.tvExtra3.setTextColor(secondaryColor)
                binding.tvExtra4.setTextColor(secondaryColor)
                binding.tvExtra5.setTextColor(secondaryColor)
                binding.tvExtra6.setTextColor(secondaryColor)
                binding.tvFastPair.setTextColor(secondaryColor)

                binding.tvSelectExploitLabel.setTextColor(textColor)
                binding.spExploits.setBackgroundResource(R.drawable.btn_bg_morio)
                binding.tvExploitDesc.setBackgroundColor(Color.parseColor("#244f48"))
                binding.tvExploitDesc.setTextColor(accentColor)
            }
        }
    }

    private fun startAlarmNeonAnimation() {
        if (currentTheme == RadarView.Theme.HIGH_CONTRAST) return // Skip glow in white themes
        val anim = android.animation.ValueAnimator.ofFloat(2f, 12f)
        anim.duration = 1500
        anim.repeatCount = android.animation.ValueAnimator.INFINITE
        anim.repeatMode = android.animation.ValueAnimator.REVERSE
        anim.addUpdateListener { valueAnimator ->
            val radius = valueAnimator.animatedValue as Float
            val glowColor = PINK
            binding.btnAlarm.setShadowLayer(radius, 0f, 0f, glowColor)
        }
        anim.start()
    }

    private fun updateAlarmButton() {
        val enabled = scanner.isAlarmEnabled(currentDevice.id)
        val action = if (enabled) "OFF" else "ON"
        val text = "► 01 ALARM: $action"
        val spannable = android.text.SpannableString(text)
        
        val protocolIdx = text.indexOf("► 01")
        val alarmIdx = text.indexOf("ALARM")
        val statusIdx = text.indexOf(": ") + 2

        val labelColor = when(currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> Color.BLACK
            RadarView.Theme.RED_NIGHT -> Color.parseColor("#990000")
            RadarView.Theme.PINK -> Color.parseColor("#990099")
            RadarView.Theme.NEON -> Color.parseColor("#B3C403")
            RadarView.Theme.NARANJA -> Color.parseColor("#995400")
            RadarView.Theme.BUBBLEGUM -> Color.parseColor("#00FDFF")
            RadarView.Theme.SUMMERTIME -> Color.parseColor("#6befff")
            RadarView.Theme.MORIO -> Color.parseColor("#c8f29e")
            else -> Color.parseColor("#00AA2A")
        }

        if (protocolIdx != -1) {
            spannable.setSpan(android.text.style.ForegroundColorSpan(GREEN), protocolIdx, protocolIdx + 4, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (alarmIdx != -1) {
            spannable.setSpan(android.text.style.ForegroundColorSpan(labelColor), alarmIdx, alarmIdx + 5, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (statusIdx >= 2) {
            val statusColor = if (enabled) PINK else labelColor
            spannable.setSpan(android.text.style.ForegroundColorSpan(statusColor), statusIdx, text.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        binding.btnAlarm.text = spannable
        applyGlow(binding.btnAlarm, enabled)
    }

    private fun updateSoundButton() {
        val status = if (isSoundEnabled) "ON" else "OFF"
        val text = "► 02 SOUND: $status"
        val spannable = android.text.SpannableString(text)
        
        val protocolIdx = text.indexOf("► 02")
        val labelColor = when(currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> Color.BLACK
            RadarView.Theme.RED_NIGHT -> Color.parseColor("#990000")
            RadarView.Theme.PINK -> Color.parseColor("#990099")
            RadarView.Theme.NEON -> Color.parseColor("#B3C403")
            RadarView.Theme.NARANJA -> Color.parseColor("#995400")
            RadarView.Theme.BUBBLEGUM -> Color.parseColor("#00FDFF")
            RadarView.Theme.SUMMERTIME -> Color.parseColor("#6befff")
            RadarView.Theme.MORIO -> Color.parseColor("#c8f29e")
            else -> Color.parseColor("#00AA2A")
        }

        if (protocolIdx != -1) {
            spannable.setSpan(android.text.style.ForegroundColorSpan(GREEN), protocolIdx, protocolIdx + 4, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        val soundLabelIdx = text.indexOf("SOUND: ")
        if (soundLabelIdx != -1) {
            spannable.setSpan(android.text.style.ForegroundColorSpan(labelColor), soundLabelIdx, soundLabelIdx + 7, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            val statusIdx = soundLabelIdx + 7
            val statusColor = if (isSoundEnabled) PINK else labelColor
            spannable.setSpan(android.text.style.ForegroundColorSpan(statusColor), statusIdx, text.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        binding.btnSound.text = spannable
        applyGlow(binding.btnSound, isSoundEnabled)
    }

    private fun applyGlow(button: View, active: Boolean) {
        buttonAnimators[button]?.cancel()
        if (active) {
            val pvhX = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.05f)
            val pvhY = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.05f)
            val anim = android.animation.ObjectAnimator.ofPropertyValuesHolder(button, pvhX, pvhY).apply {
                duration = 1000
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.REVERSE
                start()
            }
            buttonAnimators[button] = anim
            if (button is Button) {
                val bgRes = when(currentTheme) {
                    RadarView.Theme.HIGH_CONTRAST -> R.drawable.btn_bg_white
                    RadarView.Theme.RED_NIGHT -> R.drawable.btn_bg_red
                    RadarView.Theme.PINK -> R.drawable.btn_bg_pink
                    RadarView.Theme.NEON -> R.drawable.btn_bg_neon
                    RadarView.Theme.NARANJA -> R.drawable.btn_bg_naranja
                    RadarView.Theme.BUBBLEGUM -> R.drawable.btn_bg_bubblegum
                    RadarView.Theme.SUMMERTIME -> R.drawable.btn_bg_summertime
                    RadarView.Theme.MORIO -> R.drawable.btn_bg_morio
                    RadarView.Theme.DEFAULT -> R.drawable.btn_bg
                }
                button.setBackgroundResource(bgRes)
                if (button.id != binding.btnSound.id && button.id != binding.btnAlarm.id) {
                    val glowColor = PINK
                    setCyberText(button, button.text.toString(), glowColor)
                }
            }
        } else {
            button.scaleX = 1.0f
            button.scaleY = 1.0f
            if (button is Button) {
                val bgRes = when(currentTheme) {
                    RadarView.Theme.HIGH_CONTRAST -> R.drawable.btn_bg_white_dim
                    RadarView.Theme.RED_NIGHT -> R.drawable.btn_bg_red
                    RadarView.Theme.PINK -> R.drawable.btn_bg_dim
                    RadarView.Theme.NEON -> R.drawable.btn_bg_dim
                    RadarView.Theme.NARANJA -> R.drawable.btn_bg_dim
                    RadarView.Theme.BUBBLEGUM -> R.drawable.btn_bg_dim
                    RadarView.Theme.SUMMERTIME -> R.drawable.btn_bg_summertime_dim
                    RadarView.Theme.MORIO -> R.drawable.btn_bg_morio_dim
                    RadarView.Theme.DEFAULT -> R.drawable.btn_bg_dim
                }
                button.setBackgroundResource(bgRes)
                if (button.id != binding.btnSound.id && button.id != binding.btnAlarm.id) {
                    val color = when (button.id) {
                        binding.btnAlarm.id -> when(currentTheme) {
                            RadarView.Theme.HIGH_CONTRAST -> Color.BLACK
                            RadarView.Theme.RED_NIGHT -> Color.parseColor("#990000")
                            RadarView.Theme.PINK -> Color.parseColor("#990099")
                            RadarView.Theme.NEON -> Color.parseColor("#B3C403")
                            RadarView.Theme.NARANJA -> Color.parseColor("#995400")
                            RadarView.Theme.BUBBLEGUM -> Color.parseColor("#00FDFF")
                            RadarView.Theme.SUMMERTIME -> Color.parseColor("#6befff")
                            RadarView.Theme.MORIO -> Color.parseColor("#c8f29e")
                            else -> Color.parseColor("#00AA2A")
                        }
                        else -> GREEN
                    }
                    setCyberText(button, button.text.toString(), color)
                }
            }
            buttonAnimators.remove(button)
        }
    }

    private fun setCyberText(button: Button, text: String, contentColor: Int) {
        val spannable = android.text.SpannableString(text)
        
        val protocolIdx = text.indexOf("►")
        
        if (protocolIdx != -1) {
            spannable.setSpan(android.text.style.ForegroundColorSpan(GREEN), protocolIdx, protocolIdx + 4, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(android.text.style.ForegroundColorSpan(contentColor), protocolIdx + 4, text.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            button.setTextColor(contentColor)
            button.text = text
            return
        }
        button.text = spannable
    }

    private fun startBeeping() {
        handler.post(object : Runnable {
            override fun run() {
                if (isSoundEnabled) {
                    val dist = currentDevice.distanceMeters
                    // Beep faster when closer: interval between 100ms and 2000ms
                    val interval = (dist * 100).toLong().coerceIn(100, 2000)
                    
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
                    handler.postDelayed(this, interval)
                } else {
                    handler.postDelayed(this, 500)
                }
            }
        })
    }

    override fun onDevicesUpdated(devices: List<ScanDevice>) {
        val updated = devices.find { it.id == currentDevice.id }
        if (updated != null) {
            currentDevice = updated
            runOnUiThread { displayDevice(currentDevice) }
        }
    }

    override fun onScanStatusChanged(scanning: Boolean) {}
    override fun onLocationUpdated(lat: Double, lon: Double) {}
    override fun onMovementDetected(device: ScanDevice) {
        if (device.id == currentDevice.id) {
            runOnUiThread {
                binding.tvTitle.text = "!! MOVEMENT DETECTED !!"
                binding.tvTitle.setTextColor(Color.RED)
            }
        }
    }
    override fun onError(msg: String) {}

    private fun getDeviceColor(type: DeviceType): Int {
        return when (currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> Color.BLACK
            RadarView.Theme.RED_NIGHT -> Color.RED
            RadarView.Theme.PINK -> when (type) {
                DeviceType.WIFI, DeviceType.PAGER -> GREEN
                DeviceType.AIRCRAFT, DeviceType.DRONE -> CYAN
                DeviceType.LTE, DeviceType.FIVE_G -> AMBER
                DeviceType.CAMERA -> Color.RED
                else -> PINK
            }
            RadarView.Theme.NEON -> when (type) {
                DeviceType.WIFI, DeviceType.PAGER -> GREEN
                DeviceType.AIRCRAFT, DeviceType.DRONE -> GREEN
                DeviceType.LTE, DeviceType.FIVE_G -> GREEN
                DeviceType.CAMERA -> Color.RED
                else -> GREEN
            }
            RadarView.Theme.NARANJA -> when (type) {
                DeviceType.WIFI, DeviceType.PAGER -> GREEN
                DeviceType.AIRCRAFT, DeviceType.DRONE -> GREEN
                DeviceType.LTE, DeviceType.FIVE_G -> GREEN
                DeviceType.CAMERA -> Color.RED
                else -> GREEN
            }
            RadarView.Theme.BUBBLEGUM -> when (type) {
                DeviceType.WIFI, DeviceType.PAGER -> GREEN
                DeviceType.AIRCRAFT, DeviceType.DRONE -> CYAN
                DeviceType.LTE, DeviceType.FIVE_G -> AMBER
                DeviceType.CAMERA -> Color.RED
                else -> PINK
            }
            RadarView.Theme.SUMMERTIME -> when (type) {
                DeviceType.WIFI, DeviceType.PAGER -> Color.parseColor("#ff9f6b") // Peach
                DeviceType.AIRCRAFT, DeviceType.DRONE -> Color.parseColor("#6befff") // Cyan
                DeviceType.LTE, DeviceType.FIVE_G -> Color.parseColor("#6befff") // Cyan
                DeviceType.CAMERA -> Color.RED
                else -> Color.parseColor("#ff9f6b") // Peach
            }
            RadarView.Theme.MORIO -> when (type) {
                DeviceType.WIFI, DeviceType.PAGER -> Color.parseColor("#c3ac3a") // Main Green
                DeviceType.AIRCRAFT, DeviceType.DRONE -> Color.parseColor("#c8f29e") // Cyan
                DeviceType.LTE, DeviceType.FIVE_G -> Color.parseColor("#c8f29e") // Cyan
                DeviceType.CAMERA -> Color.RED
                else -> Color.parseColor("#c3ac3a") // Main Green
            }
            RadarView.Theme.DEFAULT -> when (type) {
                DeviceType.WIFI, DeviceType.PAGER -> GREEN
                DeviceType.AIRCRAFT, DeviceType.DRONE -> CYAN
                DeviceType.LTE, DeviceType.FIVE_G -> AMBER
                DeviceType.CAMERA -> Color.RED
                else -> PINK
            }
        }
    }

    private fun displayDevice(d: ScanDevice) {
        val color = getDeviceColor(d.type)

        binding.tvType.text    = "▸ TYPE    : ${d.typeLabel}"
        binding.tvType.setTextColor(color)
        binding.tvName.text    = "▸ NAME    : ${d.displayName}"
        binding.tvAddress.text = "▸ ADDRESS : ${d.address}"
        binding.tvRssi.text    = "▸ RSSI    : ${d.rssi} dBm  [${d.signalStrength}]"
        
        val label = "▸ DIST    : "
        val value = d.distanceLabel
        val fullText = label + value
        val spannable = android.text.SpannableString(fullText)
        
        // Label color
        val distLabelColor = GREEN
        spannable.setSpan(
            android.text.style.ForegroundColorSpan(distLabelColor),
            0, label.length,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        // Value color (Blended Green to Pink as distance decreases)
        val ratio = (d.distanceMeters / 15.0).coerceIn(0.0, 1.0).toFloat()
        val farColor = GREEN
        val nearColor = PINK
        val blendedColor = ColorUtils.blendARGB(nearColor, farColor, ratio)
        spannable.setSpan(
            android.text.style.ForegroundColorSpan(blendedColor),
            label.length, fullText.length,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.tvDist.text = spannable

        if (d.type == DeviceType.WIFI) {
            binding.tvExtra1.text = "▸ SSID    : ${d.ssid}"
            binding.tvExtra2.text = "▸ FREQ    : ${d.frequency} MHz  (Ch ${d.channel})"
            binding.tvExtra3.text = "▸ SECURITY: ${parseSecurityShort(d.capabilities)}"
            binding.tvExtra4.text = "▸ SEEN    : ${d.seenCount} TIMES"
            
            val lastSeenSec = (System.currentTimeMillis() - d.lastSeen) / 1000
            binding.tvExtra5.text = "▸ UPDATED : ${lastSeenSec}s AGO"
            
            binding.tvExtra1.visibility = View.VISIBLE
            binding.tvExtra2.visibility = View.VISIBLE
            binding.tvExtra3.visibility = View.VISIBLE
            binding.tvExtra4.visibility = View.VISIBLE
            binding.tvExtra5.visibility = View.VISIBLE
            binding.tvExtra6.visibility = View.GONE
        } else if (d.type == DeviceType.AIRCRAFT) {
            binding.tvExtra1.text = "▸ ALTITUDE: ${d.altitude?.let { "%.0f m".format(it * 0.3048f) } ?: "---"}"
            binding.tvExtra2.text = "▸ SPEED   : ${d.speed?.let { "%.0f km/h".format(it * 1.852f) } ?: "---"}"
            binding.tvExtra3.text = "▸ HEADING : ${d.heading?.let { "%.0f°".format(it) } ?: "---"}"
            binding.tvExtra4.text = "▸ COUNTRY : ${if (d.country.isNullOrBlank() || d.country == "UNKNOWN") "---" else d.country}"
            binding.tvExtra5.text = "▸ ORIGIN  : ${if (d.origin.isNullOrBlank() || d.origin == "UNKNOWN") "---" else d.origin}"
            binding.tvFastPair.text = "▸ DEST    : ${if (d.destination.isNullOrBlank() || d.destination == "UNKNOWN") "---" else d.destination}"
            
            val lastSeenSec = (System.currentTimeMillis() - d.lastSeen) / 1000
            binding.tvExtra6.text = "▸ UPDATED : ${lastSeenSec}s AGO"
            
            binding.tvExtra1.visibility = View.VISIBLE
            binding.tvExtra2.visibility = View.VISIBLE
            binding.tvExtra3.visibility = View.VISIBLE
            binding.tvExtra4.visibility = View.VISIBLE
            binding.tvExtra5.visibility = View.VISIBLE
            binding.tvExtra6.visibility = View.VISIBLE
            binding.tvFastPair.visibility = View.VISIBLE
        } else if (d.type == DeviceType.LTE || d.type == DeviceType.FIVE_G) {
            binding.tvExtra1.text = "▸ MCC/MNC : ${d.mcc ?: "---"} / ${d.mnc ?: "---"}"
            binding.tvExtra2.text = "▸ LAC/TAC : ${d.lac ?: "---"}"
            binding.tvExtra3.text = "▸ CELL ID : ${d.cid ?: "---"}"
            binding.tvExtra4.text = "▸ PCI     : ${d.pci ?: "---"}"
            binding.tvExtra5.text = "▸ ARFCN   : ${d.arfcn ?: "---"} ${if (d.band != null) "(${d.band})" else ""}"
            
            val lastSeenSec = (System.currentTimeMillis() - d.lastSeen) / 1000
            binding.tvExtra6.text = "▸ UPDATED : ${lastSeenSec}s AGO"

            binding.tvExtra1.visibility = View.VISIBLE
            binding.tvExtra2.visibility = View.VISIBLE
            binding.tvExtra3.visibility = View.VISIBLE
            binding.tvExtra4.visibility = View.VISIBLE
            binding.tvExtra5.visibility = View.VISIBLE
            binding.tvExtra6.visibility = View.VISIBLE
            binding.tvFastPair.visibility = View.GONE
        } else if (d.type == DeviceType.CAMERA) {
            binding.tvExtra1.text = "▸ CAM TYPE: ${d.cameraType?.uppercase() ?: "FIXED"}"
            binding.tvExtra2.text = "▸ OPERATOR: ${d.manufacturer.ifBlank { "Unknown" }}"
            binding.tvExtra3.text = "▸ SOURCE  : OpenStreetMap"
            binding.tvExtra4.text = "▸ LAT     : %.6f".format(d.lat ?: 0.0)
            binding.tvExtra5.text = "▸ LON     : %.6f".format(d.lon ?: 0.0)

            val lastSec = (System.currentTimeMillis() - d.lastSeen) / 1000
            binding.tvExtra6.text = "▸ UPDATED : ${lastSec}s AGO"

            binding.tvExtra1.visibility = View.VISIBLE
            binding.tvExtra2.visibility = View.VISIBLE
            binding.tvExtra3.visibility = View.VISIBLE
            binding.tvExtra4.visibility = View.VISIBLE
            binding.tvExtra5.visibility = View.VISIBLE
            binding.tvExtra6.visibility = View.VISIBLE
            binding.tvFastPair.visibility = View.GONE
        } else {
            binding.tvExtra1.text = "▸ MFR ID  : ${d.manufacturer.ifBlank { "Unknown" }}"
            binding.tvExtra2.text = "▸ UUIDS   : ${d.uuids.ifBlank { "None" }}"
            
            val lastSeenSec = (System.currentTimeMillis() - d.lastSeen) / 1000
            binding.tvExtra3.text = "▸ SEEN    : ${d.seenCount} TIMES"
            binding.tvExtra4.text = "▸ UPDATED : ${lastSeenSec}s AGO"
            
            binding.tvExtra1.visibility = View.VISIBLE
            binding.tvExtra2.visibility = View.VISIBLE
            binding.tvExtra3.visibility = View.VISIBLE
            binding.tvExtra4.visibility = View.VISIBLE
            binding.tvExtra5.visibility = View.GONE
            binding.tvExtra6.visibility = View.GONE
            binding.tvFastPair.visibility = View.GONE
        }

        // Vulnerabilities are handled by the exploit dropdown, no need for redundant text
        // binding.tvFastPair.visibility = if (d.type == DeviceType.AIRCRAFT) View.VISIBLE else View.GONE
        // if (d.type != DeviceType.AIRCRAFT) binding.tvExtra6.visibility = View.GONE
        // binding.tvExtra3.visibility = View.GONE

        // ESL Tag Tools Visibility
        binding.btnEsl.visibility = if (d.type == DeviceType.BLE) View.VISIBLE else View.GONE

        binding.tvTitle.text      = "TARGET ACQUIRED"
        val titleColor = PINK
        binding.tvTitle.setTextColor(titleColor)
        
        // Pulsate animation for target acquired
        binding.tvTitle.clearAnimation()
        val anim = android.view.animation.AlphaAnimation(0.3f, 1.0f).apply {
            duration = 500
            repeatMode = android.view.animation.Animation.REVERSE
            repeatCount = android.view.animation.Animation.INFINITE
        }
        binding.tvTitle.startAnimation(anim)
    }

    private fun parseSecurityShort(caps: String) = when {
        caps.contains("WPA3") -> "WPA3"
        caps.contains("WPA2") -> "WPA2"
        caps.contains("WPA")  -> "WPA"
        caps.contains("WEP")  -> "WEP"
        else                  -> "OPEN"
    }

    private fun startBlink() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                binding.tvTitle.alpha = if (blinkOn) 1f else 0.4f
                blinkOn = !blinkOn
                handler.postDelayed(this, 700)
            }
        }, 700)
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner.removeListener(this)
        handler.removeCallbacksAndMessages(null)
    }
}
