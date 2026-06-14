package com.github.deadizar.aimanager.provider

import com.github.deadizar.aimanager.provider.onemin.OneMinProvider
import com.github.deadizar.aimanager.provider.openai.OpenAiCompatibleProvider

class ProviderRegistry {
    fun create(config: ProviderConfig): Result<AiProvider> = runCatching {
        when (config.id) {
            ONE_MIN_ID -> OneMinProvider(config)
            OPENAI_COMPATIBLE_ID -> OpenAiCompatibleProvider(config)
            else -> error("Unsupported provider id: ${config.id}")
        }
    }

    companion object {
        const val ONE_MIN_ID = "onemin"
        const val OPENAI_COMPATIBLE_ID = "openai-compatible"
    }
}

