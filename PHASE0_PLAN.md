# Phase 0 — Feasibility Spikes Plan

> **Status:** PLANNED
> **Prerequisite:** 2 real Android phones (different brands), ≥ 1 Bluetooth helmet headset

---

## Overview

Phase 0 is mandatory. These spikes must run on **real devices** before architecture decisions marked `ADR` are finalized. If a spike disproves an assumption, we **stop and report with data** rather than silently substituting another approach.

---

## Spike A: Nearby Connections Range & Transport Characteristics

### Test Procedure
1. Establish Nearby Connections (`P2P_POINT_TO_POINT`, `BYTES` payload) between two phones
2. Walk/drive apart measuring at 10m intervals:
   - Connection state (still connected Y/N)
   - Payload delivery latency (ping-pong timestamp)
   - Throughput (flood 100-byte payloads, measure rate)
   - Head-of-line blocking behavior (add artificial delay on one side, measure latency growth)
3. Repeat with `STREAM` payload type
4. Test both static (standing) and moving (walking/car) scenarios
5. Record GPS track alongside all measurements

### Measurements to Record
- Discovery time (seconds from `startDiscovery` to connection)
- Max range before disconnect (metres, with GPS)
- RTT at various distances (10m, 25m, 50m, 100m, 150m, 200m)
- Throughput (packets/sec, bytes/sec) at each distance
- HOL blocking: does latency grow when signal weakens?
- `BYTES` vs `STREAM` comparison table
- Does the connection survive momentary obstructions (trees, buildings)?
- Battery consumption during 15-minute sustained session

### Decision Output
- Go/no-go for `LocalNearbyTransport` as v1 primary
- `BYTES` vs `STREAM` recommendation
- Realistic range expectations for documentation

---

## Spike B: Wi-Fi Direct Raw UDP

### Test Procedure
1. Establish Wi-Fi Direct group (`WifiP2pManager`)
2. Force 2.4 GHz band (`groupOwnerBand`)
3. Open raw UDP socket between phones
4. Measure same metrics as Spike A
5. Compare range, latency, and reliability vs Nearby Connections

### Decision Output
- Go/contingent/no-go for `LocalWifiDirectTransport`
- Comparison table vs Nearby Connections
- Is the extra complexity (manual socket management, GO negotiation) justified?

---

## Spike C: Wi-Fi 2.4 GHz + Bluetooth SCO Coexistence

### Test Procedure
1. Connect helmet headset via HFP/SCO
2. Establish local Wi-Fi link (Nearby or Direct)
3. Run full-duplex audio (capture via SCO mic, play via SCO speaker)
4. Measure:
   - Audio quality: glitches, dropouts, latency
   - Wi-Fi throughput during SCO
   - SCO stability during Wi-Fi activity
5. Repeat with 5 GHz Wi-Fi (if available via Wi-Fi Direct)
6. Test on at least 2 different phone brands

### Measurements to Record
- Glitch count per minute (2.4 GHz vs 5 GHz)
- Audio latency increase when Wi-Fi is active
- Does SCO disconnect under heavy Wi-Fi load?
- Battery impact of concurrent Wi-Fi + SCO

### Decision Output
- 2.4 GHz vs 5 GHz recommendation
- `AudioPolicy` coexistence defaults

---

## Spike D: Bluetooth HFP/SCO Behavior

### Test Procedure
1. Pair helmet headset with phone
2. Set `MODE_IN_COMMUNICATION`, start SCO
3. Measure:
   - SCO establishment time
   - Audio latency (mic → ear via loopback recording)
   - mSBC vs CVSD detection and quality comparison
   - A2DP suspension: does music stop? Can it be routed separately?
   - Media button behavior
   - Reconnect behavior after intentional disconnect
4. Test `setCommunicationDevice()` (API 31+) vs `startBluetoothSco()` (API 29-30)
5. Test with multiple headset types (proprietary intercom, generic HFP, LE Audio if available)

### Measurements to Record
- SCO setup time (seconds)
- Mouth-to-ear latency via SCO (expected 100-250ms)
- mSBC availability and negotiated codec
- A2DP behavior during SCO
- Media button → mute toggle reliability
- Reconnect time after helmet power-cycle

### Decision Output
- Latency baseline for NFR-1 targets
- Audio Mode viability (Voice-first vs Music-first)
- Per-device quirks for `DEVICES.md`

---

