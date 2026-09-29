package com.bikeride.intercom.engine.session

import com.bikeride.intercom.core.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DefaultHandoverController].
 *
 * Section 11 requirement: "100% branch coverage of the transition function
 * via unit tests, including flap-guard, dwell, preference-mode, and
 * 'peer offline' cases, using a fake clock and scripted LinkQuality traces."
 */
class HandoverControllerTest {

    private lateinit var controller: HandoverController
    private val policy = HandoverPolicy()
    private var now = 0L

    @BeforeEach
    fun setup() {
        controller = DefaultHandoverController()
        now = 0L
    }

    private fun advance(ms: Long) { now += ms }

    private fun quality(score: Int) = LinkQuality(
        score = score, measuredAtMs = now
    )

    private fun context(
        pref: ConnectionPreference = ConnectionPreference.AUTOMATIC,
        peerHasInternet: Boolean = true,
        localScore: Int? = null,
        internetScore: Int? = null,
        battery: Int = 100,
        flapGuard: Boolean = false,
        inDwell: Boolean = false
    ) = HandoverContext(
        preference = pref,
        peerHasInternet = peerHasInternet,
        localQuality = localScore?.let { quality(it) },
        internetQuality = internetScore?.let { quality(it) },
        batteryPct = battery,
        flapGuardActive = flapGuard,
        inDwell = inDwell
    )

