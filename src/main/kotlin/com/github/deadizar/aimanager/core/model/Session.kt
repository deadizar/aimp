package com.github.deadizar.aimanager.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String,
    val title: String,
    val agentId: String? = null,
    val messages: List<Message> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

