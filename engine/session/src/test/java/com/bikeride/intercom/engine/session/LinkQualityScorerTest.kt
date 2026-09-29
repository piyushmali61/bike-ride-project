package com.bikeride.intercom.engine.session

import com.bikeride.intercom.core.model.HandoverPolicy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Unit tests for [LinkQualityScorer].
 *
 * Tests the pure score computation function with various metric combinations.
 */
class LinkQualityScorerTest {

    private val policy = HandoverPolicy()

    @Test
    @DisplayName("Perfect metrics yield score of 100")
    fun perfectScore() {
        val quality = LinkQualityScorer.compute(
            rttMs = 0, jitterMs = 0, lossPct = 0f,
            latePct = 0f, sendDropPct = 0f,
            policy = policy, timestampMs = 1000L
        )
        assertEquals(100, quality.score)
    }

    @Test
    @DisplayName("Maximum bad metrics yield score of 0")
    fun worstScore() {
        val quality = LinkQualityScorer.compute(
            rttMs = 500, jitterMs = 100, lossPct = 15f,
            latePct = 5f, sendDropPct = 15f,
            policy = policy, timestampMs = 1000L
        )
        assertEquals(0, quality.score)
    }

    @Test
    @DisplayName("Loss only penalty")
    fun lossOnlyPenalty() {
        // 5% loss = 50% of max (10%) → penalty = 0.5 → score = 100 - 40*0.5 = 80
        val quality = LinkQualityScorer.compute(
            rttMs = 0, jitterMs = 0, lossPct = 5f,
            policy = policy, timestampMs = 1000L
        )
        assertEquals(80, quality.score)
    }

    @Test
    @DisplayName("Latency only penalty")
    fun latencyOnlyPenalty() {
        // 200ms RTT = 50% of max (400ms) → penalty = 0.5 → score = 100 - 30*0.5 = 85
        val quality = LinkQualityScorer.compute(
            rttMs = 200, jitterMs = 0, lossPct = 0f,
            policy = policy, timestampMs = 1000L
        )
        assertEquals(85, quality.score)
    }

    @Test
    @DisplayName("Jitter only penalty")
    fun jitterOnlyPenalty() {
        // 40ms jitter = 50% of max (80ms) → penalty = 0.5 → score = 100 - 20*0.5 = 90
        val quality = LinkQualityScorer.compute(
            rttMs = 0, jitterMs = 40, lossPct = 0f,
            policy = policy, timestampMs = 1000L
        )
        assertEquals(90, quality.score)
    }

    @Test
    @DisplayName("Late packets count as loss")
    fun latePacketsCountAsLoss() {
        // 3% loss + 2% late = 5% effective loss
        val quality = LinkQualityScorer.compute(
            rttMs = 0, jitterMs = 0, lossPct = 3f, latePct = 2f,
            policy = policy, timestampMs = 1000L
        )
        assertEquals(80, quality.score) // Same as 5% loss only
    }

    @Test
    @DisplayName("Null metrics treated as zero (no penalty)")
    fun nullMetricsSafe() {
        val quality = LinkQualityScorer.compute(
            policy = policy, timestampMs = 1000L
        )
        assertEquals(100, quality.score)
    }

    @ParameterizedTest
    @CsvSource(
        "0, 100",     // No loss = perfect
        "1, 96",      // 1% = 10% penalty → 100-4 = 96
        "5, 80",      // 5% = 50% penalty → 100-20 = 80
        "10, 60",     // 10% = 100% penalty → 100-40 = 60
        "15, 60"      // Capped at max
    )
    @DisplayName("Loss percentage to score mapping")
    fun lossPctScores(lossPct: Float, expectedScore: Int) {
        val quality = LinkQualityScorer.compute(
            lossPct = lossPct,
            policy = policy, timestampMs = 1000L
        )
        assertEquals(expectedScore, quality.score)
    }

    @Nested
    @DisplayName("Score boundaries")
    inner class Boundaries {
        @Test
        fun `score is clamped to 0-100 range`() {
            // Even with extreme values, score stays in range
            val quality = LinkQualityScorer.compute(
                rttMs = 9999, jitterMs = 9999, lossPct = 99f,
                sendDropPct = 99f,
                policy = policy, timestampMs = 1000L
            )
            assertTrue(quality.score in 0..100)
            assertEquals(0, quality.score)
        }

        @Test
        fun `negative-ish values don't break computation`() {
            val quality = LinkQualityScorer.compute(
                rttMs = -1, jitterMs = -1, lossPct = -1f,
                policy = policy, timestampMs = 1000L
            )
            assertEquals(100, quality.score) // Negative treated as 0 by coercion
        }
    }

    @Test
    @DisplayName("Timestamp is preserved in output")
    fun timestampPreserved() {
        val quality = LinkQualityScorer.compute(
            policy = policy, timestampMs = 42L
        )
        assertEquals(42L, quality.measuredAtMs)
    }

    @Test
    @DisplayName("Raw metrics are preserved in output")
    fun rawMetricsPreserved() {
        val quality = LinkQualityScorer.compute(
            rttMs = 50, jitterMs = 10, lossPct = 2f,
            latePct = 1f, sendDropPct = 0.5f,
            throughputKbps = 64, estimatedDistanceM = 120,
            policy = policy, timestampMs = 1000L
        )
        assertEquals(50, quality.rttMs)
        assertEquals(10, quality.jitterMs)
        assertEquals(2f, quality.lossPct)
        assertEquals(1f, quality.latePct)
        assertEquals(0.5f, quality.sendDropPct)
        assertEquals(64, quality.throughputKbps)
        assertEquals(120, quality.estimatedDistanceM)
    }
}
