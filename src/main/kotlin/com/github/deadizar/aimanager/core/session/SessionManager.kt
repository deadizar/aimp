package com.github.deadizar.aimanager.core.session

import com.github.deadizar.aimanager.core.model.Session
import java.util.UUID

class SessionManager(
    private val repository: SessionRepository = SessionRepository(),
) {

    @Volatile
    var activeSession: Session? = null
        private set

    fun newSession(title: String, agentId: String? = null): Result<Session> = runCatching {
        val now = System.currentTimeMillis()
        val session = Session(
            id = UUID.randomUUID().toString(),
            title = title,
            agentId = agentId,
            createdAt = now,
            updatedAt = now,
        )
        repository.save(session).getOrThrow()
        activeSession = session
        session
    }

    fun loadSession(sessionId: String): Result<Session?> = repository.load(sessionId).onSuccess { activeSession = it }

    fun listSessions(): Result<List<Session>> = repository.list()

    fun replaceActiveSession(session: Session): Result<Session> = runCatching {
        activeSession = session
        session
    }

    fun saveActiveSession(): Result<Session> = runCatching {
        val session = activeSession ?: error("No active session")
        repository.save(session).getOrThrow()
    }

    fun closeSession(): Result<Unit> = runCatching {
        activeSession = null
    }

    fun renameSession(sessionId: String, newTitle: String): Result<Session> = runCatching {
        val current = repository.load(sessionId).getOrThrow() ?: error("Session not found: $sessionId")
        val updated = current.copy(title = newTitle, updatedAt = System.currentTimeMillis())
        repository.save(updated).getOrThrow()
        if (activeSession?.id == sessionId) activeSession = updated
        updated
    }

    fun togglePin(sessionId: String): Result<Session> = runCatching {
        val current = repository.load(sessionId).getOrThrow() ?: error("Session not found: $sessionId")
        val updated = current.copy(pinned = !current.pinned, updatedAt = System.currentTimeMillis())
        repository.save(updated).getOrThrow()
        if (activeSession?.id == sessionId) activeSession = updated
        updated
    }

    fun deleteSession(sessionId: String): Result<Unit> = runCatching {
        repository.delete(sessionId).getOrThrow()
        if (activeSession?.id == sessionId) activeSession = null
    }

    fun searchSessions(query: String): Result<List<Session>> = runCatching {
        repository.list().getOrThrow().filter { it.title.contains(query, ignoreCase = true) }
    }

    fun truncateAfterMessage(session: Session, messageId: String): Result<Session> = runCatching {
        val messages = session.messages.takeWhile { it.id != messageId }
        session.copy(messages = messages, updatedAt = System.currentTimeMillis())
    }
}

