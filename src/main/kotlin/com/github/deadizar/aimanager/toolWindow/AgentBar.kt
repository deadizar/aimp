package com.github.deadizar.aimanager.toolWindow

import com.github.deadizar.aimanager.services.AiManagerService
import com.intellij.ui.components.JBTextField
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class AgentBar(
    private val service: AiManagerService,
    private val onNewSession: (String, String) -> Unit,
) {
    private val providerField = JBTextField(20)
    private val modelField = JBTextField(16)

    val panel: JPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
        add(JLabel("Provider"))
        add(providerField)
        add(JLabel("Model"))
        add(modelField)
        add(JButton("Nueva sesión").apply {
            addActionListener {
                onNewSession(selectedProviderInstanceId(), selectedModelId())
            }
        })
    }

    init {
        val providers = service.availableProviders()
        val active = providers.firstOrNull { it.instanceId == service.activeProviderInstanceId() } ?: providers.firstOrNull()
        providerField.text = active?.instanceId ?: "default-onemin"
        modelField.text = active?.defaultModel ?: "gpt-4o"
    }

    fun selectedProviderInstanceId(): String = providerField.text.trim().ifBlank { "default-onemin" }

    fun selectedModelId(): String = modelField.text.trim().ifBlank { "gpt-4o" }
}

