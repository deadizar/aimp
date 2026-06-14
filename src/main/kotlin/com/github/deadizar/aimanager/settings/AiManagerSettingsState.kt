package com.github.deadizar.aimanager.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "AiManagerSettingsState", storages = [Storage("aimanager.xml")])
class AiManagerSettingsState : PersistentStateComponent<AiManagerSettingsState.State> {
    data class ProviderEntry(
        var instanceId: String = "default-onemin",
        var providerId: String = "onemin",
        var name: String = "1min.ai",
        var baseUrl: String = "https://api.1min.ai",
        var defaultModel: String = "gpt-4o",
        var apiKeyRef: String = "aimanager:default-onemin",
        var capabilitiesCsv: String = "CHAT,IMAGE,TTS,STT",
    )

    data class State(
        var providers: MutableList<ProviderEntry> = mutableListOf(ProviderEntry()),
        var activeProviderInstanceId: String = "default-onemin",
    )

    private var currentState = State()

    override fun getState(): State = currentState

    override fun loadState(state: State) {
        currentState = state
    }
}

