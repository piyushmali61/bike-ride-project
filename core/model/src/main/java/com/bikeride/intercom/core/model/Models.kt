package com.bikeride.intercom.core.model

/**
 * Smart Intercom — Core Domain Models
 *
 * All data classes, enums, and sealed types that define the domain.
 * These are transport-agnostic, UI-agnostic, and have no platform dependencies.
 *
 * Reference: Spec Section 19 (Data Models)
 */

// ═══════════════════════════════════════════════════════════════════════════
// Transport & Connection
// ═══════════════════════════════════════════════════════════════════════════

/** Identifies a transport implementation */
enum class TransportKind {
    LOCAL_NEARBY,
    LOCAL_WIFI_DIRECT,
    INTERNET_WEBRTC,
    BT_FALLBACK
}

/** User preference for transport selection (Section 10.3) */
enum class ConnectionPreference {
    PREFER_LOCAL,
    PREFER_INTERNET,
    AUTOMATIC
}

/** Transport connection lifecycle */
enum class TransportState {
    IDLE,
    CONNECTING,
    CONNECTED,
    DEGRADED,
    FAILED
}

/** Result of a non-blocking send attempt */
enum class SendResult {
    SENT,
    DROPPED_OLDEST,  // backpressure: oldest frame dropped to make room
    QUEUE_FULL,      // should not happen if backpressure is working
    NOT_CONNECTED
}

/** Why a transport was disconnected */
enum class DisconnectReason {
    USER_REQUESTED,
    HANDOVER,
    LINK_DEAD,
    PEER_DISCONNECTED,
    AUTH_FAILURE,
    ERROR,
    SESSION_ENDED
}

// ═══════════════════════════════════════════════════════════════════════════
// Link Quality & Health (Section 10.2)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Measured link health metrics for a single transport.
 * Computed from a 3-second sliding window at 1 Hz by LinkMonitor.
 *
 * All thresholds and penalty weights are in [HandoverPolicy].
 */
