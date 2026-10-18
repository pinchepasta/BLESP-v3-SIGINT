package com.espsdr.jammer.jammer

data class RefTarget(
    val group: String,
    val mhz: Double,
    val what: String,
    val widthMhz: Double = 0.0
)

data class Reach(
    val harm: Int,
    val label: String
)

val MODES = listOf("noise", "chirp", "cw", "pulse")

const val LO_MIN = 1817.0
const val LO_MAX = 2790.0
const val LO_TRY_MIN = 1514.0

fun reach(t: Double): Reach {
    val h = if (t <= LO_MAX) 1 else if (t / 2.0 <= LO_MAX) 2 else 3
    val lo = t / h
    return when {
        lo > LO_MAX || lo < LO_TRY_MIN -> Reach(0, "out of range")
        lo < LO_MIN -> Reach(h, "marginal")
        h == 1 -> Reach(1, "direct")
        h == 2 -> Reach(2, "2nd harm")
        else -> Reach(3, "3rd harm")
    }
}

val REF_TABLE = listOf(
    // 2.4 GHz ISM 2400–2483.5 — licence-free worldwide
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2441.0, "ULTRA — full 2.4 GHz band cover (2400–2483.5 MHz)", 100.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2402.0, "BLE advertising ch37 · BT inquiry · nRF24 ch2", 2.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2405.0, "Zigbee / 802.15.4 ch11", 2.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2412.0, "Wi-Fi ch1 (US/EU non-overlapping 1·6·11)", 22.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2417.0, "Wi-Fi ch2", 22.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2422.0, "Wi-Fi ch3", 22.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2426.0, "BLE advertising ch38 · Zigbee ch15", 2.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2427.0, "Wi-Fi ch4", 22.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2432.0, "Wi-Fi ch5", 22.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2437.0, "Wi-Fi ch6 — most common AP default", 22.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2442.0, "Wi-Fi ch7", 22.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2447.0, "Wi-Fi ch8", 22.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2450.0, "Wi-Fi ch9 · Zigbee ch20 · microwave ovens", 22.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2452.0, "Wi-Fi ch10", 22.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2457.0, "Wi-Fi ch11", 22.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2462.0, "Wi-Fi ch11 centre · Zigbee ch22", 22.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2467.0, "Wi-Fi ch12 (EU/JP)", 22.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2472.0, "Wi-Fi ch13 (EU/JP)", 22.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2475.0, "Zigbee ch25", 2.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2480.0, "BLE adv ch39 · BT paging · Zigbee ch26", 2.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2484.0, "Wi-Fi ch14 (Japan only)", 22.0),
    RefTarget("2.4 GHz ISM 2400–2483.5 — licence-free worldwide", 2441.0, "whole ISM band, one LO at 2441", 83.0),

    // 2.4 GHz, outside ISM
    RefTarget("2.4 GHz, outside ISM", 2320.0, "TD-LTE band 40 / amateur 13 cm", 20.0),
    RefTarget("2.4 GHz, outside ISM", 2338.0, "SiriusXM satellite radio", 12.0),
    RefTarget("2.4 GHz, outside ISM", 2535.0, "LTE band 7 uplink", 70.0),
    RefTarget("2.4 GHz, outside ISM", 2595.0, "LTE band 38 TDD", 50.0),
    RefTarget("2.4 GHz, outside ISM", 2655.0, "LTE band 7 downlink / band 41", 70.0),

    // Sub-2.4 GHz and cellular
    RefTarget("Sub-2.4 GHz and cellular", 1575.42, "GPS L1 · Galileo E1 · BeiDou B1 (marginal)", 4.0),
    RefTarget("Sub-2.4 GHz and cellular", 1616.5, "Iridium (marginal)", 10.0),
    RefTarget("Sub-2.4 GHz and cellular", 1840.0, "GSM1800 / LTE band 3 downlink", 75.0),
    RefTarget("Sub-2.4 GHz and cellular", 1880.0, "GSM1900 / PCS / LTE band 2 downlink", 60.0),
    RefTarget("Sub-2.4 GHz and cellular", 1890.0, "DECT cordless phones (EU)", 20.0),
    RefTarget("Sub-2.4 GHz and cellular", 1960.0, "UMTS / LTE band 2 downlink", 60.0),
    RefTarget("Sub-2.4 GHz and cellular", 2140.0, "UMTS / LTE band 1 downlink", 60.0),

    // 5 GHz Wi-Fi and friends
    RefTarget("5 GHz Wi-Fi and friends", 5180.0, "Wi-Fi ch36 · UNII-1 (indoor)", 20.0),
    RefTarget("5 GHz Wi-Fi and friends", 5200.0, "Wi-Fi ch40 · UNII-1", 20.0),
    RefTarget("5 GHz Wi-Fi and friends", 5220.0, "Wi-Fi ch44 · UNII-1", 20.0),
    RefTarget("5 GHz Wi-Fi and friends", 5240.0, "Wi-Fi ch48 · UNII-1", 20.0),
    RefTarget("5 GHz Wi-Fi and friends", 5200.0, "whole UNII-1 5150–5250, one LO at 5200", 100.0),
    RefTarget("5 GHz Wi-Fi and friends", 5260.0, "Wi-Fi ch52 · UNII-2A (DFS)", 20.0),
    RefTarget("5 GHz Wi-Fi and friends", 5280.0, "Wi-Fi ch56 · UNII-2A (DFS)", 20.0),
    RefTarget("5 GHz Wi-Fi and friends", 5300.0, "Wi-Fi ch60 · UNII-2A (DFS)", 20.0),
    RefTarget("5 GHz Wi-Fi and friends", 5320.0, "Wi-Fi ch64 · UNII-2A (DFS)", 20.0),
    RefTarget("5 GHz Wi-Fi and friends", 5410.0, "U-NII-2B — weather and military radar", 120.0),
    RefTarget("5 GHz Wi-Fi and friends", 5500.0, "Wi-Fi ch100 · UNII-2C (DFS)", 20.0),
    RefTarget("5 GHz Wi-Fi and friends", 5580.0, "Wi-Fi ch116 · UNII-2C (DFS)", 20.0),
    RefTarget("5 GHz Wi-Fi and friends", 5660.0, "Wi-Fi ch132 · UNII-2C (DFS)", 20.0),
    RefTarget("5 GHz Wi-Fi and friends", 5700.0, "Wi-Fi ch140 · UNII-2C (DFS)", 20.0),
    RefTarget("5 GHz Wi-Fi and friends", 5720.0, "Wi-Fi ch144 · UNII-2C (DFS)", 20.0),
    RefTarget("5 GHz Wi-Fi and friends", 5745.0, "Wi-Fi ch149 · UNII-3", 20.0),
    RefTarget("5 GHz Wi-Fi and friends", 5765.0, "Wi-Fi ch153 · UNII-3", 20.0),
    RefTarget("5 GHz Wi-Fi and friends", 5785.0, "Wi-Fi ch157 · UNII-3", 20.0),
    RefTarget("5 GHz Wi-Fi and friends", 5805.0, "Wi-Fi ch161 · UNII-3", 20.0),
    RefTarget("5 GHz Wi-Fi and friends", 5825.0, "Wi-Fi ch165 · UNII-3", 20.0),
    RefTarget("5 GHz Wi-Fi and friends", 5785.0, "whole UNII-3 / 5.8 ISM 5725–5850, one LO at 5785", 125.0),
    RefTarget("5 GHz Wi-Fi and friends", 5880.0, "U-NII-4 (US) · DSRC 5895 · C-V2X 5905", 75.0),
    RefTarget("5 GHz Wi-Fi and friends", 5795.0, "analog FPV video and wireless senders 5645–5945", 300.0),

    // 6 GHz Wi-Fi 6E / 7
    RefTarget("6 GHz Wi-Fi 6E / 7", 5955.0, "Wi-Fi 6E ch1", 20.0),
    RefTarget("6 GHz Wi-Fi 6E / 7", 6135.0, "Wi-Fi 6E ch37", 20.0),
    RefTarget("6 GHz Wi-Fi 6E / 7", 6315.0, "Wi-Fi 6E ch73", 20.0),
    RefTarget("6 GHz Wi-Fi 6E / 7", 6495.0, "Wi-Fi 6E ch109", 20.0),
    RefTarget("6 GHz Wi-Fi 6E / 7", 6675.0, "Wi-Fi 6E ch145", 20.0),
    RefTarget("6 GHz Wi-Fi 6E / 7", 6855.0, "Wi-Fi 6E ch181", 20.0),
    RefTarget("6 GHz Wi-Fi 6E / 7", 7035.0, "Wi-Fi 6E ch217", 20.0),
    RefTarget("6 GHz Wi-Fi 6E / 7", 7115.0, "Wi-Fi 6E ch233 (last)", 20.0),
    RefTarget("6 GHz Wi-Fi 6E / 7", 6175.0, "U-NII-5 5925–6425 — also the C-band satellite uplink", 500.0),
    RefTarget("6 GHz Wi-Fi 6E / 7", 6475.0, "U-NII-6 6425–6525", 100.0),
    RefTarget("6 GHz Wi-Fi 6E / 7", 6700.0, "U-NII-7 6525–6875", 350.0),
    RefTarget("6 GHz Wi-Fi 6E / 7", 7000.0, "U-NII-8 6875–7125", 250.0),

    // Below the LO window
    RefTarget("Below the LO window", 433.92, "ISM 433 — remotes, sensors, TPMS", 1.7),
    RefTarget("Below the LO window", 868.0, "ISM 868 (EU) — LoRa, smart meters", 1.0),
    RefTarget("Below the LO window", 915.0, "ISM 915 (US) — LoRa, ISM", 26.0),
    RefTarget("Below the LO window", 1090.0, "ADS-B / Mode-S", 2.0),
    RefTarget("Below the LO window", 1227.6, "GPS L2", 20.0)
)