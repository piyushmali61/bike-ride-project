package com.bikeride.intercom.engine.session

import com.bikeride.intercom.core.model.*

/**
 * Pure, deterministic handover state machine reducer — Section 11 & 20.2
 *
 * This is the brain of the automatic LOCAL↔INTERNET handover.
 * It is a pure function: (state, event, now, policy) → Transition
 * with NO side effects, making it fully unit-testable.
 *
 * The [ConnectionManager] drives this by feeding events and executing effects.
 */
interface HandoverController {
    /**
     * Reduce: given the current state, an event, the current time, and policy,
     * produce the new state and any side-effects to execute.
     */
    fun reduce(
        state: HandoverState,
        event: HandoverEvent,
        now: Long,
        policy: HandoverPolicy,
        context: HandoverContext = HandoverContext()
    ): Transition
}

/**
 * Additional context for the reducer that doesn't change per-event
 * but is needed for some decisions (e.g., peer status, battery).
 */
data class HandoverContext(
    val preference: ConnectionPreference = ConnectionPreference.AUTOMATIC,
    val peerHasInternet: Boolean = true,
    val localQuality: LinkQuality? = null,
    val internetQuality: LinkQuality? = null,
    val batteryPct: Int = 100,
    val recentSwitchCount: Int = 0,
    val lastSwitchAtMs: Long = 0L,
    val flapGuardActive: Boolean = false,
    val inDwell: Boolean = false
)

/**
 * Default implementation of [HandoverController].
 *
 * Every state transition from the Section 11 state diagram is handled here.
 * All thresholds come from [HandoverPolicy] — no magic numbers.
 */
class DefaultHandoverController : HandoverController {

