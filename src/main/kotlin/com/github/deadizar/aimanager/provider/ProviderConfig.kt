package com.github.deadizar.aimanager.provider

enum class ProviderCapability {
    CHAT,
    IMAGE,
    TTS,
    STT,
}

data class ProviderConfig(
    val instanceId: String,
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val defaultModel: String = "gpt-4o",
    val capabilities: Set<ProviderCapability> = setOf(ProviderCapability.CHAT),
)

