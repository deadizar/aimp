package com.github.deadizar.aimanager.toolWindow

import com.github.deadizar.aimanager.core.model.Session
import com.github.deadizar.aimanager.services.AiManagerService
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class HistoryPanel(
    private val service: AiManagerService,
    private val onSessionSelected: (Session) -> Unit,
) {
    private val model = DefaultListModel<Session>()
    private val list = JBList(model)
    private val searchField = JBTextField().apply { emptyText.text = "Search sessions..." }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")

    val panel: JPanel = JPanel(BorderLayout()).apply {
        val top = JPanel(BorderLayout()).apply {
            add(searchField, BorderLayout.CENTER)
            add(JButton("Refresh").apply {
                addActionListener { refreshSessions() }
            }, BorderLayout.EAST)
        }
        add(top, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
    }

    init {
        list.cellRenderer = SessionCellRenderer()
        list.addListSelectionListener {
            val selected = list.selectedValue ?: return@addListSelectionListener
            onSessionSelected(selected)
        }

        list.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = maybeShowPopup(e)
            override fun mouseReleased(e: MouseEvent) = maybeShowPopup(e)
        })

        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = refreshSessions()
            override fun removeUpdate(e: DocumentEvent) = refreshSessions()
            override fun changedUpdate(e: DocumentEvent) = refreshSessions()
        })

        refreshSessions()
    }

    fun setSessions(sessions: List<Session>) {
        model.clear()
        sessions
            .sortedWith(compareByDescending<Session> { it.pinned }.thenByDescending { it.updatedAt })
            .forEach { model.addElement(it) }
    }

    private fun refreshSessions() {
        val query = searchField.text.trim()
        val sessions = if (query.isBlank()) {
            service.listSessions().getOrDefault(emptyList())
        } else {
            service.searchSessions(query).getOrDefault(emptyList())
        }
        setSessions(sessions)
    }

    private fun maybeShowPopup(e: MouseEvent) {
        if (!e.isPopupTrigger) return
        val index = list.locationToIndex(e.point)
        if (index < 0) return
        list.selectedIndex = index
        val session = list.selectedValue ?: return

        val popup = JPopupMenu().apply {
            add(JMenuItem("Rename").apply {
                addActionListener {
                    val newTitle = Messages.showInputDialog(panel, "New session title", "Rename Session", null, session.title, null)
                    if (!newTitle.isNullOrBlank()) {
                        service.renameSession(session.id, newTitle.trim())
                        refreshSessions()
                    }
                }
            })
            add(JMenuItem(if (session.pinned) "Unpin" else "Pin").apply {
                addActionListener {
                    service.togglePin(session.id)
                    refreshSessions()
                }
            })
            add(JMenuItem("Delete").apply {
                addActionListener {
                    val ok = Messages.showYesNoDialog(panel, "Delete this session?", "Delete Session", null)
                    if (ok == Messages.YES) {
                        service.deleteSession(session.id)
                        refreshSessions()
                    }
                }
            })
        }
        popup.show(list, e.x, e.y)
    }

    private inner class SessionCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val session = value as? Session
            if (session != null) {
                val pin = if (session.pinned) "[PIN] " else ""
                text = "$pin${session.title}  (${dateFormat.format(Date(session.updatedAt))})"
            }
            return c
        }
    }
}