    override fun reduce(
        state: HandoverState,
        event: HandoverEvent,
        now: Long,
        policy: HandoverPolicy,
        context: HandoverContext
    ): Transition {
        // Flap guard takes priority
        if (context.flapGuardActive && !isAllowedDuringFlapGuard(event)) {
            return Transition(state) // No-op during flap guard
        }

        return when (state) {
            is HandoverState.Idle -> reduceIdle(event, now, policy, context)
            is HandoverState.Connecting -> reduceConnecting(event, now, policy, context)
            is HandoverState.LocalConnected -> reduceLocalConnected(event, now, policy, context)
            is HandoverState.LocalDegrading -> reduceLocalDegrading(event, now, policy, context)
            is HandoverState.PreparingInternet -> reducePreparingInternet(event, now, policy, context)
            is HandoverState.OverlapToInternet -> reduceOverlapToInternet(state, event, now, policy, context)
            is HandoverState.InternetConnected -> reduceInternetConnected(event, now, policy, context)
            is HandoverState.ReleasingLocal -> reduceReleasingLocal(state, event, now, policy, context)
            is HandoverState.SearchingLocal -> reduceSearchingLocal(event, now, policy, context)
            is HandoverState.LocalCandidate -> reduceLocalCandidate(event, now, policy, context)
            is HandoverState.VerifyingLocal -> reduceVerifyingLocal(state, event, now, policy, context)
            is HandoverState.OverlapToLocal -> reduceOverlapToLocal(state, event, now, policy, context)
            is HandoverState.ReleasingInternet -> reduceReleasingInternet(state, event, now, policy, context)
            is HandoverState.NoConnection -> reduceNoConnection(state, event, now, policy, context)
            is HandoverState.Paused -> reducePaused(state, event, now, policy, context)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // IDLE
    // ═══════════════════════════════════════════════════════════════════

    private fun reduceIdle(
        event: HandoverEvent, now: Long, policy: HandoverPolicy, context: HandoverContext
    ): Transition = when (event) {
        is HandoverEvent.SessionStarted -> Transition(
            newState = HandoverState.Connecting,
            effects = listOf(
                HandoverEffect.StartDiscovery,
                HandoverEffect.StartTransport(TransportKind.INTERNET_WEBRTC)
            )
        )
        else -> Transition(HandoverState.Idle)
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONNECTING
    // ═══════════════════════════════════════════════════════════════════

    private fun reduceConnecting(
        event: HandoverEvent, now: Long, policy: HandoverPolicy, context: HandoverContext
    ): Transition = when (event) {
        is HandoverEvent.LocalAuthenticated -> Transition(
            newState = HandoverState.LocalConnected,
            effects = listOf(
                HandoverEffect.PlayEarcon(EarconType.CONNECTED),
                HandoverEffect.UpdateNotification(HandoverState.LocalConnected)
            )
        )
        is HandoverEvent.InternetReady -> Transition(
            newState = HandoverState.InternetConnected,
            effects = listOf(
                HandoverEffect.PlayEarcon(EarconType.CONNECTED),
                HandoverEffect.UpdateNotification(HandoverState.InternetConnected),
                HandoverEffect.StartDiscovery // Keep looking for local
            )
        )
        is HandoverEvent.SessionStopped -> Transition(
            newState = HandoverState.Idle,
            effects = listOf(HandoverEffect.EndSession)
        )
        else -> Transition(HandoverState.Connecting)
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOCAL_CONNECTED
    // ═══════════════════════════════════════════════════════════════════

    private fun reduceLocalConnected(
        event: HandoverEvent, now: Long, policy: HandoverPolicy, context: HandoverContext
    ): Transition = when (event) {
        is HandoverEvent.LocalQualityChanged -> {
            val score = event.quality.score
            when {
                score < policy.localDegraded -> Transition(
                    newState = HandoverState.LocalDegrading,
                    effects = buildList {
                        // Start warming internet if available and peer has it
                        if (context.peerHasInternet && context.batteryPct > policy.warmInternetMinBattery) {
                            add(HandoverEffect.WarmTransport(TransportKind.INTERNET_WEBRTC))
                        }
                        add(HandoverEffect.StartTimer("degrading_check", policy.switchToInternetDelayMs))
                    }
                )
                score < policy.localGood -> Transition(
                    newState = HandoverState.LocalDegrading,
                    effects = buildList {
                        if (context.peerHasInternet && context.batteryPct > policy.warmInternetMinBattery) {
                            add(HandoverEffect.WarmTransport(TransportKind.INTERNET_WEBRTC))
                        }
                    }
                )
                else -> Transition(HandoverState.LocalConnected)
            }
        }
        is HandoverEvent.LocalDisconnected, is HandoverEvent.LocalNoFrames -> {
            if (context.internetQuality != null && context.internetQuality.score >= policy.internetReadyMinScore) {
                // Emergency switch to Internet
                Transition(
                    newState = HandoverState.InternetConnected,
                    effects = listOf(
                        HandoverEffect.PlayEarcon(EarconType.SWITCHED_TO_INTERNET),
                        HandoverEffect.UpdateNotification(HandoverState.InternetConnected),
                        HandoverEffect.StartTimer("dwell", policy.minDwellAfterSwitchMs)
                    )
                )
            } else {
                Transition(
                    newState = HandoverState.NoConnection(sinceMs = now),
                    effects = listOf(
                        HandoverEffect.PlayEarcon(EarconType.CONNECTION_LOST),
                        HandoverEffect.UpdateNotification(HandoverState.NoConnection(now)),
                        HandoverEffect.StartTransport(TransportKind.INTERNET_WEBRTC),
                        HandoverEffect.StartDiscovery,
                        HandoverEffect.StartTimer("session_ttl", policy.noConnectionTtlMs)
                    )
                )
            }
        }
        is HandoverEvent.StandbyTimerElapsed -> Transition(
            newState = HandoverState.LocalConnected,
            effects = listOf(HandoverEffect.StopTransport(TransportKind.INTERNET_WEBRTC))
        )
        is HandoverEvent.PhoneCallStarted -> Transition(
            newState = HandoverState.Paused(HandoverState.LocalConnected, PauseReason.PHONE_CALL)
        )
        is HandoverEvent.SessionStopped -> Transition(
            newState = HandoverState.Idle,
            effects = listOf(HandoverEffect.EndSession)
        )
        else -> Transition(HandoverState.LocalConnected)
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOCAL_DEGRADING
    // ═══════════════════════════════════════════════════════════════════

    private fun reduceLocalDegrading(
        event: HandoverEvent, now: Long, policy: HandoverPolicy, context: HandoverContext
    ): Transition = when (event) {
        is HandoverEvent.LocalQualityChanged -> {
            val score = event.quality.score
            when {
                score >= policy.switchToLocalMinScore -> Transition(
                    newState = HandoverState.LocalConnected,
                    effects = listOf(
                        HandoverEffect.CancelTimer("degrading_check"),
                        HandoverEffect.UpdateNotification(HandoverState.LocalConnected)
                    )
                )
                else -> Transition(HandoverState.LocalDegrading)
            }
        }
        is HandoverEvent.InternetAvailable -> {
            if (!context.peerHasInternet) {
                // Peer has no Internet — stay on weak local (Section 10.3 "Both-sides rule")
                Transition(HandoverState.LocalDegrading)
            } else {
                Transition(
                    newState = HandoverState.PreparingInternet,
                    effects = listOf(
                        HandoverEffect.WarmTransport(TransportKind.INTERNET_WEBRTC)
                    )
                )
            }
        }
        is HandoverEvent.PeerOffline -> {
            // Peer offline — stay on local no matter what
            Transition(
                newState = HandoverState.LocalDegrading,
                effects = listOf(
                    HandoverEffect.UpdateNotification(HandoverState.LocalDegrading)
                )
            )
        }
        is HandoverEvent.LocalDisconnected, is HandoverEvent.LocalNoFrames -> {
            if (context.internetQuality != null && context.internetQuality.score >= policy.internetReadyMinScore) {
                Transition(
                    newState = HandoverState.InternetConnected,
                    effects = listOf(
                        HandoverEffect.PlayEarcon(EarconType.SWITCHED_TO_INTERNET),
                        HandoverEffect.UpdateNotification(HandoverState.InternetConnected),
                        HandoverEffect.StartTimer("dwell", policy.minDwellAfterSwitchMs)
                    )
                )
            } else {
                Transition(
                    newState = HandoverState.NoConnection(sinceMs = now),
                    effects = listOf(
                        HandoverEffect.PlayEarcon(EarconType.CONNECTION_LOST),
                        HandoverEffect.UpdateNotification(HandoverState.NoConnection(now)),
                        HandoverEffect.StartTransport(TransportKind.INTERNET_WEBRTC),
                        HandoverEffect.StartDiscovery,
                        HandoverEffect.StartTimer("session_ttl", policy.noConnectionTtlMs)
                    )
                )
            }
        }
        is HandoverEvent.SessionStopped -> Transition(
            newState = HandoverState.Idle,
            effects = listOf(HandoverEffect.EndSession)
        )
        else -> Transition(HandoverState.LocalDegrading)
    }

    // ═══════════════════════════════════════════════════════════════════
    // PREPARING_INTERNET
    // ═══════════════════════════════════════════════════════════════════

    private fun reducePreparingInternet(
        event: HandoverEvent, now: Long, policy: HandoverPolicy, context: HandoverContext
    ): Transition = when (event) {
        is HandoverEvent.LocalQualityChanged -> {
            if (event.quality.score >= policy.switchToLocalMinScore) {
                Transition(
                    newState = HandoverState.LocalConnected,
                    effects = listOf(
                        HandoverEffect.CancelTimer("degrading_check"),
                        HandoverEffect.UpdateNotification(HandoverState.LocalConnected)
                    )
                )
            } else {
                Transition(HandoverState.PreparingInternet)
            }
        }
        is HandoverEvent.InternetReady -> {
            // Internet is warm and ready — check if we should switch
            val shouldSwitch = when (context.preference) {
                ConnectionPreference.AUTOMATIC ->
                    (context.localQuality?.score ?: 0) < policy.switchToInternetBelowScore
                ConnectionPreference.PREFER_LOCAL ->
                    (context.localQuality?.score ?: 0) < policy.preferLocalSwitchBelowScore
                ConnectionPreference.PREFER_INTERNET -> true
            }
            if (shouldSwitch) {
                Transition(
                    newState = HandoverState.OverlapToInternet(startedAtMs = now),
                    effects = listOf(
                        HandoverEffect.StartTimer("overlap", policy.maxOverlapMs),
                        HandoverEffect.UpdateNotification(HandoverState.OverlapToInternet(now))
                    )
                )
            } else {
                Transition(HandoverState.PreparingInternet)
            }
        }
        is HandoverEvent.LocalDisconnected, is HandoverEvent.LocalNoFrames -> {
            if (context.internetQuality != null && context.internetQuality.score >= policy.internetReadyMinScore) {
                // Emergency: local dead, Internet ready
                Transition(
                    newState = HandoverState.InternetConnected,
                    effects = listOf(
                        HandoverEffect.PlayEarcon(EarconType.SWITCHED_TO_INTERNET),
                        HandoverEffect.UpdateNotification(HandoverState.InternetConnected)
                    )
                )
            } else {
                Transition(
                    newState = HandoverState.NoConnection(sinceMs = now),
                    effects = listOf(
                        HandoverEffect.PlayEarcon(EarconType.CONNECTION_LOST),
                        HandoverEffect.UpdateNotification(HandoverState.NoConnection(now)),
                        HandoverEffect.StartTimer("session_ttl", policy.noConnectionTtlMs)
                    )
                )
            }
        }
        is HandoverEvent.SessionStopped -> Transition(
            newState = HandoverState.Idle,
            effects = listOf(HandoverEffect.EndSession)
        )
        else -> Transition(HandoverState.PreparingInternet)
    }

    // ═══════════════════════════════════════════════════════════════════
    // OVERLAP_TO_INTERNET (both transports sending/receiving)
    // ═══════════════════════════════════════════════════════════════════

    private fun reduceOverlapToInternet(
        state: HandoverState.OverlapToInternet,
        event: HandoverEvent, now: Long, policy: HandoverPolicy, context: HandoverContext
    ): Transition {
        val elapsed = now - state.startedAtMs
        return when (event) {
            is HandoverEvent.OverlapTimerElapsed, is HandoverEvent.InternetReady -> {
                if (elapsed >= policy.minOverlapMs &&
                    context.internetQuality != null &&
                    context.internetQuality.score >= policy.internetReadyMinScore
                ) {
                    Transition(
                        newState = HandoverState.InternetConnected,
                        effects = listOf(
                            HandoverEffect.PlayEarcon(EarconType.SWITCHED_TO_INTERNET),
                            HandoverEffect.UpdateNotification(HandoverState.InternetConnected),
                            HandoverEffect.StartTimer("local_standby", policy.localStandbyBeforeReleaseMs),
                            HandoverEffect.StartTimer("dwell", policy.minDwellAfterSwitchMs)
                        )
                    )
                } else {
                    Transition(state) // Wait for minimum overlap
                }
            }
            is HandoverEvent.InternetLost -> {
                // Internet died during overlap — stay local
                Transition(
                    newState = HandoverState.LocalConnected,
                    effects = listOf(
                        HandoverEffect.CancelTimer("overlap"),
                        HandoverEffect.UpdateNotification(HandoverState.LocalConnected)
                    )
                )
            }
            is HandoverEvent.LocalQualityChanged -> {
                if (event.quality.score >= policy.switchToLocalMinScore) {
                    // Local recovered — cancel switch
                    Transition(
                        newState = HandoverState.LocalConnected,
                        effects = listOf(
                            HandoverEffect.CancelTimer("overlap"),
                            HandoverEffect.UpdateNotification(HandoverState.LocalConnected)
                        )
                    )
                } else {
                    Transition(state)
                }
            }
            is HandoverEvent.SessionStopped -> Transition(
                newState = HandoverState.Idle,
                effects = listOf(HandoverEffect.EndSession)
            )
            else -> Transition(state)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNET_CONNECTED
    // ═══════════════════════════════════════════════════════════════════

    private fun reduceInternetConnected(
        event: HandoverEvent, now: Long, policy: HandoverPolicy, context: HandoverContext
    ): Transition = when (event) {
        is HandoverEvent.StandbyTimerElapsed -> Transition(
            newState = HandoverState.InternetConnected,
            effects = listOf(HandoverEffect.StopTransport(TransportKind.LOCAL_NEARBY))
        )
        is HandoverEvent.LocalAuthenticated -> {
            // Local peer found while on Internet — start verification
            if (context.preference != ConnectionPreference.PREFER_INTERNET && !context.inDwell) {
                Transition(
                    newState = HandoverState.VerifyingLocal(startedAtMs = now),
                    effects = listOf(
                        HandoverEffect.StartTimer("local_stability", policy.switchToLocalStabilityMs)
                    )
                )
            } else {
                Transition(HandoverState.InternetConnected) // Stay on Internet
            }
        }
        is HandoverEvent.InternetLost -> {
            if (context.localQuality != null && context.localQuality.score > 0) {
                Transition(
                    newState = HandoverState.LocalConnected,
                    effects = listOf(
                        HandoverEffect.PlayEarcon(EarconType.SWITCHED_TO_LOCAL),
                        HandoverEffect.UpdateNotification(HandoverState.LocalConnected)
                    )
                )
            } else {
                Transition(
                    newState = HandoverState.NoConnection(sinceMs = now),
                    effects = listOf(
                        HandoverEffect.PlayEarcon(EarconType.CONNECTION_LOST),
                        HandoverEffect.UpdateNotification(HandoverState.NoConnection(now)),
                        HandoverEffect.StartDiscovery,
                        HandoverEffect.StartTimer("session_ttl", policy.noConnectionTtlMs)
                    )
                )
            }
        }
        is HandoverEvent.PhoneCallStarted -> Transition(
            newState = HandoverState.Paused(HandoverState.InternetConnected, PauseReason.PHONE_CALL)
        )
        is HandoverEvent.SessionStopped -> Transition(
            newState = HandoverState.Idle,
            effects = listOf(HandoverEffect.EndSession)
        )
        else -> Transition(HandoverState.InternetConnected)
    }

    // ═══════════════════════════════════════════════════════════════════
    // RELEASING_LOCAL (hot-standby window before dropping local)
    // ═══════════════════════════════════════════════════════════════════

    private fun reduceReleasingLocal(
        state: HandoverState.ReleasingLocal,
        event: HandoverEvent, now: Long, policy: HandoverPolicy, context: HandoverContext
    ): Transition = when (event) {
        is HandoverEvent.StandbyTimerElapsed -> Transition(
            newState = HandoverState.InternetConnected,
            effects = listOf(
                HandoverEffect.StopTransport(TransportKind.LOCAL_NEARBY),
                HandoverEffect.StopDiscovery
            )
        )
        is HandoverEvent.InternetLost -> Transition(
            newState = HandoverState.LocalConnected,
            effects = listOf(
                HandoverEffect.CancelTimer("local_standby"),
                HandoverEffect.PlayEarcon(EarconType.SWITCHED_TO_LOCAL),
                HandoverEffect.UpdateNotification(HandoverState.LocalConnected)
            )
        )
        is HandoverEvent.SessionStopped -> Transition(
            newState = HandoverState.Idle,
            effects = listOf(HandoverEffect.EndSession)
        )
        else -> Transition(state)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEARCHING_LOCAL (actively looking for local peer)
    // ═══════════════════════════════════════════════════════════════════

    private fun reduceSearchingLocal(
        event: HandoverEvent, now: Long, policy: HandoverPolicy, context: HandoverContext
    ): Transition = when (event) {
        is HandoverEvent.LocalConnected -> Transition(
            newState = HandoverState.LocalCandidate
        )
        is HandoverEvent.InternetLost -> Transition(
            newState = HandoverState.NoConnection(sinceMs = now),
            effects = listOf(
                HandoverEffect.PlayEarcon(EarconType.CONNECTION_LOST),
                HandoverEffect.UpdateNotification(HandoverState.NoConnection(now)),
                HandoverEffect.StartTimer("session_ttl", policy.noConnectionTtlMs)
            )
        )
        is HandoverEvent.SessionStopped -> Transition(
            newState = HandoverState.Idle,
            effects = listOf(HandoverEffect.EndSession)
        )
        else -> Transition(HandoverState.SearchingLocal)
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOCAL_CANDIDATE (local found, needs auth)
    // ═══════════════════════════════════════════════════════════════════

    private fun reduceLocalCandidate(
        event: HandoverEvent, now: Long, policy: HandoverPolicy, context: HandoverContext
    ): Transition = when (event) {
        is HandoverEvent.LocalAuthenticated -> Transition(
            newState = HandoverState.VerifyingLocal(startedAtMs = now),
            effects = listOf(
                HandoverEffect.StartTimer("local_stability", policy.switchToLocalStabilityMs)
            )
        )
        is HandoverEvent.LocalDisconnected -> Transition(
            newState = HandoverState.InternetConnected
        )
        is HandoverEvent.SessionStopped -> Transition(
            newState = HandoverState.Idle,
            effects = listOf(HandoverEffect.EndSession)
        )
        else -> Transition(HandoverState.LocalCandidate)
    }

    // ═══════════════════════════════════════════════════════════════════
    // VERIFYING_LOCAL (checking stability for 8 seconds)
    // ═══════════════════════════════════════════════════════════════════

    private fun reduceVerifyingLocal(
        state: HandoverState.VerifyingLocal,
        event: HandoverEvent, now: Long, policy: HandoverPolicy, context: HandoverContext
    ): Transition = when (event) {
        is HandoverEvent.StabilityTimerElapsed -> {
            val localScore = context.localQuality?.score ?: 0
            if (localScore >= policy.switchToLocalMinScore) {
                Transition(
                    newState = HandoverState.OverlapToLocal(startedAtMs = now),
                    effects = listOf(
                        HandoverEffect.StartTimer("overlap", policy.maxOverlapMs)
                    )
                )
            } else {
                // Local not good enough after stability window
                Transition(
                    newState = HandoverState.InternetConnected,
                    effects = listOf(
                        HandoverEffect.UpdateNotification(HandoverState.InternetConnected)
                    )
                )
            }
        }
        is HandoverEvent.LocalQualityChanged -> {
            if (event.quality.score < policy.internetReadyMinScore) {
                // Unstable — abandon local upgrade
                Transition(
                    newState = HandoverState.InternetConnected,
                    effects = listOf(
                        HandoverEffect.CancelTimer("local_stability"),
                        HandoverEffect.UpdateNotification(HandoverState.InternetConnected)
                    )
                )
            } else {
                Transition(state)
            }
        }
        is HandoverEvent.LocalDisconnected -> Transition(
            newState = HandoverState.InternetConnected,
            effects = listOf(
                HandoverEffect.CancelTimer("local_stability"),
                HandoverEffect.UpdateNotification(HandoverState.InternetConnected)
            )
        )
        is HandoverEvent.SessionStopped -> Transition(
            newState = HandoverState.Idle,
            effects = listOf(HandoverEffect.EndSession)
        )
        else -> Transition(state)
    }

    // ═══════════════════════════════════════════════════════════════════
    // OVERLAP_TO_LOCAL (both transports sending/receiving)
    // ═══════════════════════════════════════════════════════════════════

    private fun reduceOverlapToLocal(
        state: HandoverState.OverlapToLocal,
        event: HandoverEvent, now: Long, policy: HandoverPolicy, context: HandoverContext
    ): Transition {
        val elapsed = now - state.startedAtMs
        return when (event) {
            is HandoverEvent.OverlapTimerElapsed -> {
                if (elapsed >= policy.minOverlapMs &&
                    context.localQuality != null &&
                    context.localQuality.score >= policy.switchToLocalMinScore
                ) {
                    Transition(
                        newState = HandoverState.LocalConnected,
                        effects = listOf(
                            HandoverEffect.PlayEarcon(EarconType.SWITCHED_TO_LOCAL),
                            HandoverEffect.UpdateNotification(HandoverState.LocalConnected),
                            HandoverEffect.StartTimer("internet_standby", policy.localStandbyBeforeReleaseMs),
                            HandoverEffect.StartTimer("dwell", policy.minDwellAfterSwitchMs)
                        )
                    )
                } else {
                    // Local degraded during overlap — go back to Internet
                    Transition(
                        newState = HandoverState.InternetConnected,
                        effects = listOf(
                            HandoverEffect.CancelTimer("overlap"),
                            HandoverEffect.UpdateNotification(HandoverState.InternetConnected)
                        )
                    )
                }
            }
            is HandoverEvent.LocalDisconnected -> Transition(
                newState = HandoverState.InternetConnected,
                effects = listOf(
                    HandoverEffect.CancelTimer("overlap"),
                    HandoverEffect.UpdateNotification(HandoverState.InternetConnected)
                )
            )
            is HandoverEvent.SessionStopped -> Transition(
                newState = HandoverState.Idle,
                effects = listOf(HandoverEffect.EndSession)
            )
            else -> Transition(state)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // RELEASING_INTERNET
    // ═══════════════════════════════════════════════════════════════════

    private fun reduceReleasingInternet(
        state: HandoverState.ReleasingInternet,
        event: HandoverEvent, now: Long, policy: HandoverPolicy, context: HandoverContext
    ): Transition = when (event) {
        is HandoverEvent.StandbyTimerElapsed -> Transition(
            newState = HandoverState.LocalConnected,
            effects = listOf(HandoverEffect.StopTransport(TransportKind.INTERNET_WEBRTC))
        )
        is HandoverEvent.LocalDisconnected, is HandoverEvent.LocalNoFrames -> {
            // Local died during Internet release — keep Internet!
            Transition(
                newState = HandoverState.InternetConnected,
                effects = listOf(
                    HandoverEffect.CancelTimer("internet_standby"),
                    HandoverEffect.PlayEarcon(EarconType.SWITCHED_TO_INTERNET),
                    HandoverEffect.UpdateNotification(HandoverState.InternetConnected)
                )
            )
        }
        is HandoverEvent.SessionStopped -> Transition(
            newState = HandoverState.Idle,
            effects = listOf(HandoverEffect.EndSession)
        )
        else -> Transition(state)
    }

    // ═══════════════════════════════════════════════════════════════════
    // NO_CONNECTION
    // ═══════════════════════════════════════════════════════════════════

    private fun reduceNoConnection(
        state: HandoverState.NoConnection,
        event: HandoverEvent, now: Long, policy: HandoverPolicy, context: HandoverContext
    ): Transition = when (event) {
        is HandoverEvent.LocalAuthenticated -> Transition(
            newState = HandoverState.LocalConnected,
            effects = listOf(
                HandoverEffect.CancelTimer("session_ttl"),
                HandoverEffect.PlayEarcon(EarconType.CONNECTED),
                HandoverEffect.UpdateNotification(HandoverState.LocalConnected)
            )
        )
        is HandoverEvent.InternetReady -> Transition(
            newState = HandoverState.InternetConnected,
            effects = listOf(
                HandoverEffect.CancelTimer("session_ttl"),
                HandoverEffect.PlayEarcon(EarconType.CONNECTED),
                HandoverEffect.UpdateNotification(HandoverState.InternetConnected),
                HandoverEffect.StartDiscovery
            )
        )
        is HandoverEvent.SessionTtlExpired -> Transition(
            newState = HandoverState.Idle,
            effects = listOf(
                HandoverEffect.PlayEarcon(EarconType.SESSION_ENDED),
                HandoverEffect.EndSession
            )
        )
        is HandoverEvent.SessionStopped -> Transition(
            newState = HandoverState.Idle,
            effects = listOf(HandoverEffect.EndSession)
        )
        else -> Transition(state)
    }

    // ═══════════════════════════════════════════════════════════════════
    // PAUSED (phone call or audio focus loss)
    // ═══════════════════════════════════════════════════════════════════

    private fun reducePaused(
        state: HandoverState.Paused,
        event: HandoverEvent, now: Long, policy: HandoverPolicy, context: HandoverContext
    ): Transition = when (event) {
        is HandoverEvent.PhoneCallEnded, is HandoverEvent.AudioFocusReturned -> Transition(
            newState = HandoverState.Connecting, // Re-establish from connecting
            effects = listOf(
                HandoverEffect.StartDiscovery,
                HandoverEffect.StartTransport(TransportKind.INTERNET_WEBRTC),
                HandoverEffect.UpdateNotification(HandoverState.Connecting)
            )
        )
        is HandoverEvent.SessionStopped -> Transition(
            newState = HandoverState.Idle,
            effects = listOf(HandoverEffect.EndSession)
        )
        else -> Transition(state)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════

    /** Events allowed during flap guard (critical failures only) */
    private fun isAllowedDuringFlapGuard(event: HandoverEvent): Boolean = when (event) {
        is HandoverEvent.LocalDisconnected,
        is HandoverEvent.LocalNoFrames,
        is HandoverEvent.InternetLost,
        is HandoverEvent.PhoneCallStarted,
        is HandoverEvent.PhoneCallEnded,
        is HandoverEvent.SessionStopped,
        is HandoverEvent.SessionTtlExpired -> true
        else -> false
    }
}
