package com.github.deadizar.aimanager

import com.github.deadizar.aimanager.core.session.SessionManager
import com.github.deadizar.aimanager.core.session.SessionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AiManagerPluginTest {

    @Test
    fun sessionManagerCreatesAndClosesSessions() {
        val manager = SessionManager(SessionRepository(Files.createTempDirectory("aimanager-plugin-test")))

        val created = manager.newSession("Smoke test session").getOrThrow()
        assertNotNull(manager.activeSession)
        assertEquals("Smoke test session", created.title)

        assertTrue(manager.closeSession().isSuccess)
        assertEquals(null, manager.activeSession)
    }
}
