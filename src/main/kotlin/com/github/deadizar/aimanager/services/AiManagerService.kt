package com.github.deadizar.aimanager.services

import com.github.deadizar.aimanager.core.model.Message
import com.github.deadizar.aimanager.core.model.MessageRole
import com.github.deadizar.aimanager.core.model.Session
import com.github.deadizar.aimanager.core.model.TokenUsage
import com.github.deadizar.aimanager.core.session.SessionManager
import com.github.deadizar.aimanager.provider.ChatRequest
import com.github.deadizar.aimanager.provider.ProviderConfig
import com.github.deadizar.aimanager.provider.ProviderRegistry
import com.github.deadizar.aimanager.settings.AiManagerSettings
import com.intellij.openapi.components.Service
import kotlinx.coroutines.runBlocking

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

    fun loadSession(sessionId: String): Result<Session?> = sessionManager.loadSession(sessionId)

    fun newSession(title: String, agentId: String?): Result<Session> = sessionManager.newSession(title, agentId)

    fun sendMessage(providerInstanceId: String, modelId: String, text: String): Result<SendMessageResult> = runCatching {
        val providerConfig = availableProviders().firstOrNull { it.instanceId == providerInstanceId }
            ?: error("Provider instance not found: $providerInstanceId")
        val provider = providerRegistry.create(providerConfig).getOrThrow()

        val now = System.currentTimeMillis()
        val currentSession = sessionManager.activeSession
            ?: sessionManager.newSession(title = text.take(48).ifBlank { "New chat" }, agentId = providerInstanceId).getOrThrow()

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

        SendMessageResult(updated, assistantMessage)
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
}

