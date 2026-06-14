package com.github.deadizar.aimanager.core.export

import com.github.deadizar.aimanager.core.model.Session
import com.github.deadizar.aimanager.core.model.MessageRole
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MessageExport(
    val role: String,
    val content: String,
    val timestamp: Long,
)

@Serializable
data class TokenSummary(
    val prompt: Int,
    val completion: Int,
)

@Serializable
data class ExportPayload(
    val sessionId: String,
    val title: String,
    val exportedAt: Long,
    val provider: String,
    val model: String,
    val tokens: TokenSummary,
    val estimatedCostUsd: Double?,
    val messages: List<MessageExport>,
)

class JsonExporter {
    private val json = Json { prettyPrint = true }

    fun render(session: Session, mode: ExportMode, meta: ExportMetadata = ExportMetadata()): String {
        val messages = session.messages.filter {
            mode == ExportMode.FULL_TRANSCRIPT || it.role == MessageRole.ASSISTANT
        }
        val export = ExportPayload(
            sessionId = session.id,
            title = session.title,
            exportedAt = meta.exportedAt,
            provider = meta.providerName,
            model = meta.modelId,
            tokens = TokenSummary(meta.totalPromptTokens, meta.totalCompletionTokens),
            estimatedCostUsd = meta.estimatedCostUsd,
            messages = messages.map { MessageExport(it.role.name, it.content, it.timestamp) },
        )
        return json.encodeToString(ExportPayload.serializer(), export)
    }
}

