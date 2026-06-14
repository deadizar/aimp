package com.github.deadizar.aimanager.e2e

import com.github.deadizar.aimanager.core.export.ExportEngine
import com.github.deadizar.aimanager.core.export.ExportMode
import com.github.deadizar.aimanager.core.model.Message
import com.github.deadizar.aimanager.core.model.MessageRole
import com.github.deadizar.aimanager.core.model.Session
import com.github.deadizar.aimanager.core.model.TokenUsage
import com.github.deadizar.aimanager.core.session.SessionManager
import com.github.deadizar.aimanager.core.session.SessionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SessionFlowTest {

    @Test
    fun fullSessionFlowCreateSaveReloadAndExport() {
        val root = Files.createTempDirectory("aimanager-e2e-session")
        val manager = SessionManager(SessionRepository(root))

        val created = manager.newSession("E2E session", "openai-test").getOrThrow()
        val updated = created.copy(
            messages = listOf(
                Message(role = MessageRole.USER, content = "Hi"),
                Message(
                    role = MessageRole.ASSISTANT,
                    content = "Hello!",
                    usage = TokenUsage(promptTokens = 10, completionTokens = 5),
                ),
            ),
        )
        manager.replaceActiveSession(updated).getOrThrow()
        manager.saveActiveSession().getOrThrow()
        manager.closeSession().getOrThrow()

        val loaded = manager.loadSession(created.id).getOrThrow()
        assertEquals(2, loaded?.messages?.size)
        assertEquals("Hello!", loaded?.messages?.last()?.content)

        val out = Files.createTempDirectory("aimanager-e2e-export").resolve("session.tex")
        val exported = ExportEngine().exportLatex(loaded ?: Session(id = "missing", title = "missing"), ExportMode.FULL_TRANSCRIPT, out)
        assertTrue(exported.isSuccess)
        assertTrue(Files.exists(out))
    }
}

