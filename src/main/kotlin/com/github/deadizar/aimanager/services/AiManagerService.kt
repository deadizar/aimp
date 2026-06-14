package com.github.deadizar.aimanager.services

import com.github.deadizar.aimanager.core.model.Message
import com.github.deadizar.aimanager.core.model.MessageRole
import com.github.deadizar.aimanager.core.model.Session
import com.github.deadizar.aimanager.core.model.TokenUsage
import com.github.deadizar.aimanager.core.model.Artifact
import com.github.deadizar.aimanager.core.model.ArtifactType
import com.github.deadizar.aimanager.core.session.SessionManager
import com.github.deadizar.aimanager.provider.AiProviderError
import com.github.deadizar.aimanager.provider.ChatRequest
import com.github.deadizar.aimanager.provider.ProviderConfig
import com.github.deadizar.aimanager.provider.ProviderRegistry
import com.github.deadizar.aimanager.settings.AiManagerSettings
import com.intellij.openapi.components.Service
import kotlinx.coroutines.runBlocking
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Service(Service.Level.APP)
class AiManagerService(
    private val sessionManager: SessionManager = SessionManager(),
    private val providerRegistry: ProviderRegistry = ProviderRegistry(),
) {
    data class SendMessageResult(
        val session: Session,
        val assistantMessage: Message,
    )

    fun availableProviders(): List<ProviderConfig> = AiManagerSettings.toProviderConfigs()

    fun activeProviderInstanceId(): String = AiManagerSettings.getActiveProviderInstanceId()

    fun activeSession(): Session? = sessionManager.activeSession

    fun listSessions(): Result<List<Session>> = sessionManager.listSessions()

    fun renameSession(sessionId: String, newTitle: String): Result<Session> =
        sessionManager.renameSession(sessionId, newTitle)

    fun togglePin(sessionId: String): Result<Session> =
        sessionManager.togglePin(sessionId)

    fun deleteSession(sessionId: String): Result<Unit> =
        sessionManager.deleteSession(sessionId)

    fun searchSessions(query: String): Result<List<Session>> =
        sessionManager.searchSessions(query)

    fun loadSession(sessionId: String): Result<Session?> = sessionManager.loadSession(sessionId)

    fun newSession(title: String, agentId: String?): Result<Session> = sessionManager.newSession(title, agentId)

    fun sendMessage(providerInstanceId: String, modelId: String, text: String): Result<SendMessageResult> = runCatching {
        val chain = (listOf(providerInstanceId) + AiManagerSettings.getFallbackChain()).distinct()
        var lastError: Throwable? = null

        for (instanceId in chain) {
            val providerConfig = availableProviders().firstOrNull { it.instanceId == instanceId }
            if (providerConfig == null) continue

            try {
                val provider = providerRegistry.create(providerConfig).getOrThrow()

                val now = System.currentTimeMillis()
                val currentSession = sessionManager.activeSession
                    ?: sessionManager.newSession(title = text.take(48).ifBlank { "New chat" }, agentId = instanceId).getOrThrow()

                val userMessage = Message(role = MessageRole.USER, content = text, timestamp = now)
                val chatResult = runBlocking {
                    provider.chat(
                        ChatRequest(
                            modelId = modelId,
                            messages = currentSession.messages + userMessage,
                            conversationId = currentSession.id,
                        ),
                    )
                }.getOrThrow()

                val assistantMessage = Message(
                    role = MessageRole.ASSISTANT,
                    content = chatResult.content,
                    timestamp = System.currentTimeMillis(),
                    usage = chatResult.usage?.let {
                        TokenUsage(it.promptTokens, it.completionTokens, it.totalTokens)
                    },
                )

                val updated = currentSession.copy(
                    messages = currentSession.messages + userMessage + assistantMessage,
                    updatedAt = System.currentTimeMillis(),
                )

                sessionManager.replaceActiveSession(updated).getOrThrow()
                sessionManager.saveActiveSession().getOrThrow()

                return SendMessageResult(updated, assistantMessage).let { Result.success(it) }
            } catch (e: AiProviderError.AuthError) {
                // Don't fallback on auth errors - they're permanent
                lastError = e
                break
            } catch (e: Exception) {
                lastError = e
                // Try next provider in chain
            }
        }

        throw lastError ?: error("No providers available")
    }

    fun generateImage(providerInstanceId: String, modelId: String, prompt: String): Result<String> = runCatching {
        val providerConfig = availableProviders().firstOrNull { it.instanceId == providerInstanceId }
            ?: error("Provider instance not found: $providerInstanceId")
        val provider = providerRegistry.create(providerConfig).getOrThrow()
        runBlocking { provider.generateImage(modelId, prompt) }.getOrThrow()
    }

    fun textToSpeech(providerInstanceId: String, modelId: String, text: String): Result<ByteArray> = runCatching {
        val providerConfig = availableProviders().firstOrNull { it.instanceId == providerInstanceId }
            ?: error("Provider instance not found: $providerInstanceId")
        val provider = providerRegistry.create(providerConfig).getOrThrow()
        runBlocking { provider.textToSpeech(modelId, text) }.getOrThrow()
    }

    fun speechToText(providerInstanceId: String, modelId: String, audio: ByteArray): Result<String> = runCatching {
        val providerConfig = availableProviders().firstOrNull { it.instanceId == providerInstanceId }
            ?: error("Provider instance not found: $providerInstanceId")
        val provider = providerRegistry.create(providerConfig).getOrThrow()
        runBlocking { provider.speechToText(modelId, audio) }.getOrThrow()
    }

    fun retryLastUserMessage(): Result<SendMessageResult> = runCatching {
        val session = activeSession() ?: error("No active session")
        val lastUserMsg = session.messages.lastOrNull { it.role == com.github.deadizar.aimanager.core.model.MessageRole.USER }
            ?: error("No user message found")
        val truncated = sessionManager.truncateAfterMessage(session, lastUserMsg.id).getOrThrow()
        sessionManager.replaceActiveSession(truncated).getOrThrow()
        sessionManager.saveActiveSession().getOrThrow()
        sendMessage(session.agentId ?: activeProviderInstanceId(), session.agentId ?: "", lastUserMsg.content).getOrThrow()
    }

    fun editAndResend(upToMessageId: String, newText: String): Result<SendMessageResult> = runCatching {
        val session = activeSession() ?: error("No active session")
        val truncated = sessionManager.truncateAfterMessage(session, upToMessageId).getOrThrow()
        sessionManager.replaceActiveSession(truncated).getOrThrow()
        sessionManager.saveActiveSession().getOrThrow()
        sendMessage(session.agentId ?: activeProviderInstanceId(), session.agentId ?: "", newText).getOrThrow()
    }

    fun saveArtifact(sessionId: String, artifact: Artifact, data: ByteArray): Result<Artifact> = runCatching {
        val artifactDir = artifactDirectory(sessionId)
        Files.createDirectories(artifactDir)
        val filePath = artifactDir.resolve("${artifact.id}.${artifact.filename.substringAfterLast('.')}")
        Files.write(filePath, data)
        
        val session = sessionManager.loadSession(sessionId).getOrThrow() ?: error("Session not found")
        val updated = session.copy(artifacts = session.artifacts + artifact.copy(filename = filePath.fileName.toString()))
        sessionManager.loadSession(sessionId).getOrThrow()
        sessionManager.replaceActiveSession(updated).getOrThrow()
        sessionManager.saveActiveSession().getOrThrow()
        artifact
    }

    fun getArtifacts(sessionId: String): Result<List<Artifact>> = runCatching {
        val session = sessionManager.loadSession(sessionId).getOrThrow()
        session?.artifacts ?: emptyList()
    }

    fun openArtifact(artifact: Artifact): Result<Unit> = runCatching {
        val file = artifactDirectory(artifact.sessionId).resolve(artifact.filename).toFile()
        if (file.exists()) {
            java.awt.Desktop.getDesktop().open(file)
        }
    }

    private fun artifactDirectory(sessionId: String): Path =
        Paths.get(System.getProperty("user.home"), ".config", "aimanager", "artifacts", sessionId)
}

