package com.github.deadizar.aimanager.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
}

@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val usage: TokenUsage? = null,
)

