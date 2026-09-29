<div align="center">

# 🏍️ AstraRide — Smart Hybrid Intercom
### *Next-Generation Real-Time Vehicle-to-Vehicle Voice Mesh for Riders & Convoys*

<p align="center">
  <img src="assets/images/hero_banner.jpg" alt="AstraRide Hero Banner" width="100%" style="border-radius: 12px;" />
</p>

[![Studio](https://img.shields.io/badge/Crafted%20By-Mythic%20Bharat%20Studios-orange?style=for-the-badge&logo=android)](https://github.com/piyushmali61)
[![Platform](https://img.shields.io/badge/Platform-Android%2010%2B%20(API%2029--35)-brightgreen?style=for-the-badge&logo=android)](https://android.com)
[![License](https://img.shields.io/badge/License-Private%20Proprietary-red?style=for-the-badge)](LICENSE)
[![Status](https://img.shields.io/badge/Build-Passing%20100%25-blue?style=for-the-badge)](https://github.com/piyushmali61/bike-ride-project)

<br/>

### 📲 [DIRECT MOBILE APK DOWNLOADS](#-direct-apk-downloads)
**Get the production Android application directly on your phone:**

<a href="https://github.com/piyushmali61/bike-ride-project/releases/download/v1.0.0/AstraRide-Intercom.apk">
  <img src="https://img.shields.io/badge/DOWNLOAD%20ASTRARIDE%20INTERCOM-APK%20(117%20MB)-0284c7?style=for-the-badge&logo=android&logoColor=white" height="42" />
</a>
&nbsp;&nbsp;
<a href="https://github.com/piyushmali61/bike-ride-project/releases/download/v1.0.0/AstraRide-Spikes.apk">
  <img src="https://img.shields.io/badge/DOWNLOAD%20SPIKES%20HARNESS-APK%20(102%20MB)-10b981?style=for-the-badge&logo=speedtest&logoColor=white" height="42" />
</a>

</div>

---

## ⚡ What is AstraRide?

**AstraRide** is a high-performance, hands-free, full-duplex motorcycle & vehicle convoy smart intercom developed by **Mythic Bharat Studios**. It solves the universal problem riders face: **what happens when you ride out of local radio range?**

Traditional Bluetooth intercoms cut off when separated by >100m. Cellular phone calls drop in tunnels and incur continuous network latency. 

**AstraRide bridges both worlds through seamless dynamic handover:**
1. **OFF-GRID LOCAL LINK**: Direct peer-to-peer Wi-Fi Direct / Google Nearby Connections mesh requiring **zero internet or cell reception**.
2. **CLOUD WEBRTC BACKBONE**: Encrypted unordered WebRTC audio channel via secure signaling when distance opens between riders.
3. **ZERO-INTERRUPTION HANDOVER**: The proprietary `HandoverController` state machine automatically arbitrates the cleanest path in real time without audio drops.

---

## 📱 User Interface Preview

<div align="center">
  <img src="assets/images/app_ui_preview.jpg" alt="AstraRide Mobile UI" width="380" style="border-radius: 16px; box-shadow: 0 10px 30px rgba(0,0,0,0.8);" />
  <p><em>AstraRide OLED Dark Mode: High-contrast daytime readability & real-time mesh link telemetry.</em></p>
</div>

---

## 🛠️ Complete Technology Stack

| Layer | Technologies & Implementations |
|---|---|
| **Operating System** | Android 10+ (API 29–35), Google Play Services, JDK 21 |
| **Language & Build** | **Kotlin 2.0.20**, Android Gradle Plugin 8.7.3, KSP |
| **UI Framework** | **Jetpack Compose**, Material 3 Dark Palette (OLED high-contrast) |
| **State & Concurrency** | Reactive `StateFlow`, Kotlin Coroutines, Unidirectional Data Flow |
| **Dependency Injection** | **Google Dagger Hilt** |
| **Off-Grid Transport** | **Google Nearby Connections** (`P2P_POINT_TO_POINT`, `P2P_CLUSTER`) & raw Wi-Fi Direct UDP |
| **Cloud Transport** | **WebRTC DataChannel** (`ordered=false, maxRetransmits=0`) eliminating TCP head-of-line blocking |
| **Audio DSP** | **Opus Codec** (RFC 6716, 20ms frames, 24–32 kbps adaptive), WebRTC AEC3 (Acoustic Echo Cancellation), NS (Noise Suppression), AGC (Automatic Gain Control) |
| **Jitter Engine** | Adaptive jitter buffer + Packet Loss Concealment (PLC) smoothing RF fade |
| **Bluetooth Routing** | `AudioManager.setCommunicationDevice` (API 31+) & wideband speech (mSBC 16 kHz) targeting Cardo, Sena, and generic helmet headsets |
| **Telephony Integration** | Android `ConnectionService` (`CAPABILITY_SELF_MANAGED`) for native VoIP priority |
| **Security & Crypto** | Ed25519 digital identity + X25519 ECDH + **ChaCha20-Poly1305** AEAD frame encryption |
| **Local Storage** | Room DB (SQLite) + Jetpack DataStore |

---

## 📦 Direct APK Downloads

### 1. Download Links
* 🚀 [**AstraRide-Intercom.apk**](https://github.com/piyushmali61/bike-ride-project/releases/download/v1.0.0/AstraRide-Intercom.apk) *(117 MB)*: Complete production intercom app.
* 🧪 [**AstraRide-Spikes.apk**](https://github.com/piyushmali61/bike-ride-project/releases/download/v1.0.0/AstraRide-Spikes.apk) *(102 MB)*: Phase 0 telemetry and RF validation suite with all 8 field spikes.

### 2. Quick Install via USB (ADB)
```powershell
adb install -r "AstraRide-Intercom.apk"
adb install -r "AstraRide-Spikes.apk"
```

---

## 🧪 Phase 0 Feasibility Harness

The codebase includes an interactive 8-spike feasibility testing suite:
- **Spike A**: Nearby Connections Range, RTT, Throughput, and HOL Blocking
- **Spike B**: Raw Wi-Fi Direct UDP Sockets
- **Spike C**: Wi-Fi 2.4 GHz vs 5 GHz & Bluetooth SCO RF Coexistence
- **Spike D**: Bluetooth HFP/SCO setup latency & mSBC wideband speech detection
- **Spike E**: ADR-1A (Unified DataChannel) vs ADR-1B (Native WebRTC Track) Benchmark
- **Spike F**: WebRTC DataChannel packet loss & jitter degradation simulation
- **Spike G**: Android 14+ Microphone FGS background start rules & deep Doze sleep
- **Spike H**: Self-Managed Telecom ConnectionService VoIP priority & cellular arbitration

---

## 🏗️ Building from Source

```bash
# Clone the standalone bike-ride-project repository
git clone https://github.com/piyushmali61/bike-ride-project.git
cd bike-ride-project

# Run unit test suite (100% passing)
./gradlew test

# Build debug APKs
./gradlew :app:assembleDebug
./gradlew :spikes:assembleDebug
```

---

## 🏢 Credits & Studio

**AstraRide** is proudly conceived, engineered, and maintained by **Mythic Bharat Studios**.  
*All rights reserved.*
