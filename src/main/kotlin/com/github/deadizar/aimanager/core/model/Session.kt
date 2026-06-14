package com.github.deadizar.aimanager.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String,
    val title: String,
    val pinned: Boolean = false,
    val agentId: String? = null,
    val messages: List<Message> = emptyList(),
    val artifacts: List<Artifact> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

