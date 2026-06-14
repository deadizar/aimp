package com.github.deadizar.aimanager.toolWindow

import com.github.deadizar.aimanager.services.AiManagerService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.nio.file.Files
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JPanel

class SttPanel(
    private val service: AiManagerService,
    private val selectedProvider: () -> String,
    private val selectedModel: () -> String,
) {
    private val resultArea = JBTextArea(10, 40).apply { isEditable = false }

    val panel: JPanel = JPanel(BorderLayout()).apply {
        val host = this
        add(JBScrollPane(resultArea), BorderLayout.CENTER)
        add(JButton("Transcribe file").apply {
            addActionListener {
                val chooser = JFileChooser()
                val selected = if (chooser.showOpenDialog(host) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
                if (selected == null) return@addActionListener
                ApplicationManager.getApplication().executeOnPooledThread {
                    val bytes = Files.readAllBytes(selected.toPath())
                    val result = service.speechToText(selectedProvider(), selectedModel(), bytes)
                    val text = result.getOrElse { "Error: ${it.message}" }
                    javax.swing.SwingUtilities.invokeLater {
                        resultArea.text = text
                    }
                }
            }
        }, BorderLayout.SOUTH)
    }
}