## Spike E: ADR-1A vs ADR-1B Audio Architecture

### Test Procedure

**ADR-1A (Unified pipeline + DataChannel):**
1. Implement minimal: Mic → Opus encode → DataChannel (unordered, maxRetransmits=0) → decode → speaker
2. Measure latency, quality, PLC behavior
3. Add simulated loss/jitter (tc netem or app-level random drop)

**ADR-1B (WebRTC native audio track for Internet):**
1. Use WebRTC's standard addTrack audio
2. Measure same metrics
3. Test switching between the two paths at the mixer level

### Measurements to Record
- Latency (P50, P90) for both architectures
- Audio quality under 1%, 3%, 5%, 10% loss
- PLC effectiveness (gap detection in test tone)
- Implementation complexity assessment (hours to complete)

### Decision Output
- **ADR-1 decision:** A or B (or hybrid)
- Justification with data

---

## Spike F: WebRTC DataChannel Audio Under Degradation

### Test Procedure
1. Set up WebRTC PeerConnection with DataChannel (unordered, unreliable)
2. Send 20ms Opus frames as binary messages
3. Use `tc netem` or network conditioner to simulate:
   - 50ms, 100ms, 200ms, 400ms latency
   - 1%, 3%, 5%, 10%, 20% random loss
   - 20ms, 50ms, 100ms jitter
4. Measure received audio quality at each condition
5. Compare against WebRTC native audio track under same conditions

### Decision Output
- DataChannel viability threshold (max acceptable loss/latency)
- Comparison data for ADR-1 decision

---

## Spike G: Microphone FGS Start Rules

### Test Procedure
1. On Android 14+ (latest): attempt to start mic FGS from various states:
   - User taps button (foreground) ✓
   - From a bound service ✓/✗?
   - From a high-priority FCM push ✗ (expected)
   - From CompanionDeviceManager presence observation ✗/? 
   - From a boot receiver ✗ (expected)
2. Test on target OEMs across all major Android manufacturers
3. Test Doze behavior with active mic FGS:
   - `adb shell dumpsys deviceidle force-idle`
   - Does audio continue? Does network stay?

### Measurements to Record
- Which start contexts succeed per API level per OEM
- FGS type requirements per Android version
- Doze behavior (network availability, CPU scheduling)

### Decision Output
- Confirmed FGS start requirements for session flow
- CompanionDeviceManager viability
- OEM-specific onboarding requirements

---

## Spike H: Self-Managed Telecom Call

### Test Procedure
1. Register `ConnectionService` with `MANAGE_OWN_CALLS`
2. Test:
   - Does it improve Bluetooth routing reliability?
   - Process priority change (check OOM score)
   - Interaction with incoming cellular calls (hold/swap)
   - OEM-specific background behavior
3. Compare battery/stability with and without

### Decision Output
- Go/no-go for self-managed Telecom approach
- Benefits and risks documented

---

## Test Devices Required

| Device | Android Version | Purpose |
|--------|----------------|---------|
| Pixel 7/8/9 | Android 14/15 | Reference device |
| Modern Tier 1 OEM series | Android 13+ | Popular OEM, OEM battery specifics |
| Xiaomi/Redmi | Android 12+ | Aggressive battery management |
| Oppo/Realme | Android 12+ | ColorOS specifics |

## Helmet Headsets Required

| Type | Purpose |
|------|---------|
| Cardo/Sena (proprietary intercom) | Real-world target headset |
| Generic HFP Bluetooth | Baseline HFP behavior |
| LE Audio headset (if available) | Future-proofing |

---

## Deliverable

`PHASE0_REPORT.md` with:
- All measurements in tables
- Go/no-go per spike
- ADR-1 final decision with justification
- Revised targets for NFR-1 latency based on real measurements
- Known device quirks
- Revised architecture decisions if any assumptions were disproved

---

## Timeline Estimate

| Spike | Estimated Duration |
|-------|-------------------|
| A (Nearby range) | 2-3 days |
| B (Wi-Fi Direct) | 1-2 days |
| C (Wi-Fi + SCO coexistence) | 1 day |
| D (Bluetooth HFP) | 1-2 days |
| E (ADR-1A vs 1B) | 2-3 days |
| F (DataChannel audio) | 1-2 days |
| G (FGS start rules) | 1 day |
| H (Telecom call) | 1 day |
| **Total** | **~10-15 days** |
