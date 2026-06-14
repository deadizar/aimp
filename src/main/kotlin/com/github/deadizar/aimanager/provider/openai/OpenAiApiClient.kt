package com.github.deadizar.aimanager.provider.openai

import com.github.deadizar.aimanager.provider.ChatRequest
import com.github.deadizar.aimanager.provider.ChatResponse
import com.github.deadizar.aimanager.provider.ProviderVerification
import com.github.deadizar.aimanager.provider.Usage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OpenAiApiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val httpClient: OkHttpClient = defaultClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun chat(request: ChatRequest): Result<ChatResponse> = runCatching {
        val payload = OpenAiChatRequest(
            model = request.modelId,
            stream = false,
            messages = request.messages.map { OpenAiMessage(role = it.role.name.lowercase(), content = it.content) },
        )

        val raw = executePost("/v1/chat/completions", json.encodeToString(OpenAiChatRequest.serializer(), payload))
        val parsed = json.decodeFromString(OpenAiChatResponse.serializer(), raw)
        val first = parsed.choices.firstOrNull() ?: error("No choices in response")

        ChatResponse(
            content = first.message.content,
            modelId = parsed.model,
            usage = parsed.usage?.let { Usage(it.promptTokens, it.completionTokens, it.totalTokens) },
        )
    }

    fun listModels(): Result<List<String>> = runCatching {
        val raw = executeGet("/v1/models")
        val parsed = json.decodeFromString(OpenAiModelsResponse.serializer(), raw)
        parsed.data.map { it.id }
    }

    fun verify(): Result<ProviderVerification> = runCatching {
        val models = listModels().getOrThrow()
        ProviderVerification(ok = true, message = "OK (${models.size} models)")
    }

    private fun executeGet(path: String): String {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}$path")
            .header("Authorization", "Bearer $apiKey")
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
            .header("Authorization", "Bearer $apiKey")
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
private data class OpenAiChatRequest(
    val model: String,
    val stream: Boolean,
    val messages: List<OpenAiMessage>,
)

@Serializable
private data class OpenAiMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class OpenAiChatResponse(
    val model: String,
    val choices: List<OpenAiChoice>,
    val usage: OpenAiUsage? = null,
)

@Serializable
private data class OpenAiChoice(
    val message: OpenAiMessage,
)

@Serializable
private data class OpenAiUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int,
)

@Serializable
private data class OpenAiModelsResponse(
    val data: List<OpenAiModelData> = emptyList(),
)

@Serializable
private data class OpenAiModelData(
    val id: String,
)

