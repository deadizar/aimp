package com.github.deadizar.aimanager.toolWindow

import com.github.deadizar.aimanager.core.model.Session
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JPanel

class HistoryPanel(
    private val onSessionSelected: (Session) -> Unit,
) {
    private val model = DefaultListModel<Session>()
    private val list = JBList(model)

    val panel: JPanel = JPanel(BorderLayout()).apply {
        add(JButton("Refresh").apply {
            addActionListener {
                list.selectedValue?.let(onSessionSelected)
            }
        }, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
    }

    init {
        list.addListSelectionListener {
            val selected = list.selectedValue ?: return@addListSelectionListener
            onSessionSelected(selected)
        }
    }

    fun setSessions(sessions: List<Session>) {
        model.clear()
        sessions.forEach { model.addElement(it) }
    }
}

