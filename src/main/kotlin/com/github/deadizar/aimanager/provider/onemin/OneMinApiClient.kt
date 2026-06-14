package com.github.deadizar.aimanager.provider.onemin

import com.github.deadizar.aimanager.provider.ChatRequest
import com.github.deadizar.aimanager.provider.ChatResponse
import com.github.deadizar.aimanager.provider.ProviderVerification
import com.github.deadizar.aimanager.provider.Usage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

class OneMinApiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val httpClient: OkHttpClient = defaultClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun chat(request: ChatRequest): Result<ChatResponse> = runCatching {
        val payload = OneMinChatRequest(
            type = "CHAT_WITH_AI",
            model = request.modelId,
            conversationId = request.conversationId,
            promptObject = PromptObject(
                prompt = request.messages.joinToString("\n") { "${it.role}: ${it.content}" },
                isMixed = false,
                webSearch = false,
            ),
        )

        val raw = executePost("/api/features", json.encodeToString(OneMinChatRequest.serializer(), payload))
        val parsed = json.decodeFromString(OneMinChatResponse.serializer(), raw)

        ChatResponse(
            content = parsed.content ?: "",
            modelId = parsed.model ?: request.modelId,
            usage = parsed.usage?.let { Usage(it.promptTokens ?: 0, it.completionTokens ?: 0, it.totalTokens ?: (it.promptTokens ?: 0) + (it.completionTokens ?: 0)) },
        )
    }

    fun listModels(): Result<List<String>> = runCatching {
        val raw = executeGet("/api/models")
        val parsed = json.decodeFromString(OneMinModelsResponse.serializer(), raw)
        parsed.models.map { it.id }
    }

    fun verify(): Result<ProviderVerification> = runCatching {
        val raw = executeGet("/api/me")
        val parsed = json.decodeFromString(OneMinVerifyResponse.serializer(), raw)
        ProviderVerification(parsed.ok, parsed.message ?: if (parsed.ok) "OK" else "Verification failed")
    }

    fun generateImage(modelId: String, prompt: String): Result<String> = runCatching {
        val payload = OneMinFeatureRequest(
            type = "IMAGE_GENERATION",
            model = modelId,
            promptObject = GenericPromptObject(prompt = prompt),
        )
        val raw = executePost("/api/features", json.encodeToString(OneMinFeatureRequest.serializer(), payload))
        val parsed = json.decodeFromString(OneMinFeatureResponse.serializer(), raw)
        parsed.url ?: parsed.content ?: ""
    }

    fun textToSpeech(modelId: String, text: String): Result<ByteArray> = runCatching {
        val payload = OneMinFeatureRequest(
            type = "TEXT_TO_SPEECH",
            model = modelId,
            promptObject = GenericPromptObject(prompt = text),
        )
        val raw = executePost("/api/features", json.encodeToString(OneMinFeatureRequest.serializer(), payload))
        val parsed = json.decodeFromString(OneMinFeatureResponse.serializer(), raw)
        when {
            parsed.audioBase64 != null -> Base64.getDecoder().decode(parsed.audioBase64)
            parsed.content != null -> parsed.content.toByteArray()
            else -> byteArrayOf()
        }
    }

    fun speechToText(modelId: String, audio: ByteArray): Result<String> = runCatching {
        val payload = OneMinFeatureRequest(
            type = "SPEECH_TO_TEXT",
            model = modelId,
            promptObject = GenericPromptObject(audioBase64 = Base64.getEncoder().encodeToString(audio)),
        )
        val raw = executePost("/api/features", json.encodeToString(OneMinFeatureRequest.serializer(), payload))
        val parsed = json.decodeFromString(OneMinFeatureResponse.serializer(), raw)
        parsed.text ?: parsed.content.orEmpty()
    }

    private fun executeGet(path: String): String {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}$path")
            .header("Authorization", apiKey)
            .get()
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: $body")
            }
            body
        }
    }

    private fun executePost(path: String, payload: String): String {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}$path")
            .header("Authorization", apiKey)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: $body")
            }
            body
        }
    }

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

@Serializable
private data class OneMinChatRequest(
    val type: String,
    val model: String,
    val conversationId: String? = null,
    val promptObject: PromptObject,
)

@Serializable
private data class PromptObject(
    val prompt: String,
    val isMixed: Boolean,
    val webSearch: Boolean,
)

@Serializable
private data class OneMinChatResponse(
    val content: String? = null,
    val model: String? = null,
    val usage: OneMinUsage? = null,
)

@Serializable
private data class OneMinUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
)

@Serializable
private data class OneMinModelsResponse(
    val models: List<OneMinModel> = emptyList(),
)

@Serializable
private data class OneMinModel(
    val id: String,
)

@Serializable
private data class OneMinVerifyResponse(
    val ok: Boolean,
    val message: String? = null,
)

@Serializable
private data class OneMinFeatureRequest(
    val type: String,
    val model: String,
    val promptObject: GenericPromptObject,
)

@Serializable
private data class GenericPromptObject(
    val prompt: String? = null,
    val audioBase64: String? = null,
)

@Serializable
private data class OneMinFeatureResponse(
    val content: String? = null,
    val url: String? = null,
    val text: String? = null,
    val audioBase64: String? = null,
)

