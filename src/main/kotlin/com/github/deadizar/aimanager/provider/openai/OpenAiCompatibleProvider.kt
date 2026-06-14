package com.github.deadizar.aimanager.provider.openai

import com.github.deadizar.aimanager.provider.AiProvider
import com.github.deadizar.aimanager.provider.ChatRequest
import com.github.deadizar.aimanager.provider.ChatResponse
import com.github.deadizar.aimanager.provider.ProviderCapability
import com.github.deadizar.aimanager.provider.ProviderConfig
import com.github.deadizar.aimanager.provider.ProviderVerification

class OpenAiCompatibleProvider(
    override val config: ProviderConfig,
    private val client: OpenAiApiClient = OpenAiApiClient(config.baseUrl, config.apiKey),
) : AiProvider {

    init {
        require(config.capabilities.contains(ProviderCapability.CHAT)) {
            "OpenAiCompatibleProvider requires CHAT capability"
        }
    }

    override suspend fun chat(request: ChatRequest): Result<ChatResponse> = client.chat(request)

    override suspend fun listModels(): Result<List<String>> = client.listModels()

    override suspend fun verify(): Result<ProviderVerification> = client.verify()
}

