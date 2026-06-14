package com.github.deadizar.aimanager.e2e

import com.github.deadizar.aimanager.core.model.Message
import com.github.deadizar.aimanager.core.model.MessageRole
import com.github.deadizar.aimanager.provider.AiProviderError
import com.github.deadizar.aimanager.provider.ChatRequest
import com.github.deadizar.aimanager.provider.ProviderCapability
import com.github.deadizar.aimanager.provider.ProviderConfig
import com.github.deadizar.aimanager.provider.RetryPolicy
import com.github.deadizar.aimanager.provider.openai.OpenAiCompatibleProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderFlowTest {

    @Test
    fun authErrorMapsToTypedError() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(401).setBody("{\"error\":\"invalid_api_key\"}"))
            server.start()

            val provider = providerFor(server, "auth-case")
            val result = provider.chat(request())

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is AiProviderError.AuthError)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun retrySucceedsOnThirdAttempt() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{\"error\":\"unavailable\"}"))
            server.enqueue(MockResponse().setResponseCode(503).setBody("{\"error\":\"unavailable\"}"))
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "model": "gpt-4o-mini",
                      "choices": [{"message": {"role": "assistant", "content": "ok"}}],
                      "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2}
                    }
                    """.trimIndent(),
                ),
            )
            server.start()

            val provider = providerFor(
                server = server,
                instanceId = "retry-case",
                retryPolicy = RetryPolicy(maxAttempts = 3, initialDelayMs = 1, maxDelayMs = 1),
            )
            val result = provider.chat(request())

            assertTrue(result.isSuccess)
            assertEquals(3, server.requestCount)
            assertEquals("ok", result.getOrThrow().content)
        } finally {
            server.shutdown()
        }
    }

    private fun providerFor(server: MockWebServer, instanceId: String, retryPolicy: RetryPolicy = RetryPolicy()): OpenAiCompatibleProvider {
        return OpenAiCompatibleProvider(
            config = ProviderConfig(
                instanceId = instanceId,
                id = "openai-compatible",
                name = "OpenAI Compatible",
                baseUrl = server.url("/").toString(),
                apiKey = "test-key",
                capabilities = setOf(ProviderCapability.CHAT),
                retryPolicy = retryPolicy,
            ),
        )
    }

    private fun request(): ChatRequest = ChatRequest(
        modelId = "gpt-4o-mini",
        messages = listOf(Message(role = MessageRole.USER, content = "hello")),
    )
}

