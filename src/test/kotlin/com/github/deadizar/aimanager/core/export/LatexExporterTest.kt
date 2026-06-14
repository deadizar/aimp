package com.github.deadizar.aimanager.core.export

import com.github.deadizar.aimanager.core.model.Message
import com.github.deadizar.aimanager.core.model.MessageRole
import com.github.deadizar.aimanager.core.model.Session
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LatexExporterTest {

    @Test
    fun renderEscapesSpecialCharacters() {
        val exporter = LatexExporter()
        val session = Session(
            id = "s-1",
            title = "Title \$ & %",
            messages = listOf(
                Message(role = MessageRole.USER, content = "print(\"x_y\")"),
                Message(role = MessageRole.ASSISTANT, content = "Cost is \$100 & tax"),
            ),
        )

        val tex = exporter.render(session, ExportMode.FULL_TRANSCRIPT)
        assertTrue(tex.contains("\\$"))
        assertTrue(tex.contains("\\_"))
        assertTrue(tex.contains("\\&"))
    }

    @Test
    fun assistantOnlyModeSkipsUserMessages() {
        val exporter = LatexExporter()
        val session = Session(
            id = "s-2",
            title = "Assistant only",
            messages = listOf(
                Message(role = MessageRole.USER, content = "u1"),
                Message(role = MessageRole.ASSISTANT, content = "a1"),
            ),
        )

        val tex = exporter.render(session, ExportMode.ASSISTANT_ONLY)
        assertFalse(tex.contains("u1"))
        assertTrue(tex.contains("a1"))
    }

    @Test
    fun exportEngineWritesFile() {
        val engine = ExportEngine()
        val out = Files.createTempDirectory("aimanager-export").resolve("session.tex")
        val session = Session(id = "s-3", title = "Export", messages = emptyList())

        val result = engine.exportLatex(session, ExportMode.FULL_TRANSCRIPT, out)
        assertTrue(result.isSuccess)
        assertTrue(Files.exists(out))
    }
}

