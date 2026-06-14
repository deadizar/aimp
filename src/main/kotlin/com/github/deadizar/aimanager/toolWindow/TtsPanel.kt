package com.github.deadizar.aimanager.toolWindow

import com.github.deadizar.aimanager.services.AiManagerService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel

class TtsPanel(
    private val service: AiManagerService,
    private val selectedProvider: () -> String,
    private val selectedModel: () -> String,
) {
    private val textArea = JBTextArea(8, 40)
    private val resultArea = JBTextArea(4, 40).apply { isEditable = false }

    val panel: JPanel = JPanel(BorderLayout()).apply {
        add(JBScrollPane(textArea), BorderLayout.CENTER)
        add(JBScrollPane(resultArea), BorderLayout.NORTH)
        add(JButton("Synthesize").apply {
            addActionListener {
                val text = textArea.text.trim()
                if (text.isBlank()) return@addActionListener
                ApplicationManager.getApplication().executeOnPooledThread {
                    val result = service.textToSpeech(selectedProvider(), selectedModel(), text)
                    val status = result.fold(
                        onSuccess = { "Generated audio bytes: ${it.size}" },
                        onFailure = { "Error: ${it.message}" },
                    )
                    javax.swing.SwingUtilities.invokeLater {
                        resultArea.text = status
                    }
                }
            }
        }, BorderLayout.SOUTH)
    }
}

