package com.github.deadizar.aimanager.settings

import com.github.deadizar.aimanager.provider.ProviderCapability
import com.github.deadizar.aimanager.provider.ProviderConfig
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service

object AiManagerSettings {

    private fun stateService(): AiManagerSettingsState = ApplicationManager.getApplication().service()

    fun getProviders(): List<AiManagerSettingsState.ProviderEntry> =
        stateService().state.providers.map { it.copy() }

    fun getActiveProviderInstanceId(): String = stateService().state.activeProviderInstanceId

    fun updateProviders(providers: List<AiManagerSettingsState.ProviderEntry>, activeProviderInstanceId: String) {
        val state = stateService().state
        state.providers = providers.toMutableList()
        state.activeProviderInstanceId = activeProviderInstanceId
    }

    fun saveApiKey(apiKeyRef: String, apiKey: String) {
        if (apiKey.isBlank()) {
            return
        }
        PasswordSafe.instance.set(CredentialAttributes(apiKeyRef), Credentials("api-key", apiKey))
    }

    fun readApiKey(apiKeyRef: String): String =
        PasswordSafe.instance.get(CredentialAttributes(apiKeyRef))?.password?.toString().orEmpty()

    fun toProviderConfigs(): List<ProviderConfig> =
        getProviders().map { entry ->
            ProviderConfig(
                instanceId = entry.instanceId,
                id = entry.providerId,
                name = entry.name,
                baseUrl = entry.baseUrl,
                apiKey = readApiKey(entry.apiKeyRef),
                defaultModel = entry.defaultModel,
                capabilities = entry.capabilitiesCsv
                    .split(',')
                    .mapNotNull { value -> runCatching { ProviderCapability.valueOf(value.trim()) }.getOrNull() }
                    .toSet()
                    .ifEmpty { setOf(ProviderCapability.CHAT) },
            )
        }
}

