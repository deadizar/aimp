package com.github.deadizar.aimanager.toolWindow

import com.github.deadizar.aimanager.services.AiManagerService
import java.awt.BorderLayout
import javax.swing.JTabbedPane
import javax.swing.JPanel
import javax.swing.JSplitPane

class AiManagerToolWindow(
    private val service: AiManagerService,
) {
    private val agentBar = AgentBar(service) { providerId, _ ->
        service.newSession("New session", providerId)
        refresh()
    }

    private val chatPanel = ChatPanel(service, agentBar::selectedProviderInstanceId, agentBar::selectedModelId)

    private val historyPanel = HistoryPanel(service) { session ->
        service.loadSession(session.id)
        chatPanel.renderSession(service.activeSession())
    }

    val panel: JPanel = JPanel(BorderLayout()).apply {
        val tabs = JTabbedPane().apply {
            addTab("Chat", chatPanel.panel)
            addTab("Image", ImagePanel(service, agentBar::selectedProviderInstanceId, agentBar::selectedModelId).panel)
            addTab("TTS", TtsPanel(service, agentBar::selectedProviderInstanceId, agentBar::selectedModelId).panel)
            addTab("STT", SttPanel(service, agentBar::selectedProviderInstanceId, agentBar::selectedModelId).panel)
        }

        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, historyPanel.panel, tabs)
        split.resizeWeight = 0.25

        add(agentBar.panel, BorderLayout.NORTH)
        add(split, BorderLayout.CENTER)
    }

    fun refresh() {
        historyPanel.setSessions(service.listSessions().getOrDefault(emptyList()))
        chatPanel.renderSession(service.activeSession())
    }
}

