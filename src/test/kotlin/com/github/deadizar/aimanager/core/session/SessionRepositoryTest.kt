package com.github.deadizar.aimanager.core.session

import com.github.deadizar.aimanager.core.model.Message
import com.github.deadizar.aimanager.core.model.MessageRole
import com.github.deadizar.aimanager.core.model.Session
import com.github.deadizar.aimanager.core.model.TokenUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SessionRepositoryTest {

    @Test
    fun saveLoadAndDeleteSession() {
        val repository = SessionRepository(Files.createTempDirectory("aimanager-session-repo"))
        val session = sampleSession(id = "session-1", updatedAt = 2L)

        assertTrue(repository.save(session).isSuccess)
        assertEquals(session, repository.load(session.id).getOrThrow())
        assertTrue(repository.delete(session.id).getOrThrow())
        assertNull(repository.load(session.id).getOrThrow())
    }

    @Test
    fun listSessionsReturnsMostRecentFirst() {
        val repository = SessionRepository(Files.createTempDirectory("aimanager-session-repo-list"))
        val older = sampleSession(id = "older", updatedAt = 1L)
        val newer = sampleSession(id = "newer", updatedAt = 10L)

        assertTrue(repository.save(older).isSuccess)
        assertTrue(repository.save(newer).isSuccess)

        val sessions = repository.list().getOrThrow()
        assertEquals(listOf(newer, older), sessions)
    }

    @Test
    fun deleteMissingSessionReturnsFalse() {
        val repository = SessionRepository(Files.createTempDirectory("aimanager-session-repo-delete-missing"))

        assertFalse(repository.delete("missing").getOrThrow())
    }

    private fun sampleSession(id: String, updatedAt: Long): Session = Session(
        id = id,
        title = "Session $id",
        agentId = "agent-1",
        messages = listOf(
            Message(
                role = MessageRole.USER,
                content = "Hello",
                timestamp = 1L,
                usage = TokenUsage(promptTokens = 5, completionTokens = 7),
            ),
            Message(
                role = MessageRole.ASSISTANT,
                content = "World",
                timestamp = 2L,
            ),
        ),
        createdAt = 1L,
        updatedAt = updatedAt,
    )
}
