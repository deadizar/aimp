package com.github.deadizar.aimanager.toolWindow

import com.github.deadizar.aimanager.services.AiManagerService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel

class ImagePanel(
    private val service: AiManagerService,
    private val selectedProvider: () -> String,
    private val selectedModel: () -> String,
) {
    private val promptArea = JBTextArea(5, 40)
    private val resultArea = JBTextArea(10, 40).apply { isEditable = false }

    val panel: JPanel = JPanel(BorderLayout()).apply {
        add(JBScrollPane(promptArea), BorderLayout.NORTH)
        add(JBScrollPane(resultArea), BorderLayout.CENTER)
        add(JButton("Generate image").apply {
            addActionListener {
                val prompt = promptArea.text.trim()
                if (prompt.isBlank()) return@addActionListener
                ApplicationManager.getApplication().executeOnPooledThread {
                    val result = service.generateImage(selectedProvider(), selectedModel(), prompt)
                    val text = result.getOrElse { "Error: ${it.message}" }
                    javax.swing.SwingUtilities.invokeLater {
                        resultArea.text = text
                    }
                }
            }
        }, BorderLayout.SOUTH)
    }
}

