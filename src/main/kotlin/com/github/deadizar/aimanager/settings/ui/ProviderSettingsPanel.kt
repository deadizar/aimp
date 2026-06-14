package com.github.deadizar.aimanager.settings.ui

import com.github.deadizar.aimanager.provider.ProviderCapability
import com.github.deadizar.aimanager.provider.ProviderRegistry
import com.github.deadizar.aimanager.settings.AiManagerSettings
import com.github.deadizar.aimanager.settings.AiManagerSettingsState
import com.github.deadizar.aimanager.settings.ProviderTestCache
import com.github.deadizar.aimanager.settings.ProviderValidator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.runBlocking
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

    // Validation labels
    private val baseUrlValidationLabel = JLabel("")
    private val apiKeyValidationLabel = JLabel("")
    private val defaultModelValidationLabel = JLabel("")

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
            button("Test All") { testAllConnections() }
            button("Diagnose") { diagnoseProvider() }
        }
        row("Instance ID") { cell(instanceIdField).align(AlignX.FILL) }
        row("Provider ID") { cell(providerIdField).align(AlignX.FILL).comment("onemin | openai-compatible") }
        row("Name") { cell(nameField).align(AlignX.FILL) }
        row("Base URL") {
            cell(baseUrlField).align(AlignX.FILL)
            cell(baseUrlValidationLabel)
        }
        row("Default model") {
            cell(defaultModelField).align(AlignX.FILL)
            cell(defaultModelValidationLabel)
        }
        row("API key") {
            cell(apiKeyField).align(AlignX.FILL).comment("Stored in PasswordSafe")
            cell(apiKeyValidationLabel)
        }
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

        // Install validation listeners
        installValidation(
            baseUrlField, baseUrlValidationLabel,
            { ProviderValidator.validateBaseUrl(it) }
        )
        installValidation(
            apiKeyField, apiKeyValidationLabel,
            { ProviderValidator.validateApiKey(it) }
        )
        installValidation(
            defaultModelField, defaultModelValidationLabel,
            { ProviderValidator.validateModelId(it) }
        )
    }

    private fun installValidation(
        field: JTextField,
        label: JLabel,
        validate: (String) -> ProviderValidator.ValidationResult
    ) {
        field.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = update()
            override fun removeUpdate(e: DocumentEvent) = update()
            override fun changedUpdate(e: DocumentEvent) = update()
            private fun update() {
                val r = validate(field.text)
                label.text = if (r.ok) "" else r.message
                label.foreground = if (r.ok) JBColor.GREEN else JBColor.RED
            }
        })
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
        val testResult = if (result.isSuccess && result.getOrThrow().ok) {
            "OK (${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))})"
        } else {
            "FAIL: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
        }
        ProviderTestCache.store(entry.instanceId, testResult)

        if (result.isSuccess && result.getOrThrow().ok) {
            Messages.showInfoMessage("Connection verified", "AI Manager")
        } else {
            Messages.showErrorDialog("Connection failed: ${result.exceptionOrNull()?.message}", "AI Manager")
        }
    }

    private fun testAllConnections() {
        val providers = (0 until model.size()).map { model.getElementAt(it) }
        val results = StringBuilder("Connection Test Results:\n\n")
        val registry = ProviderRegistry()

        ApplicationManager.getApplication().executeOnPooledThread {
            providers.forEach { entry ->
                val config = AiManagerSettings.toProviderConfigs()
                    .firstOrNull { it.instanceId == entry.instanceId }
                val line = if (config == null) {
                    "⚠ ${entry.name}: not saved yet (apply first)"
                } else {
                    val r = runBlocking { registry.create(config).getOrNull()?.verify() }
                    val testResult = if (r?.isSuccess == true && r.getOrThrow().ok) {
                        val msg = "✓ ${entry.name}: ${r.getOrThrow().message}"
                        ProviderTestCache.store(entry.instanceId, "OK (${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))})")
                        msg
                    } else {
                        val msg = "✗ ${entry.name}: ${r?.exceptionOrNull()?.message ?: "init failed"}"
                        ProviderTestCache.store(entry.instanceId, "FAIL: ${r?.exceptionOrNull()?.message ?: "init failed"}")
                        msg
                    }
                    testResult
                }
                results.appendLine(line)
            }
            SwingUtilities.invokeLater {
                Messages.showInfoMessage(panel, results.toString(), "Batch Connection Test")
            }
        }
    }

    private fun diagnoseProvider() {
        val entry = providerList.selectedValue ?: run {
            Messages.showWarningDialog("Select a provider first", "Diagnose")
            return
        }

        val keyRef = entry.apiKeyRef
        val keySet = AiManagerSettings.readApiKey(keyRef).isNotBlank()
        val lastTestCache = ProviderTestCache.getLastResult(entry.instanceId)

        val report = buildString {
            appendLine("Provider:       ${entry.name} (${entry.instanceId})")
            appendLine("Provider ID:    ${entry.providerId}")
            appendLine("Base URL:       ${entry.baseUrl}")
            appendLine("Default model:  ${entry.defaultModel}")
            appendLine("Capabilities:   ${entry.capabilitiesCsv}")
            appendLine("API key:        ${if (keySet) "SET ($keyRef)" else "NOT SET"}")
            appendLine("Last test:      ${lastTestCache ?: "never run"}")
        }

        Messages.showInfoMessage(panel, report, "Provider Diagnostic — ${entry.name}")
    }

    private fun entryFromFields(): AiManagerSettingsState.ProviderEntry? {
        val instanceId = instanceIdField.text.trim()
        val providerId = providerIdField.text.trim()
        val baseUrl = baseUrlField.text.trim()
        val apiKey = apiKeyField.text.trim()
        val defaultModel = defaultModelField.text.trim().ifBlank { "gpt-4o" }

        if (instanceId.isBlank() || providerId.isBlank() || baseUrl.isBlank()) {
            Messages.showErrorDialog("Instance ID, Provider ID and Base URL are required", "AI Manager")
            return null
        }

        // Validate fields
        val urlValidation = ProviderValidator.validateBaseUrl(baseUrl)
        if (!urlValidation.ok) {
            Messages.showErrorDialog(urlValidation.message, "Validation Error")
            return null
        }

        val apiKeyValidation = ProviderValidator.validateApiKey(apiKey)
        if (!apiKeyValidation.ok) {
            Messages.showErrorDialog(apiKeyValidation.message, "Validation Error")
            return null
        }

        val modelValidation = ProviderValidator.validateModelId(defaultModel)
        if (!modelValidation.ok) {
            Messages.showErrorDialog(modelValidation.message, "Validation Error")
            return null
        }

        val capabilities = capabilitiesField.text.trim().ifBlank { ProviderCapability.CHAT.name }
        return AiManagerSettingsState.ProviderEntry(
            instanceId = instanceId,
            providerId = providerId,
            name = nameField.text.trim().ifBlank { instanceId },
            baseUrl = baseUrl,
            defaultModel = defaultModel,
            apiKeyRef = "aimanager:$instanceId",
            capabilitiesCsv = capabilities,
        )
    }
}

