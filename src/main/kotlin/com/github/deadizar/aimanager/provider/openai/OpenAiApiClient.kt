package com.github.deadizar.aimanager.provider.openai

import com.github.deadizar.aimanager.provider.AiProviderError
import com.github.deadizar.aimanager.provider.ChatRequest
import com.github.deadizar.aimanager.provider.ChatResponse
import com.github.deadizar.aimanager.provider.ProviderConfig
import com.github.deadizar.aimanager.provider.ProviderVerification
import com.github.deadizar.aimanager.provider.RetryPolicy
import com.github.deadizar.aimanager.provider.Usage
import com.github.deadizar.aimanager.provider.withRetry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAiApiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val connectTimeoutMs: Long = 10_000L,
    private val readTimeoutMs: Long = 60_000L,
    private val httpClient: OkHttpClient = createClient(connectTimeoutMs, readTimeoutMs),
) {
    companion object {
        fun createClient(connectTimeoutMs: Long = 10_000L, readTimeoutMs: Long = 60_000L): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(30_000L, TimeUnit.MILLISECONDS)
                .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun chat(request: ChatRequest): Result<ChatResponse> = withRetry(retryPolicy) {
        runCatching {
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
        }.mapError { if (it is AiProviderError) it else AiProviderError.ApiError(0, it.message ?: "Unknown error", it) }
    }

    suspend fun listModels(): Result<List<String>> = withRetry(retryPolicy) {
        runCatching {
            val raw = executeGet("/v1/models")
            val parsed = json.decodeFromString(OpenAiModelsResponse.serializer(), raw)
            parsed.data.map { it.id }
        }.mapError { if (it is AiProviderError) it else AiProviderError.ApiError(0, it.message ?: "Unknown error", it) }
    }

    suspend fun verify(): Result<ProviderVerification> = withRetry(retryPolicy) {
        runCatching {
            val models = listModels().getOrThrow()
            ProviderVerification(ok = true, message = "OK (${models.size} models)")
        }.mapError { if (it is AiProviderError) it else AiProviderError.ApiError(0, it.message ?: "Unknown error", it) }
    }

    private fun executeGet(path: String): String {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}$path")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw mapHttpError(response.code, body)
                }
                body
            }
        } catch (e: IOException) {
            throw AiProviderError.NetworkError("Network error: ${e.message}", e)
        }
    }

    private fun executePost(path: String, payload: String): String {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}$path")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw mapHttpError(response.code, body)
                }
                body
            }
        } catch (e: IOException) {
            throw AiProviderError.NetworkError("Network error: ${e.message}", e)
        }
    }

    private fun mapHttpError(code: Int, body: String): AiProviderError = when (code) {
        401, 403 -> AiProviderError.AuthError("Authentication failed (HTTP $code)")
        429 -> AiProviderError.QuotaError("Rate limit or quota exceeded (HTTP 429)")
        404 -> if ("model" in body.lowercase())
            AiProviderError.InvalidModelError("Model not found: $body")
        else AiProviderError.ApiError(404, body)
        else -> AiProviderError.ApiError(code, "HTTP $code: $body")
    }

    private fun <T> Result<T>.mapError(mapper: (Throwable) -> Throwable): Result<T> =
        fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(mapper(it)) },
        )
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