data class LinkQuality(
    /** Composite score 0..100 (Section 10.2 formula) */
    val score: Int,
    /** Round-trip time in milliseconds (app-level ping) */
    val rttMs: Int? = null,
    /** Jitter in milliseconds */
    val jitterMs: Int? = null,
    /** Packet loss percentage (0.0 - 100.0) */
    val lossPct: Float? = null,
    /** Late (past playout deadline) packet percentage */
    val latePct: Float? = null,
    /** Sender-side drop percentage (backpressure) */
    val sendDropPct: Float? = null,
    /** Estimated throughput */
    val throughputKbps: Int? = null,
    /** GPS-derived distance hint; null if unknown (Section 8.3) */
    val estimatedDistanceM: Int? = null,
    /** Monotonic timestamp of measurement */
    val measuredAtMs: Long
) {
    companion object {
        val UNKNOWN = LinkQuality(score = 0, measuredAtMs = 0L)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Audio Packets (Section 7)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * A single encrypted audio frame ready for transport.
 *
 * The packet is transport-agnostic: the same bytes go over Local or Internet.
 * The receiver uses [seq] for dedup and ordering, [timestampMs] for jitter
 * buffer targeting, and [flags] for DTX/FEC/keepalive handling.
 */
data class AudioPacket(
    /** Monotonically increasing sequence number (64-bit, wraps at 2^63) */
    val seq: Long,
    /** RTP-style timestamp in media clock ticks (16 kHz → 320 per 20 ms frame) */
    val timestampMs: Int,
    /** Bitfield: DTX, FEC, keepalive, redundancy (RED) */
    val flags: Int,
    /** AEAD-encrypted Opus frame (ChaCha20-Poly1305 or AES-GCM) */
    val ciphertext: ByteArray
) {
    companion object {
        const val FLAG_DTX = 0x01       // Discontinuous transmission (silence)
        const val FLAG_FEC = 0x02       // Contains forward error correction
        const val FLAG_KEEPALIVE = 0x04 // No audio data, just a presence signal
        const val FLAG_RED = 0x08       // Contains redundancy (previous frame piggyback)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioPacket) return false
        return seq == other.seq
    }

    override fun hashCode(): Int = seq.hashCode()
}

// ═══════════════════════════════════════════════════════════════════════════
// Audio Settings & State
// ═══════════════════════════════════════════════════════════════════════════

/** Ducking level for music when voice is active (Section 14) */
enum class DuckingLevel {
    OFF,    // 0 dB
    LOW,    // -6 dB
    MEDIUM, // -12 dB
    HIGH    // -20 dB
}

/** Noise suppression level (Section 15) */
enum class NoiseSuppressionLevel {
    OFF,
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH,
    ENHANCED  // Neural NS (optional, CPU-intensive)
}

/** Audio routing state for Bluetooth headset (Section 12) */
enum class AudioRouteState {
    HELMET_OK,         // Bluetooth headset connected and audio routed
    HELMET_LOST,       // Headset disconnected, attempting reconnect
    ROUTING_TO_PHONE,  // Fallback to phone speaker/mic
    ROUTING_TO_WIRED   // Wired headset
}

/** Audio mode trade-off setting (Section 12.2) */
enum class AudioMode {
    VOICE_FIRST,   // SCO always on — instant speech, music at SCO quality
    MUSIC_FIRST    // SCO on-demand — better music, speech delay risk
}

/** Aggregate audio statistics for diagnostics */
data class AudioStats(
    val captureActive: Boolean = false,
    val playoutActive: Boolean = false,
    val encoderBitrateKbps: Int = 0,
    val decoderLossConcealed: Int = 0,
    val jitterBufferMs: Int = 0,
    val jitterBufferTargetMs: Int = 0,
    val aecActive: Boolean = false,
    val nsActive: Boolean = false,
    val vadSpeaking: Boolean = false,
    val peerSpeaking: Boolean = false
)

// ═══════════════════════════════════════════════════════════════════════════
// Pairing & Trust (Section 16)
// ═══════════════════════════════════════════════════════════════════════════

/** Role in a session (Section 10.4) */
enum class SessionRole {
    LEADER,   // Creator; arbitrates voluntary switches
    FOLLOWER  // Joiner
}

/**
 * A trusted peer stored locally after successful pairing.
 *
 * The identity public key + pairing secret are the trust anchors.
 * The pairing secret is NEVER sent to any server.
 */
data class TrustedPeer(
    /** Unique peer identifier (derived from identity public key) */
    val peerId: String,
    /** User-chosen display name */
    val displayName: String,
    /** Ed25519 identity public key */
    val identityPubKey: ByteArray,
    /** Human-readable key fingerprint for verification */
    val fingerprint: String,
    /** When the trust was established */
    val pairedAtMs: Long,
    /** Last successful connection */
    val lastSeenMs: Long? = null,
    /** Optional trust expiry (Section 16.3) */
    val trustExpiresAtMs: Long? = null,
    /** Role assigned during pairing */
    val role: SessionRole
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrustedPeer) return false
        return peerId == other.peerId
    }

    override fun hashCode(): Int = peerId.hashCode()
}

// ═══════════════════════════════════════════════════════════════════════════
// GPS & Location (Section 8.3)
// ═══════════════════════════════════════════════════════════════════════════

/** GPS fix shared between peers for distance hinting */
data class GpsFix(
    val latDeg: Double,
    val lonDeg: Double,
    val accuracyM: Float,
    val speedMps: Float? = null,
    val bearingDeg: Float? = null,
    val timestampMs: Long
)

// ═══════════════════════════════════════════════════════════════════════════
// Link Reports (Section 10.1 / 10.4)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Health report exchanged between peers over any connected path.
 * Used by the session leader for coordinated handover decisions.
 */
data class LinkReport(
    val from: String,
    val transport: TransportKind,
    val quality: LinkQuality,
    val hasInternet: Boolean,
    val gps: GpsFix? = null
)

// ═══════════════════════════════════════════════════════════════════════════
// Control Messages
// ═══════════════════════════════════════════════════════════════════════════

/** Messages exchanged between peers on the reliable control channel */
sealed class ControlMessage {
    /** Periodic link health report */
    data class LinkHealthReport(val report: LinkReport) : ControlMessage()

    /** Request to switch transports (from follower to leader) */
    data class SwitchRequest(val targetTransport: TransportKind, val reason: String) : ControlMessage()

    /** Leader instructs follower to begin overlap/switch */
    data class SwitchCommand(val targetTransport: TransportKind, val overlapMs: Long) : ControlMessage()

    /** Confirm transport released */
    data class TransportReleased(val transport: TransportKind) : ControlMessage()

