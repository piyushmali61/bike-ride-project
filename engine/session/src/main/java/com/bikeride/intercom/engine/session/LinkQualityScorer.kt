package com.bikeride.intercom.engine.session

import com.bikeride.intercom.core.model.HandoverPolicy
import com.bikeride.intercom.core.model.LinkQuality

/**
 * Link quality score calculator — Section 10.2
 *
 * Pure function: computes a 0–100 composite score from raw metrics.
 * All weights and ranges come from [HandoverPolicy].
 *
 * score = 100 − (40·lossPenalty + 30·latencyPenalty + 20·jitterPenalty + 10·backpressurePenalty)
 *
 * Each penalty is normalised to 0–1 over the range good → unusable.
 */
object LinkQualityScorer {

    /**
     * Compute the composite link quality score.
     *
     * @param rttMs Round-trip time in milliseconds
     * @param jitterMs Jitter in milliseconds
     * @param lossPct Packet loss percentage (0–100)
     * @param sendDropPct Sender-side backpressure drop percentage (0–100)
     * @param policy Tunable thresholds and weights
     * @param timestampMs Monotonic measurement timestamp
     * @return A [LinkQuality] with the computed score and all raw metrics
     */
    fun compute(
        rttMs: Int? = null,
        jitterMs: Int? = null,
        lossPct: Float? = null,
        latePct: Float? = null,
        sendDropPct: Float? = null,
        throughputKbps: Int? = null,
        estimatedDistanceM: Int? = null,
        policy: HandoverPolicy = HandoverPolicy(),
        timestampMs: Long
    ): LinkQuality {
        // Combined loss = actual loss + late arrivals (past playout deadline)
        val effectiveLossPct = (lossPct ?: 0f) + (latePct ?: 0f)

        val lossPenalty = normalize(effectiveLossPct, 0f, policy.maxLossPct)
        val latencyPenalty = normalize((rttMs ?: 0).toFloat(), 0f, policy.maxLatencyMs.toFloat())
        val jitterPenalty = normalize((jitterMs ?: 0).toFloat(), 0f, policy.maxJitterMs.toFloat())
        val backpressurePenalty = normalize(sendDropPct ?: 0f, 0f, policy.maxBackpressurePct)

        val rawScore = 100f - (
            policy.lossWeight * lossPenalty +
            policy.latencyWeight * latencyPenalty +
            policy.jitterWeight * jitterPenalty +
            policy.backpressureWeight * backpressurePenalty
        )

        val score = rawScore.toInt().coerceIn(0, 100)

        return LinkQuality(
            score = score,
            rttMs = rttMs,
            jitterMs = jitterMs,
            lossPct = lossPct,
            latePct = latePct,
            sendDropPct = sendDropPct,
            throughputKbps = throughputKbps,
            estimatedDistanceM = estimatedDistanceM,
            measuredAtMs = timestampMs
        )
    }

    /**
     * Normalize a value to 0–1 within the range [min, max].
     * Values below min → 0, above max → 1.
     */
    private fun normalize(value: Float, min: Float, max: Float): Float {
        if (max <= min) return 0f
        return ((value - min) / (max - min)).coerceIn(0f, 1f)
    }
}
