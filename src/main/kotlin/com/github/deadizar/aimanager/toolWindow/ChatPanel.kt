package com.github.deadizar.aimanager.toolWindow

import com.github.deadizar.aimanager.core.export.ExportEngine
import com.github.deadizar.aimanager.core.export.ExportMode
import com.github.deadizar.aimanager.core.model.Message
import com.github.deadizar.aimanager.core.model.MessageRole
import com.github.deadizar.aimanager.core.model.Session
import com.github.deadizar.aimanager.services.AiManagerService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.nio.file.Paths
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.SwingUtilities

class ChatPanel(
    private val service: AiManagerService,
    private val selectedProvider: () -> String,
    private val selectedModel: () -> String,
) {
    private val messagesPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val input = JBTextArea(4, 70)
    private val tokenLabel = JBLabel("Tokens: -")
    private val exportEngine = ExportEngine()

    val panel: JPanel = JPanel(BorderLayout()).apply {
        val topBar = JPanel().apply {
            add(JButton("Send").apply {
                addActionListener { sendMessage() }
            })
            add(JButton("Retry last").apply {
                addActionListener { retryLastMessage() }
            })
            add(JButton("Export session").apply {
                addActionListener { exportSession() }
            })
            add(tokenLabel)
        }
        add(topBar, BorderLayout.NORTH)
        add(JBScrollPane(messagesPanel), BorderLayout.CENTER)
        add(JBScrollPane(input), BorderLayout.SOUTH)
    }

    fun renderSession(session: Session?) {
        messagesPanel.removeAll()
        if (session == null) {
            tokenLabel.text = "Tokens: -"
            messagesPanel.revalidate()
            messagesPanel.repaint()
            return
        }
        val lastAssistantId = session.messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.id
        session.messages.forEach { msg ->
            messagesPanel.add(
                createMessageRow(
                    msg,
                    isLastAssistant = (msg.role == MessageRole.ASSISTANT && msg.id == lastAssistantId),
                ),
            )
        }
        val total = session.messages.mapNotNull { it.usage?.totalTokens }.sum()
        tokenLabel.text = "Tokens: $total"
        messagesPanel.revalidate()
        messagesPanel.repaint()
        SwingUtilities.invokeLater {
            messagesPanel.scrollRectToVisible(messagesPanel.bounds)
        }
    }

    private fun createMessageRow(message: Message, isLastAssistant: Boolean): JPanel {
        val text = JBTextArea(message.content, 4, 68).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        }
        val title = JBLabel(message.role.name)
        val actions = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))

        if (message.role == MessageRole.USER) {
            actions.add(JButton("Edit + Resend").apply {
                addActionListener {
                    val updated = JOptionPane.showInputDialog(panel, "Edit user message", message.content)
                    if (!updated.isNullOrBlank()) {
                        runAsync { service.editAndResend(message.id, updated.trim()) }
                    }
                }
            })
        }
        if (isLastAssistant) {
            actions.add(JButton("Retry").apply {
                addActionListener { retryLastMessage() }
            })
        }

        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
            add(title, BorderLayout.NORTH)
            add(text, BorderLayout.CENTER)
            add(actions, BorderLayout.SOUTH)
        }
    }

    private fun sendMessage() {
        val text = input.text.trim()
        if (text.isBlank()) return
        input.text = ""

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = service.sendMessage(selectedProvider(), selectedModel(), text)
            SwingUtilities.invokeLater {
                if (result.isSuccess) {
                    renderSession(result.getOrThrow().session)
                } else {
                    JOptionPane.showMessageDialog(panel, "Send failed: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    private fun retryLastMessage() {
        runAsync { service.retryLastUserMessage() }
    }

    private fun runAsync(action: () -> Result<AiManagerService.SendMessageResult>) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = action()
            SwingUtilities.invokeLater {
                if (result.isSuccess) {
                    renderSession(result.getOrThrow().session)
                } else {
                    JOptionPane.showMessageDialog(panel, "Action failed: ${result.exceptionOrNull()?.message}")
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