    /** Mute/speaking state */
    data class PeerAudioState(val muted: Boolean, val speaking: Boolean) : ControlMessage()

    /** GPS fix for proximity hinting */
    data class GpsUpdate(val fix: GpsFix) : ControlMessage()

    /** Session lifecycle */
    data object Bye : ControlMessage()

    /** Peer revocation (signed) */
    data class Revoke(val peerId: String, val signature: ByteArray) : ControlMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Revoke) return false
            return peerId == other.peerId
        }
        override fun hashCode(): Int = peerId.hashCode()
    }

    /** Ping for RTT measurement */
    data class Ping(val sentAtMs: Long) : ControlMessage()

    /** Pong response */
    data class Pong(val pingSentAtMs: Long, val pongSentAtMs: Long) : ControlMessage()
}

// ═══════════════════════════════════════════════════════════════════════════
// Session State (Section 11 — State Machine)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Top-level handover state machine states.
 *
 * Matches the state diagram in Section 11 of the spec.
 * Sub-states (RELEASING_*, PREPARING_*, SEARCHING_*, VERIFYING_*)
 * are modeled as sealed subtypes for exhaustive when-expressions.
 */
sealed class HandoverState {
    data object Idle : HandoverState()
    data object Connecting : HandoverState()

    // Local primary states
    data object LocalConnected : HandoverState()
    data object LocalDegrading : HandoverState()

    // Transition states (Local → Internet)
    data object PreparingInternet : HandoverState()
    data class OverlapToInternet(val startedAtMs: Long) : HandoverState()

    // Internet primary states
    data object InternetConnected : HandoverState()
    data class ReleasingLocal(val startedAtMs: Long) : HandoverState()

    // Transition states (Internet → Local)
    data object SearchingLocal : HandoverState()
    data object LocalCandidate : HandoverState()
    data class VerifyingLocal(val startedAtMs: Long) : HandoverState()
    data class OverlapToLocal(val startedAtMs: Long) : HandoverState()
    data class ReleasingInternet(val startedAtMs: Long) : HandoverState()

    // Failure / pause states
    data class NoConnection(val sinceMs: Long) : HandoverState()
    data class Paused(val previousState: HandoverState, val reason: PauseReason) : HandoverState()
}

/** Why the session is paused */
enum class PauseReason {
    PHONE_CALL,
    AUDIO_FOCUS_LOST
}

// ═══════════════════════════════════════════════════════════════════════════
// Handover Events (input to the state machine reducer)
// ═══════════════════════════════════════════════════════════════════════════

sealed class HandoverEvent {
    // Session lifecycle
    data object SessionStarted : HandoverEvent()
    data object SessionStopped : HandoverEvent()

    // Local transport events
    data object LocalConnected : HandoverEvent()
    data object LocalAuthenticated : HandoverEvent()
    data object LocalDisconnected : HandoverEvent()
    data class LocalQualityChanged(val quality: LinkQuality) : HandoverEvent()
    data object LocalNoFrames : HandoverEvent()  // 1.5s silence

    // Internet transport events
    data object InternetAvailable : HandoverEvent()
    data object InternetConnected : HandoverEvent()
    data object InternetReady : HandoverEvent()  // connected + score ≥ 60 + ≥ 25 frames
    data object InternetLost : HandoverEvent()
    data class InternetQualityChanged(val quality: LinkQuality) : HandoverEvent()

    // Peer status
    data object PeerHasInternet : HandoverEvent()
    data object PeerOffline : HandoverEvent()

    // Timers
    data object StabilityTimerElapsed : HandoverEvent()  // e.g. 8s stable local
    data object OverlapTimerElapsed : HandoverEvent()
    data object StandbyTimerElapsed : HandoverEvent()   // 10s hot-standby
    data object DwellTimerElapsed : HandoverEvent()     // min dwell after switch
    data object SessionTtlExpired : HandoverEvent()     // 10 min no connection

    // External
    data object PhoneCallStarted : HandoverEvent()
    data object PhoneCallEnded : HandoverEvent()
    data object AudioFocusLost : HandoverEvent()
    data object AudioFocusReturned : HandoverEvent()

    // Anti-flap
    data object FlapGuardTriggered : HandoverEvent()
    data object FlapGuardExpired : HandoverEvent()
}

