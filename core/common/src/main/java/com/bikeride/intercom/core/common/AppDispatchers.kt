package com.bikeride.intercom.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * App-wide coroutine dispatchers for DI.
 * Section 6.3: dedicated real-time threads, single-threaded control plane.
 */
interface AppDispatchers {
    /** Default dispatcher for computation */
    val default: CoroutineDispatcher

    /** IO dispatcher for blocking I/O */
    val io: CoroutineDispatcher

    /** Main/UI dispatcher */
    val main: CoroutineDispatcher

    /**
     * Single-threaded dispatcher for deterministic control-plane logic.
     * ConnectionManager, HandoverController, and state machine run here.
     */
    val controlPlane: CoroutineDispatcher
}

/**
 * Injectable monotonic clock for testability.
 * All timing decisions (dwell times, stability windows, flap guard) use this.
 */
interface MonotonicClock {
    fun nowMs(): Long
}

/** Production clock backed by System.nanoTime() */
class SystemMonotonicClock : MonotonicClock {
    private val startNanos = System.nanoTime()
    override fun nowMs(): Long = (System.nanoTime() - startNanos) / 1_000_000
}

/**
 * Fake clock for unit testing state machine transitions.
 * Allows scripted time advancement.
 */
class FakeMonotonicClock(private var currentMs: Long = 0L) : MonotonicClock {
    override fun nowMs(): Long = currentMs
    fun advanceBy(ms: Long) { currentMs += ms }
    fun setTime(ms: Long) { currentMs = ms }
}
