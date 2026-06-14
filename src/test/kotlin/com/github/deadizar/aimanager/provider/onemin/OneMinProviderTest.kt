package com.github.deadizar.aimanager.provider.onemin

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
import java.util.Base64

class OneMinProviderTest {

    @Test
    fun chatListModelsAndVerify() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "content": "hello from 1min",
                      "model": "gpt-4o",
                      "usage": {
                        "promptTokens": 2,
                        "completionTokens": 3,
                        "totalTokens": 5
                      }
                    }
                    """.trimIndent(),
                ),
            )
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "models": [
                        {"id": "gpt-4o"},
                        {"id": "claude-3.5-sonnet"}
                      ]
                    }
                    """.trimIndent(),
                ),
            )
            server.enqueue(
                MockResponse().setResponseCode(200).setBody("{" + "\"ok\":true,\"message\":\"verified\"" + "}"),
            )
            server.start()

            val provider = OneMinProvider(
                config = ProviderConfig(
                    instanceId = "onemin-test",
                    id = "onemin",
                    name = "1min.ai",
                    baseUrl = server.url("/").toString(),
                    apiKey = "key-1",
                    capabilities = setOf(ProviderCapability.CHAT),
                ),
            )

            val chatResult = provider.chat(
                ChatRequest(
                    modelId = "gpt-4o",
                    messages = listOf(Message(role = MessageRole.USER, content = "hello")),
                ),
            ).getOrThrow()
            assertEquals("hello from 1min", chatResult.content)
            assertEquals(5, chatResult.usage?.totalTokens)

            val models = provider.listModels().getOrThrow()
            assertEquals(listOf("gpt-4o", "claude-3.5-sonnet"), models)

            val verification = provider.verify().getOrThrow()
            assertTrue(verification.ok)
            assertEquals("verified", verification.message)

            val firstRequest = server.takeRequest()
            val secondRequest = server.takeRequest()
            val thirdRequest = server.takeRequest()
            assertEquals("/api/features", firstRequest.path)
            assertEquals("/api/models", secondRequest.path)
            assertEquals("/api/me", thirdRequest.path)
            assertEquals("key-1", firstRequest.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun imageTtsAndSttFlows() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "url": "https://cdn.example/image.png"
                    }
                    """.trimIndent(),
                ),
            )
            val ttsBytes = "audio-bytes".toByteArray()
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "audioBase64": "${Base64.getEncoder().encodeToString(ttsBytes)}"
                    }
                    """.trimIndent(),
                ),
            )
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "text": "recognized speech"
                    }
                    """.trimIndent(),
                ),
            )
            server.start()

            val provider = OneMinProvider(
                config = ProviderConfig(
                    instanceId = "onemin-extra-test",
                    id = "onemin",
                    name = "1min.ai",
                    baseUrl = server.url("/").toString(),
                    apiKey = "key-2",
                    capabilities = setOf(ProviderCapability.CHAT, ProviderCapability.IMAGE, ProviderCapability.TTS, ProviderCapability.STT),
                ),
            )

            val image = provider.generateImage("flux", "cat astronaut").getOrThrow()
            assertEquals("https://cdn.example/image.png", image)

            val audio = provider.textToSpeech("tts-1", "hello").getOrThrow()
            assertEquals(String(ttsBytes), String(audio))

            val text = provider.speechToText("stt-1", "wav".toByteArray()).getOrThrow()
            assertEquals("recognized speech", text)

            assertEquals("/api/features", server.takeRequest().path)
            assertEquals("/api/features", server.takeRequest().path)
            assertEquals("/api/features", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }
}

