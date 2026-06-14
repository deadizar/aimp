package com.github.deadizar.aimanager.core.session

import com.github.deadizar.aimanager.core.model.Session
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.use

class SessionRepository(
    private val rootDirectory: Path = defaultRootDirectory(),
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun save(session: Session): Result<Session> = runCatching {
        Files.createDirectories(rootDirectory)
        Files.writeString(sessionPath(session.id), json.encodeToString(Session.serializer(), session), StandardCharsets.UTF_8)
        session
    }

    fun load(sessionId: String): Result<Session?> = runCatching {
        val sessionFile = sessionPath(sessionId)
        if (!Files.exists(sessionFile)) {
            return@runCatching null
        }

        json.decodeFromString(Session.serializer(), Files.readString(sessionFile, StandardCharsets.UTF_8))
    }

    fun list(): Result<List<Session>> = runCatching {
        if (!Files.isDirectory(rootDirectory)) {
            return@runCatching emptyList()
        }

        Files.list(rootDirectory).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(SESSION_EXTENSION) }
                .map { path -> json.decodeFromString(Session.serializer(), Files.readString(path, StandardCharsets.UTF_8)) }
                .sorted(compareByDescending<Session> { it.updatedAt }.thenByDescending { it.createdAt })
                .toList()
        }
    }

    fun delete(sessionId: String): Result<Boolean> = runCatching {
        Files.deleteIfExists(sessionPath(sessionId))
    }

    private fun sessionPath(sessionId: String): Path = rootDirectory.resolve("$sessionId$SESSION_EXTENSION")

    companion object {
        private const val SESSION_EXTENSION = ".json"

        fun defaultRootDirectory(): Path = Paths.get(System.getProperty("user.home"), ".config", "aimanager", "sessions")
    }
}