// ═══════════════════════════════════════════════════════════════════════════
// Handover Transition (output of the state machine reducer)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Result of a state machine transition.
 * [effects] are side-effects to execute (start/stop transports, timers, earcons).
 */
data class Transition(
    val newState: HandoverState,
    val effects: List<HandoverEffect> = emptyList()
)

/** Side-effects triggered by state transitions */
sealed class HandoverEffect {
    data class StartTransport(val kind: TransportKind) : HandoverEffect()
    data class StopTransport(val kind: TransportKind) : HandoverEffect()
    data class WarmTransport(val kind: TransportKind) : HandoverEffect()
    data class StartTimer(val name: String, val durationMs: Long) : HandoverEffect()
    data class CancelTimer(val name: String) : HandoverEffect()
    data class PlayEarcon(val earcon: EarconType) : HandoverEffect()
    data class UpdateNotification(val state: HandoverState) : HandoverEffect()
    data object StartDiscovery : HandoverEffect()
    data object StopDiscovery : HandoverEffect()
    data class SendControlMessage(val msg: ControlMessage) : HandoverEffect()
    data object EndSession : HandoverEffect()
}

/** Audio cues (Section 17 — earcons, < 500ms, quiet) */
enum class EarconType {
    CONNECTED,
    SWITCHED_TO_INTERNET,
    SWITCHED_TO_LOCAL,
    CONNECTION_LOST,
    HELMET_LOST,
    HELMET_BACK,
    SESSION_ENDED
}

// ═══════════════════════════════════════════════════════════════════════════
// Aggregate Session State (exposed to UI)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * The complete observable session state, exposed as StateFlow to the UI.
 * Combines handover state, audio state, and Bluetooth route state.
 */
data class SessionState(
    val phase: HandoverState = HandoverState.Idle,
    val activeTransports: Set<TransportKind> = emptySet(),
    val primary: TransportKind? = null,
    val route: AudioRouteState = AudioRouteState.ROUTING_TO_PHONE,
    val muted: Boolean = false,
    val peerSpeaking: Boolean = false,
    val peerMuted: Boolean = false,
    val localQuality: LinkQuality? = null,
    val internetQuality: LinkQuality? = null
)

// ═══════════════════════════════════════════════════════════════════════════
// Handover Policy (Section 10.3 — all tunable thresholds)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * All tunable handover thresholds in one place.
 * Every threshold from Section 10.3 is here; tune from field data.
 */
data class HandoverPolicy(
    // Score thresholds
    val localGood: Int = 70,
    val localDegraded: Int = 40,

    // Internet warming
    val warmInternetBelowScore: Int = 70,
    val warmInternetDelayMs: Long = 2_000,
    val warmInternetMinBattery: Int = 20,
    val warmInternetBelowScoreWhenCold: Int = 85,

    // Switch to Internet
    val switchToInternetBelowScore: Int = 40,
    val switchToInternetDelayMs: Long = 3_000,
    val switchToInternetLossThreshold: Float = 10f,  // % late/lost over 3s

    // Emergency switch
    val emergencyNoFramesMs: Long = 1_500,

    // Overlap
    val minOverlapMs: Long = 1_500,
    val maxOverlapMs: Long = 5_000,

    // Internet ready criterion
    val internetReadyMinScore: Int = 60,
    val internetReadyMinFrames: Int = 25,

    // Local hot-standby before release
    val localStandbyBeforeReleaseMs: Long = 10_000,

    // Switch to Local
    val switchToLocalMinScore: Int = 80,
    val switchToLocalStabilityMs: Long = 8_000,
    val switchToLocalMaxDistanceFactor: Float = 0.6f,

    // Anti-flapping
    val minDwellAfterSwitchMs: Long = 10_000,
    val flapGuardSwitchCount: Int = 3,
    val flapGuardWindowMs: Long = 120_000,   // 2 min
    val flapGuardPinMs: Long = 120_000,      // 2 min pin

    // Session TTL when no transport
    val noConnectionTtlMs: Long = 600_000,   // 10 min

    // Score calculation weights (Section 10.2)
    val lossWeight: Float = 40f,
    val latencyWeight: Float = 30f,
    val jitterWeight: Float = 20f,
    val backpressureWeight: Float = 10f,

    // Score penalty ranges
    val maxLossPct: Float = 10f,
    val maxLatencyMs: Int = 400,
    val maxJitterMs: Int = 80,
    val maxBackpressurePct: Float = 10f,

    // Preference mode overrides
    val preferLocalSwitchBelowScore: Int = 25,
    val preferLocalUpgradeScore: Int = 60,
    val preferLocalUpgradeStabilityMs: Long = 5_000,
    val preferInternetMinScore: Int = 40
)

