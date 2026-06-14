package com.github.deadizar.aimanager.core.export

data class ExportMetadata(
    val exportedAt: Long = System.currentTimeMillis(),
    val providerName: String = "",
    val providerInstanceId: String = "",
    val modelId: String = "",
    val totalPromptTokens: Int = 0,
    val totalCompletionTokens: Int = 0,
    val estimatedCostUsd: Double? = null,
)

