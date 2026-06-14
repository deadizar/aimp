package com.github.deadizar.aimanager.e2e

import com.github.deadizar.aimanager.core.export.ExportEngine
import com.github.deadizar.aimanager.core.export.ExportMode
import com.github.deadizar.aimanager.core.export.JsonExporter
import com.github.deadizar.aimanager.core.export.MarkdownExporter
import com.github.deadizar.aimanager.core.model.Message
import com.github.deadizar.aimanager.core.model.MessageRole
import com.github.deadizar.aimanager.core.model.Session
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ExportFlowTest {

    @Test
    fun latexExportContainsExpectedSections() {
        val session = sampleSession()
        val out = Files.createTempDirectory("aimanager-e2e-export").resolve("chat.tex")

        val result = ExportEngine().exportLatex(session, ExportMode.FULL_TRANSCRIPT, out)

        assertTrue(result.isSuccess)
        val tex = Files.readString(out)
        assertTrue(tex.contains("\\section*{USER}"))
        assertTrue(tex.contains("\\section*{ASSISTANT}"))
    }

    @Test
    fun markdownExportProducesHeadings() {
        val md = MarkdownExporter().render(sampleSession(), ExportMode.FULL_TRANSCRIPT)

        assertTrue(md.contains("## User"))
        assertTrue(md.contains("## Assistant"))
        assertTrue(md.contains("title:"))
    }

    @Test
    fun jsonExportIncludesTokenSummaryAndMessages() {
        val json = JsonExporter().render(sampleSession(), ExportMode.FULL_TRANSCRIPT)

        assertTrue(json.contains("\"tokens\""))
        assertTrue(json.contains("\"messages\""))
        assertTrue(json.contains("\"USER\""))
    }

    private fun sampleSession(): Session = Session(
        id = "e2e-export",
        title = "Export flow",
        messages = listOf(
            Message(role = MessageRole.USER, content = "How are you?"),
            Message(role = MessageRole.ASSISTANT, content = "Doing great!"),
        ),
    )
}

