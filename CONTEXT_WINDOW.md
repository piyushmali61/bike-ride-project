# Context Window — Bike Ride Project (AstraRide)

> **Read this first.** This file is the shared memory for everyone working on this repo — people
> and AI assistants (Claude, Copilot, Gemini, Cursor, …). Before changing anything, read it.
> After a meaningful change, **update this file in the same commit** so nobody has a memory gap.

- **Repo:** https://github.com/piyushmali61/bike-ride-project
- **Local path (Aayush):** `C:\Users\aayus\Downloads\bike-ride-project`
- **Current version:** 1.5.4 (versionCode 10) — bump both in `app/build.gradle.kts` on every release
- **Last updated:** 2026-10-01

---

## 1. Update rules (must follow)

1. **Always update the existing codebase in parallel** — build on what is there.
2. **The existing codebase is the first priority.**
3. **Do not replace or unnecessarily rewrite existing functionality.**
4. **Do not touch unrelated code or features.**
5. `git pull` before you start; teammates push often.
6. Never show fake data as real (no placeholder riders, made-up rider counts, demo messages).
7. Anything that alerts the whole convoy (SOS, horn) must ask for confirmation in the UI and send **once**.
8. Update this file (sections 4–6) when you add, change or remove a feature.

---

## 2. What the app is

AstraRide is an Android motorcycle **convoy intercom + offline messaging** app (Kotlin, Jetpack Compose, Hilt).

- **Voice intercom:** full-duplex voice between riders over **Google Nearby** and **Hotspot/Wi-Fi UDP**. Voice relays through riders in the middle (up to 4 hops).
- **Convoy Mesh Chat:** messages, SOS, locations, photos hop phone-to-phone over **Bluetooth LE** (no internet), and sync over free public **Nostr relays** when any rider has data. No server of our own.
- **Hands-free:** wave over the phone or double-tap the riding screen to mute; say **"SOS" twice** for an alarm (offline Vosk speech, model downloaded once ~40 MB).
- **Spoken announcements:** incoming alerts/locations/photos are read aloud (TTS).
- **Map / navigation / Picture Stop** (added by Piyush in `feature/ui/nav/`).

---

## 3. Code map

| Module | What lives there |
|---|---|
| `app/` | `MainActivity`, manifest, release config (ARM-only ABIs, ProGuard rules for Vosk/JNA) |
| `feature/ui/` | All Compose screens: `HomeScreen`, `ChatScreen`, `RidingHudOverlay`, `ProfileDialog`, `RideMapScreen`, `nav/*`; view models `IntercomViewModel`, `ChatViewModel`; `MeshAnnouncer` |
| `service/` | `IntercomService` — foreground service that runs a ride (mic, speaker, transports, Voice SOS) |
| `engine/audio/` | `AudioCaptureEngine` / `AudioPlaybackEngine` (each owns its own thread), `SpeechCommandSpotter` (Vosk Voice SOS), `VoiceAnnouncer` (TTS), horn, mute feedback |
| `transport/local-nearby/` | `NearbyMeshTransport` (Nearby Connections), `LocalLanTransport` (Hotspot UDP port 50005), `VoiceFrame` (voice packet + dedup) |
| `transport/mesh/` | `ConvoyMesh` (chat brain), `BleMeshLink` (BLE GATT mesh), `NostrLink` + `Schnorr` (internet relays), `MeshRouter` (dedup/TTL/store-and-forward), `RoomCipher` (AES-GCM per room), `MessageStore`, `LocationHelper` |
| `bluetooth/` | `AudioRouteManager` (speaker / earpiece / helmet) |

**Wire formats are shared between phones — never renumber or reorder:**
`MeshType` codes (1 CHAT … 7 DESTINATION), the 42-byte mesh packet header, the voice frame header (`senderId:4 seq:2 ttl:1 + 640 B PCM`). All riders must run compatible versions.

---

## 4. Feature status

