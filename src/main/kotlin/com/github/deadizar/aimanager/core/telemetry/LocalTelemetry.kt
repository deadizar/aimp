package com.github.deadizar.aimanager.core.telemetry

import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque

data class ProviderStats(
    val totalRequests: Int,
    val successRate: Double,
    val avgLatencyMs: Long,
    val errorBreakdown: Map<String, Int>,
)

object LocalTelemetry {
    private val ring = ArrayDeque<TelemetryEvent>(1000)
    private val json = Json { prettyPrint = true }

    fun record(event: TelemetryEvent) {
        synchronized(ring) {
            if (ring.size >= 1000) ring.removeFirst()
            ring.addLast(event)
        }
    }

    fun snapshot(): List<TelemetryEvent> = synchronized(ring) { ring.toList() }

    fun statsByProvider(): Map<String, ProviderStats> {
        return snapshot().groupBy { it.providerInstanceId }.mapValues { (_, events) ->
            ProviderStats(
                totalRequests = events.size,
                successRate = if (events.isEmpty()) 0.0 else events.count { it.success }.toDouble() / events.size,
                avgLatencyMs = if (events.isEmpty()) 0L else events.map { it.latencyMs }.average().toLong(),
                errorBreakdown = events.filter { !it.success }
                    .groupBy { it.errorType ?: "Unknown" }
                    .mapValues { it.value.size },
            )
        }
    }

    fun exportJson(path: Path) {
        // Placeholder for future JSON export
    }

    fun clear() {
        synchronized(ring) { ring.clear() }
    }
}

