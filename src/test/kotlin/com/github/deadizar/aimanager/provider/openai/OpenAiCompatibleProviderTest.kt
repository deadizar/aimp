package com.github.deadizar.aimanager.provider.openai

import com.github.deadizar.aimanager.core.model.Message
import com.github.deadizar.aimanager.core.model.MessageRole
import com.github.deadizar.aimanager.provider.ChatRequest
import com.github.deadizar.aimanager.provider.ProviderCapability
import com.github.deadizar.aimanager.provider.ProviderConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleProviderTest {

    @Test
    fun chatListModelsAndVerify() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "model": "gpt-4o-mini",
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "hello from openai"
                          }
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 4,
                        "completion_tokens": 6,
                        "total_tokens": 10
                      }
                    }
                    """.trimIndent(),
                ),
            )
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "data": [
                        {"id": "gpt-4o-mini"},
                        {"id": "gpt-4.1"}
                      ]
                    }
                    """.trimIndent(),
                ),
            )
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "data": [
                        {"id": "gpt-4o-mini"}
                      ]
                    }
                    """.trimIndent(),
                ),
            )
            server.start()

            val provider = OpenAiCompatibleProvider(
                config = ProviderConfig(
                    instanceId = "openai-test",
                    id = "openai-compatible",
                    name = "OpenAI Compatible",
                    baseUrl = server.url("/").toString(),
                    apiKey = "openai-key",
                    capabilities = setOf(ProviderCapability.CHAT),
                ),
            )

            val chatResult = provider.chat(
                ChatRequest(
                    modelId = "gpt-4o-mini",
                    messages = listOf(Message(role = MessageRole.USER, content = "hi")),
                ),
            ).getOrThrow()
            assertEquals("hello from openai", chatResult.content)
            assertEquals(10, chatResult.usage?.totalTokens)

            val models = provider.listModels().getOrThrow()
            assertEquals(listOf("gpt-4o-mini", "gpt-4.1"), models)

            val verification = provider.verify().getOrThrow()
            assertTrue(verification.ok)

            val firstRequest = server.takeRequest()
            val secondRequest = server.takeRequest()
            val thirdRequest = server.takeRequest()
            assertEquals("/v1/chat/completions", firstRequest.path)
            assertEquals("/v1/models", secondRequest.path)
            assertEquals("/v1/models", thirdRequest.path)
            assertEquals("Bearer openai-key", firstRequest.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }
}