| Feature | Status |
|---|---|
| Voice intercom (Nearby + Hotspot), voice relay, mixer | ✅ Works — two-way verified on emulator with a simulated 2nd rider |
| Mesh Chat over internet (Nostr) | ✅ Verified (send, receive, history sync, photos) |
| Mesh Chat over Bluetooth LE (offline, multi-hop) | ⚠️ Built + unit-tested, **not yet tested on real phones** |
| Voice SOS ("SOS" twice) | ✅ Engine verified (19/19 on synthesized speech); ⚠️ not tested with a real voice in wind |
| Read-aloud announcements | ✅ |
| Riding HUD: double-tap mute, Slow down / Hi / Stop / Pit stop, Horn, Location, Speaker | ✅ |
| Live HUD chooser: **with Map** (`RideMapScreen`) or **HUD only** (`RidingHudOverlay`); both show the shared `ChannelQuietCard` voice-activity card | ✅ |
| Mesh Chat panel: SOS + Location always visible; alerts Hide/Show; More (8 extra alerts) | ✅ |
| My Rooms (riders add their own rooms in the Room dialog) | ✅ |
| Permanent delete of messages / convoy history | ✅ |
| Map, navigation, Picture Stop, bike profile | ✅ (Piyush) |

---

## 5. Change log (newest first)

| Version | Commit | Who | What |
|---|---|---|---|
| 1.5.4 | (this commit) | Aayush | Map locate/zoom buttons sit in a row just above the bottom panel (they overlapped the voice card when a destination was set) |
| 1.5.3 | (this commit) | Aayush | Live HUD chooser (with Map / HUD only); `ChannelQuietCard` moved to `components/` and shown in both HUDs; map SOS now asks for confirmation; HUD shows the selected room before a ride |
| 1.5.2 | `87379b0` | Aayush | Floating SOS spacing fixed + confirm dialog (it sent twice, no confirm); My Rooms; collapsible Mesh Chat alerts + More; no fake room counts |
| 1.5.1 | `e518b2c` | Aayush | Old SOS from history no longer sounds the alarm on launch; removed fake riders "Vishal/Unnati"; deletes made permanent; fixed misleading "Say MUTE" guide text |
| 1.5.0+ | `b05a899`, `cb75df4` | Piyush | Cockpit UI redesign, map/navigation, Picture Stop, delete convoy/message, bike profile, smart arrival |
| 1.5.0 | `88804d4` | Aayush | Ride-first HUD, Voice SOS (Vosk), spoken alerts, photos in mesh, Mesh Chat redesign |
| 1.4.1 | `fe822d3` | Aayush | HUD freeze fix (Nearby payloads moved off the main thread) |
| 1.4.0 | `bea861d` | Aayush | Crash fixes (double audio, mic/speaker release races, background restart); voice mesh relay |
| 1.3.0 | `8cb5bad` | Aayush | Offline Convoy Mesh Chat (BLE + Nostr), rider profiles |
| 1.2.0 and earlier | — | Aayush / Piyush | "Universal Rider Mesh" home screen redesign, original intercom |

---

## 6. Known issues / to do

1. **Signing key mismatch (important).** Aayush and Piyush build the APK on different computers, so each APK is signed with a different debug key. Phones **cannot update** from one person's APK to the other's — they must uninstall first. Fix: create **one release keystore**, share it privately (never commit it), and both build with it.
2. **Real-phone testing still needed:** BLE offline chat between 2–3 phones; voice relay A → B → C; Voice SOS with a real voice and wind.
3. **Play Store blocker:** native libs (Vosk, JNA, WebRTC) are not 16 KB page-aligned. Sideloaded APK works; Play Store submission would need updated libraries.
4. **Release APK is ARM-only** (`arm64-v8a`, `armeabi-v7a`). It will not run on x86 emulators — use the debug build there.
5. Test SOS / alerts only in a **private test room**, never CONVOY 1 — other riders' phones will alarm.

---

## 7. Build & test

```powershell
# JDK 17 (Android Studio's JBR 17); Gradle 8.11 does not run on Java 25
$env:JAVA_HOME="$env:USERPROFILE\.jdks\jbr-17.0.14"
.\gradlew.bat :transport:mesh:testDebugUnitTest :transport:local-nearby:testDebugUnitTest   # unit tests
.\gradlew.bat :app:assembleDebug     # emulator / dev build (package com.bikeride.intercom.debug)
.\gradlew.bat :app:assembleRelease   # phone build → copy to AstraRide-Intercom.apk for the README download link
```

`local.properties` (not committed) needs `sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk`.
