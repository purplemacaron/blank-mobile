package dev.blank.mobile.agent

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class StoredMessage(
    val role: String,
    val content: String,
    val reasoning: String = "",
)

@Serializable
data class Session(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<StoredMessage> = emptyList(),
)

data class SessionMeta(val id: String, val title: String, val updatedAt: Long)

fun titleFor(messages: List<StoredMessage>): String {
    val first = messages.firstOrNull { it.role == "user" }?.content?.trim() ?: return ""
    return if (first.length > 40) first.take(40) + "…" else first
}

class SessionStore(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        dir.mkdirs()
    }

    fun list(): List<SessionMeta> =
        dir.listFiles { file -> file.name.endsWith(".json") }
            ?.mapNotNull { file ->
                runCatching {
                    val session = json.decodeFromString<Session>(file.readText())
                    SessionMeta(session.id, session.title, session.updatedAt)
                }.getOrNull()
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()

    fun load(id: String): Session? {
        val file = fileFor(id)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<Session>(file.readText()) }.getOrNull()
    }

    fun save(session: Session) {
        val target = fileFor(session.id)
        val tmp = File(dir, "${session.id}.json.tmp")
        tmp.writeText(json.encodeToString(session))
        if (!tmp.renameTo(target)) {
            tmp.delete()
            target.writeText(json.encodeToString(session))
        }
    }

    fun delete(id: String) {
        fileFor(id).delete()
    }

    private fun fileFor(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "invalid session id" }
        return File(dir, "$id.json")
    }
}