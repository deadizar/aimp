package com.github.deadizar.aimanager.core.export

import com.github.deadizar.aimanager.core.model.Session
import com.github.deadizar.aimanager.core.model.MessageRole
import java.time.Instant

class MarkdownExporter {
    fun render(session: Session, mode: ExportMode, meta: ExportMetadata = ExportMetadata()): String {
        val sb = StringBuilder()

        // Frontmatter YAML
        sb.appendLine("---")
        sb.appendLine("title: \"${session.title}\"")
        sb.appendLine("exported_at: \"${Instant.ofEpochMilli(meta.exportedAt)}\"")
        sb.appendLine("provider: \"${meta.providerName}\"")
        sb.appendLine("model: \"${meta.modelId}\"")
        sb.appendLine("tokens: { prompt: ${meta.totalPromptTokens}, completion: ${meta.totalCompletionTokens} }")
        if (meta.estimatedCostUsd != null) sb.appendLine("cost_usd: ${meta.estimatedCostUsd}")
        sb.appendLine("---")
        sb.appendLine()

        val messages = session.messages.filter {
            mode == ExportMode.FULL_TRANSCRIPT || it.role == MessageRole.ASSISTANT
        }
        messages.forEach { msg ->
            sb.appendLine("## ${msg.role.name.lowercase().replaceFirstChar { it.uppercase() }}")
            sb.appendLine()
            sb.appendLine(msg.content)
            sb.appendLine()
        }
        return sb.toString()
    }
}

