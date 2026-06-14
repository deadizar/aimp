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
}

