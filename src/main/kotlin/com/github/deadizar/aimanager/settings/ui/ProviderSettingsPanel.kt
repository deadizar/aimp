package com.github.deadizar.aimanager.settings.ui

import com.github.deadizar.aimanager.provider.ProviderCapability
import com.github.deadizar.aimanager.provider.ProviderRegistry
import com.github.deadizar.aimanager.settings.AiManagerSettings
import com.github.deadizar.aimanager.settings.AiManagerSettingsState
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.runBlocking
import javax.swing.DefaultListModel
import javax.swing.JTextField

class ProviderSettingsPanel {
    private val model = DefaultListModel<AiManagerSettingsState.ProviderEntry>()
    private val providerList = JBList(model)

    private val instanceIdField = JTextField()
    private val providerIdField = JTextField()
    private val nameField = JTextField()
    private val baseUrlField = JTextField()
    private val defaultModelField = JTextField()
    private val apiKeyField = JTextField()
    private val capabilitiesField = JTextField()

    private var activeProviderInstanceId: String = ""

    val panel = panel {
        row("Providers") {
            scrollCell(providerList).align(AlignX.FILL)
        }
        row {
            button("Add") { addProvider() }
            button("Update") { updateProvider() }
            button("Delete") { deleteProvider() }
            button("Set Active") { setActiveProvider() }
            button("Test Connection") { testConnection() }
        }
        row("Instance ID") { cell(instanceIdField).align(AlignX.FILL) }
        row("Provider ID") { cell(providerIdField).align(AlignX.FILL).comment("onemin | openai-compatible") }
        row("Name") { cell(nameField).align(AlignX.FILL) }
        row("Base URL") { cell(baseUrlField).align(AlignX.FILL) }
        row("Default model") { cell(defaultModelField).align(AlignX.FILL) }
        row("API key") { cell(apiKeyField).align(AlignX.FILL).comment("Stored in PasswordSafe") }
        row("Capabilities") { cell(capabilitiesField).align(AlignX.FILL).comment("CSV: CHAT,IMAGE,TTS,STT") }
    }

    init {
        providerList.addListSelectionListener {
            val selected = providerList.selectedValue ?: return@addListSelectionListener
            instanceIdField.text = selected.instanceId
            providerIdField.text = selected.providerId
            nameField.text = selected.name
            baseUrlField.text = selected.baseUrl
            defaultModelField.text = selected.defaultModel
            capabilitiesField.text = selected.capabilitiesCsv
            apiKeyField.text = AiManagerSettings.readApiKey(selected.apiKeyRef)
        }
    }

    fun reset() {
        model.clear()
        val providers = AiManagerSettings.getProviders()
        providers.forEach { model.addElement(it) }
        activeProviderInstanceId = AiManagerSettings.getActiveProviderInstanceId()
    }

    fun isModified(): Boolean {
        val current = (0 until model.size()).map { model.getElementAt(it) }
        return current != AiManagerSettings.getProviders() || activeProviderInstanceId != AiManagerSettings.getActiveProviderInstanceId()
    }

    fun apply() {
        val providers = (0 until model.size()).map { model.getElementAt(it) }
        providers.forEach { entry ->
            val selectedKey = if (providerList.selectedValue?.instanceId == entry.instanceId) apiKeyField.text else ""
            AiManagerSettings.saveApiKey(entry.apiKeyRef, selectedKey)
        }
        val active = activeProviderInstanceId.ifBlank { providers.firstOrNull()?.instanceId.orEmpty() }
        AiManagerSettings.updateProviders(providers, active)
    }

    private fun addProvider() {
        val entry = entryFromFields() ?: return
        model.addElement(entry)
    }

    private fun updateProvider() {
        val idx = providerList.selectedIndex
        if (idx < 0) return
        val entry = entryFromFields() ?: return
        model.set(idx, entry)
    }

    private fun deleteProvider() {
        val idx = providerList.selectedIndex
        if (idx < 0) return
        model.remove(idx)
    }

    private fun setActiveProvider() {
        val selected = providerList.selectedValue ?: return
        activeProviderInstanceId = selected.instanceId
        Messages.showInfoMessage("Active provider set to ${selected.instanceId}", "AI Manager")
    }

    private fun testConnection() {
        val entry = entryFromFields() ?: return
        AiManagerSettings.saveApiKey(entry.apiKeyRef, apiKeyField.text)
        val provider = ProviderRegistry().create(
            AiManagerSettings.toProviderConfigs().firstOrNull { it.instanceId == entry.instanceId }
                ?: return,
        ).getOrElse {
            Messages.showErrorDialog("Provider init failed: ${it.message}", "AI Manager")
            return
        }

        val result = runBlocking { provider.verify() }
        if (result.isSuccess && result.getOrThrow().ok) {
            Messages.showInfoMessage("Connection verified", "AI Manager")
        } else {
            Messages.showErrorDialog("Connection failed: ${result.exceptionOrNull()?.message}", "AI Manager")
        }
    }

    private fun entryFromFields(): AiManagerSettingsState.ProviderEntry? {
        val instanceId = instanceIdField.text.trim()
        val providerId = providerIdField.text.trim()
        val baseUrl = baseUrlField.text.trim()
        if (instanceId.isBlank() || providerId.isBlank() || baseUrl.isBlank()) {
            Messages.showErrorDialog("Instance ID, Provider ID and Base URL are required", "AI Manager")
            return null
        }

        val capabilities = capabilitiesField.text.trim().ifBlank { ProviderCapability.CHAT.name }
        return AiManagerSettingsState.ProviderEntry(
            instanceId = instanceId,
            providerId = providerId,
            name = nameField.text.trim().ifBlank { instanceId },
            baseUrl = baseUrl,
            defaultModel = defaultModelField.text.trim().ifBlank { "gpt-4o" },
            apiKeyRef = "aimanager:$instanceId",
            capabilitiesCsv = capabilities,
        )
    }
}