// ═══════════════════════════════════════════════════════════════════════════
// Audio Policy (codec & processing tunables)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * All audio pipeline tunables in one place (Section 7).
 */
data class AudioPolicy(
    val sampleRateHz: Int = 16_000,
    val frameSizeMs: Int = 20,
    val channels: Int = 1,

    // Opus encoder
    val opusBitrateMinKbps: Int = 12,
    val opusBitrateMaxKbps: Int = 32,
    val opusBitrateDefaultKbps: Int = 20,
    val opusComplexity: Int = 6,
    val opusFecEnabled: Boolean = true,
    val opusDtxEnabled: Boolean = true,

    // Jitter buffer
    val jitterBufferLocalMinMs: Int = 40,
    val jitterBufferLocalMaxMs: Int = 80,
    val jitterBufferInternetMinMs: Int = 80,
    val jitterBufferInternetMaxMs: Int = 200,
    val jitterBufferDedupeWindow: Int = 64,

    // VAD
    val vadHangoverMs: Int = 300,

    // Ducking (Section 14.2)
    val duckingAttackMs: Int = 120,
    val duckingReleaseMs: Int = 800,
    val duckingHangoverMs: Int = 800,

    // Data Saver
    val dataSaverBitrateKbps: Int = 12,
    val dataSaverFecEnabled: Boolean = false
)

// ═══════════════════════════════════════════════════════════════════════════
// Pairing Models (Section 16)
// ═══════════════════════════════════════════════════════════════════════════

/** QR code / typed code payload for creating a connection */
data class PairingOffer(
    val version: Int = 1,
    val creatorPubKey: ByteArray,
    val deviceId: String,
    val pairingSecret: ByteArray,  // ≥ 128-bit one-time secret
    val expiresAtMs: Long,         // 5 min from creation
    val shortCode: String          // 6-8 char human-readable code
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairingOffer) return false
        return deviceId == other.deviceId && shortCode == other.shortCode
    }
    override fun hashCode(): Int = deviceId.hashCode()
}

/** Payload received when joining a connection */
data class PairingPayload(
    val version: Int,
    val creatorPubKey: ByteArray,
    val deviceId: String,
    val pairingSecret: ByteArray,
    val expiresAtMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairingPayload) return false
        return deviceId == other.deviceId
    }
    override fun hashCode(): Int = deviceId.hashCode()
}

/** Result of the pairing handshake */
sealed class PairingResult {
    data class Success(val peer: TrustedPeer) : PairingResult()
    data class SasConfirmationRequired(val sasCode: String, val peer: TrustedPeer) : PairingResult()
    data class Failed(val reason: String) : PairingResult()
    data object Expired : PairingResult()
}

// ═══════════════════════════════════════════════════════════════════════════
// Probe / Hints (for transport connect)
// ═══════════════════════════════════════════════════════════════════════════

/** Hints for transport connection */
data class ConnectHints(
    val isReconnect: Boolean = false,
    val preferredBand: WifiBand? = null,
    val warmStandbyOnly: Boolean = false
)

enum class WifiBand { BAND_2_4GHZ, BAND_5GHZ }

/** Result of a transport probe (active RTT check) */
data class ProbeResult(
    val rttMs: Int,
    val success: Boolean,
    val timestampMs: Long
)

// ═══════════════════════════════════════════════════════════════════════════
// Diagnostic Event (Section 18.8 — rolling, capped, no audio/PII)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Structured diagnostic event for logging and the diagnostics overlay.
 * Never contains audio data or personally identifying information.
 */
data class DiagnosticEvent(
    val id: Long = 0,
    val timestampMs: Long,
    val category: DiagnosticCategory,
    val message: String,
    val fromState: String? = null,
    val toState: String? = null,
    val event: String? = null,
    val scores: String? = null,  // JSON of scores at transition time
    val extra: Map<String, String> = emptyMap()
)

enum class DiagnosticCategory {
    STATE_TRANSITION,
    TRANSPORT,
    AUDIO,
    BLUETOOTH,
    SECURITY,
    ERROR,
    PERFORMANCE
}