    // ═══════════════════════════════════════════════════════════════════
    // IDLE state tests
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("IDLE state")
    inner class IdleTests {
        @Test
        fun `session started transitions to CONNECTING`() {
            val result = controller.reduce(
                HandoverState.Idle,
                HandoverEvent.SessionStarted,
                now, policy
            )
            assertTrue(result.newState is HandoverState.Connecting)
            assertTrue(result.effects.any { it is HandoverEffect.StartDiscovery })
        }

        @Test
        fun `unknown events in IDLE are no-ops`() {
            val result = controller.reduce(
                HandoverState.Idle,
                HandoverEvent.InternetLost,
                now, policy
            )
            assertTrue(result.newState is HandoverState.Idle)
            assertTrue(result.effects.isEmpty())
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONNECTING state tests
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CONNECTING state")
    inner class ConnectingTests {
        @Test
        fun `local authenticated transitions to LOCAL_CONNECTED`() {
            val result = controller.reduce(
                HandoverState.Connecting,
                HandoverEvent.LocalAuthenticated,
                now, policy
            )
            assertTrue(result.newState is HandoverState.LocalConnected)
            assertTrue(result.effects.any { it is HandoverEffect.PlayEarcon })
        }

        @Test
        fun `internet ready transitions to INTERNET_CONNECTED`() {
            val result = controller.reduce(
                HandoverState.Connecting,
                HandoverEvent.InternetReady,
                now, policy
            )
            assertTrue(result.newState is HandoverState.InternetConnected)
        }

        @Test
        fun `session stopped returns to IDLE`() {
            val result = controller.reduce(
                HandoverState.Connecting,
                HandoverEvent.SessionStopped,
                now, policy
            )
            assertTrue(result.newState is HandoverState.Idle)
            assertTrue(result.effects.any { it is HandoverEffect.EndSession })
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOCAL_CONNECTED state tests
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("LOCAL_CONNECTED state")
    inner class LocalConnectedTests {
        @Test
        fun `quality below degraded threshold triggers LOCAL_DEGRADING`() {
            val result = controller.reduce(
                HandoverState.LocalConnected,
                HandoverEvent.LocalQualityChanged(quality(30)),
                now, policy,
                context(peerHasInternet = true, battery = 80)
            )
            assertTrue(result.newState is HandoverState.LocalDegrading)
        }

        @Test
        fun `quality above good threshold stays LOCAL_CONNECTED`() {
            val result = controller.reduce(
                HandoverState.LocalConnected,
                HandoverEvent.LocalQualityChanged(quality(85)),
                now, policy
            )
            assertTrue(result.newState is HandoverState.LocalConnected)
        }

        @Test
        fun `local disconnect with internet ready does emergency switch`() {
            val result = controller.reduce(
                HandoverState.LocalConnected,
                HandoverEvent.LocalDisconnected,
                now, policy,
                context(internetScore = 80)
            )
            assertTrue(result.newState is HandoverState.InternetConnected)
            assertTrue(result.effects.any {
                it is HandoverEffect.PlayEarcon && it.earcon == EarconType.SWITCHED_TO_INTERNET
            })
        }

        @Test
        fun `local disconnect without internet goes to NO_CONNECTION`() {
            val result = controller.reduce(
                HandoverState.LocalConnected,
                HandoverEvent.LocalDisconnected,
                now, policy,
                context(internetScore = null)
            )
            assertTrue(result.newState is HandoverState.NoConnection)
            assertTrue(result.effects.any { it is HandoverEffect.PlayEarcon })
        }

        @Test
        fun `phone call pauses session`() {
            val result = controller.reduce(
                HandoverState.LocalConnected,
                HandoverEvent.PhoneCallStarted,
                now, policy
            )
            assertTrue(result.newState is HandoverState.Paused)
            val paused = result.newState as HandoverState.Paused
            assertEquals(PauseReason.PHONE_CALL, paused.reason)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOCAL_DEGRADING state tests
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("LOCAL_DEGRADING state")
    inner class LocalDegradingTests {
        @Test
        fun `quality recovery transitions back to LOCAL_CONNECTED`() {
            val result = controller.reduce(
                HandoverState.LocalDegrading,
                HandoverEvent.LocalQualityChanged(quality(85)),
                now, policy
            )
            assertTrue(result.newState is HandoverState.LocalConnected)
        }

        @Test
        fun `internet available with peer online transitions to PREPARING_INTERNET`() {
            val result = controller.reduce(
                HandoverState.LocalDegrading,
                HandoverEvent.InternetAvailable,
                now, policy,
                context(peerHasInternet = true)
            )
            assertTrue(result.newState is HandoverState.PreparingInternet)
        }

        @Test
        fun `internet available but peer offline stays LOCAL_DEGRADING (both-sides rule)`() {
            val result = controller.reduce(
                HandoverState.LocalDegrading,
                HandoverEvent.InternetAvailable,
                now, policy,
                context(peerHasInternet = false)
            )
            assertTrue(result.newState is HandoverState.LocalDegrading)
        }

        @Test
        fun `peer going offline keeps us on local`() {
            val result = controller.reduce(
                HandoverState.LocalDegrading,
                HandoverEvent.PeerOffline,
                now, policy
            )
            assertTrue(result.newState is HandoverState.LocalDegrading)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // OVERLAP_TO_INTERNET state tests
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("OVERLAP_TO_INTERNET state")
    inner class OverlapToInternetTests {
        @Test
        fun `overlap timer with good internet transitions to INTERNET_CONNECTED`() {
            val startTime = 1000L
            advance(startTime + policy.minOverlapMs + 100)

            val result = controller.reduce(
                HandoverState.OverlapToInternet(startedAtMs = startTime),
                HandoverEvent.OverlapTimerElapsed,
                now, policy,
                context(internetScore = 80)
            )
            assertTrue(result.newState is HandoverState.InternetConnected)
            assertTrue(result.effects.any {
                it is HandoverEffect.PlayEarcon && it.earcon == EarconType.SWITCHED_TO_INTERNET
            })
        }

        @Test
        fun `internet lost during overlap returns to LOCAL_CONNECTED`() {
            val result = controller.reduce(
                HandoverState.OverlapToInternet(startedAtMs = 0L),
                HandoverEvent.InternetLost,
                now, policy
            )
            assertTrue(result.newState is HandoverState.LocalConnected)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // NO_CONNECTION state tests
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("NO_CONNECTION state")
    inner class NoConnectionTests {
        @Test
        fun `local returns recovers to LOCAL_CONNECTED`() {
            val result = controller.reduce(
                HandoverState.NoConnection(sinceMs = 0L),
                HandoverEvent.LocalAuthenticated,
                now, policy
            )
            assertTrue(result.newState is HandoverState.LocalConnected)
            assertTrue(result.effects.any { it is HandoverEffect.CancelTimer })
        }

        @Test
        fun `internet returns recovers to INTERNET_CONNECTED`() {
            val result = controller.reduce(
                HandoverState.NoConnection(sinceMs = 0L),
                HandoverEvent.InternetReady,
                now, policy
            )
            assertTrue(result.newState is HandoverState.InternetConnected)
        }

        @Test
        fun `TTL expiry ends session`() {
            val result = controller.reduce(
                HandoverState.NoConnection(sinceMs = 0L),
                HandoverEvent.SessionTtlExpired,
                now, policy
            )
            assertTrue(result.newState is HandoverState.Idle)
            assertTrue(result.effects.any { it is HandoverEffect.EndSession })
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PAUSED state tests
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PAUSED state")
    inner class PausedTests {
        @Test
        fun `phone call ended resumes to CONNECTING`() {
            val result = controller.reduce(
                HandoverState.Paused(HandoverState.LocalConnected, PauseReason.PHONE_CALL),
                HandoverEvent.PhoneCallEnded,
                now, policy
            )
            assertTrue(result.newState is HandoverState.Connecting)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Flap guard tests
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Flap guard")
    inner class FlapGuardTests {
        @Test
        fun `non-critical events are blocked during flap guard`() {
            val result = controller.reduce(
                HandoverState.InternetConnected,
                HandoverEvent.LocalAuthenticated,
                now, policy,
                context(flapGuard = true)
            )
            // Should be a no-op (stays in same state)
            assertTrue(result.newState is HandoverState.InternetConnected)
        }

        @Test
        fun `critical events pass through flap guard`() {
            val result = controller.reduce(
                HandoverState.InternetConnected,
                HandoverEvent.InternetLost,
                now, policy,
                context(flapGuard = true)
            )
            // Should process the event
            assertTrue(result.newState is HandoverState.NoConnection)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Preference mode tests
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Preference modes")
    inner class PreferenceModeTests {
        @Test
        fun `PREFER_INTERNET does not upgrade to local during dwell`() {
            val result = controller.reduce(
                HandoverState.InternetConnected,
                HandoverEvent.LocalAuthenticated,
                now, policy,
                context(pref = ConnectionPreference.PREFER_INTERNET)
            )
            assertTrue(result.newState is HandoverState.InternetConnected)
        }

        @Test
        fun `dwell period blocks local upgrade`() {
            val result = controller.reduce(
                HandoverState.InternetConnected,
                HandoverEvent.LocalAuthenticated,
                now, policy,
                context(inDwell = true)
            )
            assertTrue(result.newState is HandoverState.InternetConnected)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Full handover scenario: LOCAL → INTERNET → LOCAL
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full handover scenario")
    inner class FullScenarioTests {
        @Test
        fun `complete LOCAL to INTERNET handover sequence`() {
            var state: HandoverState = HandoverState.Idle

            // Start session
            var t = controller.reduce(state, HandoverEvent.SessionStarted, now, policy)
            state = t.newState
            assertTrue(state is HandoverState.Connecting)

            // Local connected
            t = controller.reduce(state, HandoverEvent.LocalAuthenticated, now, policy)
            state = t.newState
            assertTrue(state is HandoverState.LocalConnected)

            // Local degrades
            advance(1000)
            t = controller.reduce(
                state,
                HandoverEvent.LocalQualityChanged(quality(30)),
                now, policy,
                context(peerHasInternet = true, battery = 80)
            )
            state = t.newState
            assertTrue(state is HandoverState.LocalDegrading)

            // Internet becomes available
            t = controller.reduce(
                state,
                HandoverEvent.InternetAvailable,
                now, policy,
                context(peerHasInternet = true)
            )
            state = t.newState
            assertTrue(state is HandoverState.PreparingInternet)

            // Internet ready + local still bad → overlap
            advance(3000)
            t = controller.reduce(
                state,
                HandoverEvent.InternetReady,
                now, policy,
                context(localScore = 25, internetScore = 80)
            )
            state = t.newState
            assertTrue(state is HandoverState.OverlapToInternet)

            // Overlap completes
            advance(policy.minOverlapMs + 100)
            t = controller.reduce(
                state,
                HandoverEvent.OverlapTimerElapsed,
                now, policy,
                context(internetScore = 80)
            )
            state = t.newState
            assertTrue(state is HandoverState.InternetConnected)
        }
    }
}
