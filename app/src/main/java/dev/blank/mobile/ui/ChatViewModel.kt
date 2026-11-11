package dev.blank.mobile.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.blank.mobile.agent.Session
import dev.blank.mobile.agent.SessionStore
import dev.blank.mobile.agent.StoredMessage
import dev.blank.mobile.agent.titleFor
import dev.blank.mobile.data.ConfigStore
import dev.blank.mobile.provider.ChatMessage
import dev.blank.mobile.provider.HttpProvider
import dev.blank.mobile.provider.StreamEvent
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class UiMessage(val role: String, val content: String, val reasoning: String = "")

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    val config = ConfigStore(app)
    val sessions = SessionStore(File(app.filesDir, "sessions"))
    private val http = HttpProvider()

    var messages by mutableStateOf(listOf<UiMessage>())
        private set
    var streaming by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var sessionId by mutableStateOf<String?>(null)
        private set

    private var job: Job? = null
    private var createdAt = System.currentTimeMillis()

    fun send(text: String) {
        if (streaming || text.isBlank() || !config.configured) return
        error = null
        val history = (messages + UiMessage("user", text.trim())).map { ChatMessage(it.role, it.content) }
        messages = messages + UiMessage("user", text.trim()) + UiMessage("assistant", "")
        streaming = true
        job = viewModelScope.launch {
            try {
                val def = config.provider()
                http.stream(
                    protocol = def.protocol,
                    baseUrl = config.baseUrl(def),
                    apiKey = config.apiKey(def.id).ifBlank { null },
                    model = config.modelId,
                    system = SYSTEM,
                    messages = history,
                ).collect { event ->
                    when (event) {
                        is StreamEvent.Text -> updateLast { it.copy(content = it.content + event.text) }
                        is StreamEvent.Reasoning -> updateLast { it.copy(reasoning = it.reasoning + event.text) }
                        else -> {}
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Provider error"
            } finally {
                streaming = false
                persist()
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun newSession() {
        stop()
        messages = emptyList()
        sessionId = null
        error = null
        createdAt = System.currentTimeMillis()
    }

    fun openSession(id: String) {
        stop()
        val session = sessions.load(id) ?: return
        sessionId = session.id
        createdAt = session.createdAt
        messages = session.messages.map { UiMessage(it.role, it.content, it.reasoning) }
        error = null
    }

    fun clearError() {
        error = null
    }

    private fun updateLast(transform: (UiMessage) -> UiMessage) {
        val list = messages.toMutableList()
        if (list.isEmpty()) return
        list[list.lastIndex] = transform(list[list.lastIndex])
        messages = list
    }

    private fun persist() {
        val stored = messages
            .filter { it.content.isNotBlank() || it.reasoning.isNotBlank() }
            .map { StoredMessage(it.role, it.content, it.reasoning) }
        if (stored.isEmpty()) return
        val id = sessionId ?: UUID.randomUUID().toString().also { sessionId = it }
        val existing = sessions.load(id)
        sessions.save(
            Session(
                id = id,
                title = existing?.title?.ifBlank { null } ?: titleFor(stored).ifBlank { "Untitled" },
                createdAt = existing?.createdAt ?: createdAt,
                updatedAt = System.currentTimeMillis(),
                messages = stored,
            ),
        )
    }

    companion object {
        private const val SYSTEM =
            "You are blank mobile, a concise on-device assistant. Reply in the user's language."
    }
}