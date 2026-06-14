package com.github.deadizar.aimanager.core.telemetry

import kotlinx.serialization.Serializable

@Serializable
data class TelemetryEvent(
    val timestampMs: Long = System.currentTimeMillis(),
    val providerInstanceId: String,
    val modelId: String,
    val operation: String,
    val latencyMs: Long,
    val success: Boolean,
    val errorType: String? = null,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
)

