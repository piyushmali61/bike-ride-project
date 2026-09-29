package com.bikeride.intercom.spikes.measurement

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

/**
 * Central measurement collector for all Phase 0 spikes.
 *
 * Records timestamped data points with tags for export.
 * All measurements are stored in-memory and can be exported as CSV.
 */
class MeasurementCollector(private val spikeName: String) {

    data class DataPoint(
        val timestampMs: Long = System.currentTimeMillis(),
        val metric: String,
        val value: Double,
        val unit: String = "",
        val tags: Map<String, String> = emptyMap()
    ) {
        fun toCsv(): String {
            val tagsStr = tags.entries.joinToString(";") { "${it.key}=${it.value}" }
            return "$timestampMs,$metric,$value,$unit,$tagsStr"
        }
    }

    private val _dataPoints = mutableListOf<DataPoint>()
    val dataPoints: List<DataPoint> get() = _dataPoints.toList()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    fun record(metric: String, value: Double, unit: String = "", tags: Map<String, String> = emptyMap()) {
        val dp = DataPoint(metric = metric, value = value, unit = unit, tags = tags)
        _dataPoints.add(dp)
        val logLine = "[$spikeName] $metric = $value $unit ${tags.entries.joinToString { "${it.key}=${it.value}" }}"
        Timber.d(logLine)
        _log.value = _log.value + logLine
    }

    fun recordLatency(rttMs: Long, tags: Map<String, String> = emptyMap()) {
        record("rtt", rttMs.toDouble(), "ms", tags)
    }

    fun recordThroughput(bytesPerSec: Double, tags: Map<String, String> = emptyMap()) {
        record("throughput", bytesPerSec, "bytes/s", tags)
    }

    fun recordEvent(event: String, tags: Map<String, String> = emptyMap()) {
        record("event", 1.0, event, tags)
        val logLine = "[$spikeName] EVENT: $event ${tags.entries.joinToString { "${it.key}=${it.value}" }}"
        _log.value = _log.value + logLine
    }

    fun recordError(error: String) {
        record("error", 1.0, error)
        Timber.e("[$spikeName] ERROR: $error")
        _log.value = _log.value + "[$spikeName] ERROR: $error"
    }

    fun addLog(message: String) {
        Timber.i("[$spikeName] $message")
        _log.value = _log.value + "[$spikeName] $message"
    }

    fun exportCsv(): String {
        val sb = StringBuilder()
        sb.appendLine("timestamp_ms,metric,value,unit,tags")
        _dataPoints.forEach { sb.appendLine(it.toCsv()) }
        return sb.toString()
    }

    fun summary(): Map<String, SummaryStats> {
        return _dataPoints
            .groupBy { it.metric }
            .mapValues { (_, points) ->
                val values = points.map { it.value }.sorted()
                SummaryStats(
                    count = values.size,
                    min = values.firstOrNull() ?: 0.0,
                    max = values.lastOrNull() ?: 0.0,
                    mean = if (values.isNotEmpty()) values.average() else 0.0,
                    p50 = percentile(values, 50),
                    p90 = percentile(values, 90),
                    p99 = percentile(values, 99),
                    unit = points.firstOrNull()?.unit ?: ""
                )
            }
    }

    fun clear() {
        _dataPoints.clear()
        _log.value = emptyList()
    }

    private fun percentile(sorted: List<Double>, p: Int): Double {
        if (sorted.isEmpty()) return 0.0
        val index = (p / 100.0 * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }
}

data class SummaryStats(
    val count: Int,
    val min: Double,
    val max: Double,
    val mean: Double,
    val p50: Double,
    val p90: Double,
    val p99: Double,
    val unit: String
) {
    fun formatted(): String =
        "count=$count min=${format(min)} max=${format(max)} mean=${format(mean)} P50=${format(p50)} P90=${format(p90)} P99=${format(p99)} $unit"

    private fun format(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)
}

/**
 * Latency tracker: measures round-trip time for ping/pong patterns.
 */
class LatencyTracker {
    private val pending = mutableMapOf<Long, Long>() // id → sendTimeNanos
    private var nextId = 0L

    fun startPing(): Long {
        val id = nextId++
        pending[id] = System.nanoTime()
        return id
    }

    fun completePong(id: Long): Long? {
        val sendTime = pending.remove(id) ?: return null
        return (System.nanoTime() - sendTime) / 1_000_000 // Convert to ms
    }

    fun clearStale(maxAgeMs: Long = 5000) {
        val cutoff = System.nanoTime() - maxAgeMs * 1_000_000
        pending.entries.removeAll { it.value < cutoff }
    }
}

/**
 * Throughput tracker: counts bytes over a sliding window.
 */
class ThroughputTracker(private val windowMs: Long = 1000) {
    private data class Entry(val timeMs: Long, val bytes: Int)

    private val entries = ArrayDeque<Entry>()

    fun recordBytes(count: Int) {
        val now = System.currentTimeMillis()
        entries.addLast(Entry(now, count))
        pruneOld(now)
    }

    fun getBytesPerSecond(): Double {
        val now = System.currentTimeMillis()
        pruneOld(now)
        val totalBytes = entries.sumOf { it.bytes }
        val elapsed = if (entries.isNotEmpty()) {
            (now - entries.first().timeMs).coerceAtLeast(1)
        } else windowMs
        return totalBytes * 1000.0 / elapsed
    }

    fun getPacketsPerSecond(): Double {
        val now = System.currentTimeMillis()
        pruneOld(now)
        val elapsed = if (entries.isNotEmpty()) {
            (now - entries.first().timeMs).coerceAtLeast(1)
        } else windowMs
        return entries.size * 1000.0 / elapsed
    }

    private fun pruneOld(now: Long) {
        while (entries.isNotEmpty() && entries.first().timeMs < now - windowMs) {
            entries.removeFirst()
        }
    }
}
