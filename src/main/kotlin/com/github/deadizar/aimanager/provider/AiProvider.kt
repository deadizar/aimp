package com.github.deadizar.aimanager.provider

import com.github.deadizar.aimanager.core.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface AiProvider {
    val config: ProviderConfig

    suspend fun chat(request: ChatRequest): Result<ChatResponse>

    suspend fun listModels(): Result<List<String>>

    suspend fun verify(): Result<ProviderVerification>

    suspend fun generateImage(modelId: String, prompt: String): Result<String> =
        Result.failure(UnsupportedOperationException("Image generation is not supported by provider ${config.id}"))

    suspend fun textToSpeech(modelId: String, text: String): Result<ByteArray> =
        Result.failure(UnsupportedOperationException("TTS is not supported by provider ${config.id}"))

    suspend fun speechToText(modelId: String, audio: ByteArray): Result<String> =
        Result.failure(UnsupportedOperationException("STT is not supported by provider ${config.id}"))

    suspend fun chatStream(request: ChatRequest): Flow<String> = flow {
        val result = chat(request).getOrThrow()
        emit(result.content)
    }
}

data class ChatRequest(
    val modelId: String,
    val messages: List<Message>,
    val conversationId: String? = null,
)

data class ChatResponse(
    val content: String,
    val modelId: String,
    val usage: Usage? = null,
)

data class Usage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = promptTokens + completionTokens,
)

data class ProviderVerification(
    val ok: Boolean,
    val message: String,
)

