package com.github.deadizar.aimanager.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ArtifactType {
    IMAGE,
    AUDIO,
    TRANSCRIPT,
}

@Serializable
data class Artifact(
    val id: String = UUID.randomUUID().toString(),
    val type: ArtifactType,
    val sessionId: String,
    val filename: String,
    val mimeType: String,
    val createdAt: Long = System.currentTimeMillis(),
    val prompt: String? = null,
)

