package com.github.deadizar.aimanager.toolWindow

import com.github.deadizar.aimanager.core.export.ExportEngine
import com.github.deadizar.aimanager.core.export.ExportMode
import com.github.deadizar.aimanager.core.model.Session
import com.github.deadizar.aimanager.services.AiManagerService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.nio.file.Paths
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JPanel

class ChatPanel(
    private val service: AiManagerService,
    private val selectedProvider: () -> String,
    private val selectedModel: () -> String,
) {
    private val transcript = JBTextArea(20, 70).apply { isEditable = false }
    private val input = JBTextArea(4, 70)
    private val tokenLabel = JBLabel("Tokens: -")
    private val exportEngine = ExportEngine()

    val panel: JPanel = JPanel(BorderLayout()).apply {
        val topBar = JPanel().apply {
            add(JButton("Send").apply {
                addActionListener { sendMessage() }
            })
            add(JButton("Export session").apply {
                addActionListener { exportSession() }
            })
            add(tokenLabel)
        }
        add(topBar, BorderLayout.NORTH)
        add(JBScrollPane(transcript), BorderLayout.CENTER)
        add(JBScrollPane(input), BorderLayout.SOUTH)
    }

    fun renderSession(session: Session?) {
        if (session == null) {
            transcript.text = ""
            tokenLabel.text = "Tokens: -"
            return
        }
        transcript.text = session.messages.joinToString("\n\n") { "[${it.role}] ${it.content}" }
        val total = session.messages.mapNotNull { it.usage?.totalTokens }.sum()
        tokenLabel.text = "Tokens: $total"
    }

    private fun sendMessage() {
        val text = input.text.trim()
        if (text.isBlank()) return
        input.text = ""

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = service.sendMessage(selectedProvider(), selectedModel(), text)
            javax.swing.SwingUtilities.invokeLater {
                if (result.isSuccess) {
                    renderSession(result.getOrThrow().session)
                } else {
                    transcript.append("\n\n[ERROR] ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    private fun exportSession() {
        val session = service.activeSession() ?: return
        val mode = JOptionPane.showOptionDialog(
            panel,
            "Select export mode",
            "Export LaTeX",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            arrayOf("Full transcript", "Assistant only"),
            "Full transcript",
        )
        val exportMode = if (mode == 1) ExportMode.ASSISTANT_ONLY else ExportMode.FULL_TRANSCRIPT

        val chooser = JFileChooser().apply {
            selectedFile = Paths.get(System.getProperty("user.home"), "${session.id}.tex").toFile()
        }
        if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) {
            return
        }

        val result = exportEngine.exportLatex(session, exportMode, chooser.selectedFile.toPath())
        if (result.isFailure) {
            JOptionPane.showMessageDialog(panel, "Export failed: ${result.exceptionOrNull()?.message}")
        }
    }
}

