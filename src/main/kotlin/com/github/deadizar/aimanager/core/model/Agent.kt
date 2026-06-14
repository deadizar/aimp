package com.github.deadizar.aimanager.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Agent(
    val id: String,
    val name: String,
    val providerId: String,
    val modelId: String,
    val systemPrompt: String = "",
    val parameters: Map<String, String> = emptyMap(),
)

