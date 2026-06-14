package com.github.deadizar.aimanager.provider.onemin

import com.github.deadizar.aimanager.provider.AiProvider
import com.github.deadizar.aimanager.provider.ChatRequest
import com.github.deadizar.aimanager.provider.ChatResponse
import com.github.deadizar.aimanager.provider.ProviderCapability
import com.github.deadizar.aimanager.provider.ProviderConfig
import com.github.deadizar.aimanager.provider.ProviderVerification

class OneMinProvider(
    override val config: ProviderConfig,
    private val client: OneMinApiClient = OneMinApiClient(config.baseUrl, config.apiKey),
) : AiProvider {

    init {
        require(config.capabilities.contains(ProviderCapability.CHAT)) {
            "OneMinProvider requires CHAT capability"
        }
    }

    override suspend fun chat(request: ChatRequest): Result<ChatResponse> = client.chat(request)

    override suspend fun listModels(): Result<List<String>> = client.listModels()

    override suspend fun verify(): Result<ProviderVerification> = client.verify()

    override suspend fun generateImage(modelId: String, prompt: String): Result<String> =
        client.generateImage(modelId, prompt)

    override suspend fun textToSpeech(modelId: String, text: String): Result<ByteArray> =
        client.textToSpeech(modelId, text)

    override suspend fun speechToText(modelId: String, audio: ByteArray): Result<String> =
        client.speechToText(modelId, audio)
}

