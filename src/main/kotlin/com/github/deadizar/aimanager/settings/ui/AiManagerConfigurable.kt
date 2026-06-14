package com.github.deadizar.aimanager.settings.ui

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class AiManagerConfigurable : Configurable {
    private var providerSettingsPanel: ProviderSettingsPanel? = null

    override fun getDisplayName(): String = "AI Manager"

    override fun createComponent(): JComponent {
        val panel = ProviderSettingsPanel()
        providerSettingsPanel = panel
        return panel.panel
    }

    override fun isModified(): Boolean = providerSettingsPanel?.isModified() == true

    override fun apply() {
        providerSettingsPanel?.apply()
    }

    override fun reset() {
        providerSettingsPanel?.reset()
    }

    override fun disposeUIResources() {
        providerSettingsPanel = null
    }
}

