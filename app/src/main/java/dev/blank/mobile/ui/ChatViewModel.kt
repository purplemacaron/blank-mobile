package dev.blank.mobile.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.blank.mobile.R
import dev.blank.mobile.agent.AgentLoop
import dev.blank.mobile.agent.ApprovalDecision
import dev.blank.mobile.agent.ApprovalPolicy
import dev.blank.mobile.agent.Session
import dev.blank.mobile.agent.SessionStore
import dev.blank.mobile.agent.StoredMessage
import dev.blank.mobile.agent.Tools
import dev.blank.mobile.agent.titleFor
import dev.blank.mobile.data.ConfigStore
import dev.blank.mobile.device.BlankAccessibilityService
import dev.blank.mobile.device.DeviceBridge
import dev.blank.mobile.provider.ChatMessage
import dev.blank.mobile.provider.HttpProvider
import dev.blank.mobile.provider.ImageAttachment
import dev.blank.mobile.provider.StreamEvent
import dev.blank.mobile.provider.ToolCallRef
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class UiMessage(val role: String, val content: String, val reasoning: String = "")

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    val config = ConfigStore(app)
    val sessions = SessionStore(File(app.filesDir, "sessions"))
    private val http = HttpProvider()
    private val policy = ApprovalPolicy()

    var messages by mutableStateOf(listOf<UiMessage>())
        private set
    var streaming by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var sessionId by mutableStateOf<String?>(null)
        private set
    var accessibilityConnected by mutableStateOf(BlankAccessibilityService.connected.value)
        private set
    var pendingApproval by mutableStateOf<ToolCallRef?>(null)
        private set

    private var job: Job? = null
    private var createdAt = System.currentTimeMillis()
    private var approvalDeferred: CompletableDeferred<Boolean>? = null
    private var pendingShot: ImageAttachment? = null

    init {
        viewModelScope.launch {
            BlankAccessibilityService.connected.collect { accessibilityConnected = it }
        }
    }

    fun send(text: String) {
        if (streaming || text.isBlank() || !config.configured) return
        error = null
        val trimmed = text.trim()
        val history = (messages.filter { it.role == "user" || (it.role == "assistant" && it.content.isNotBlank()) } + UiMessage("user", trimmed))
            .map { ChatMessage(it.role, it.content) }
        messages = messages + UiMessage("user", trimmed) + UiMessage("assistant", "")
        streaming = true
        job = viewModelScope.launch {
            try {
                if (accessibilityConnected) runAgent(history) else runChat(history)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Provider error"
            } finally {
                streaming = false
                pendingApproval = null
                approvalDeferred = null
                persist()
            }
        }
    }

    private suspend fun runChat(history: List<ChatMessage>) {
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
                is StreamEvent.Text -> streamIntoAssistant(text = event.text)
                is StreamEvent.Reasoning -> streamIntoAssistant(reasoning = event.text)
                else -> {}
            }
        }
    }

    private suspend fun runAgent(history: List<ChatMessage>) {
        val def = config.provider()
        val loop = AgentLoop(
            stream = { msgs, tools ->
                val shot = pendingShot
                pendingShot = null
                val requestMessages = if (shot != null) {
                    msgs + ChatMessage("user", "Current screen screenshot:", images = listOf(shot))
                } else {
                    msgs
                }
                http.stream(
                    protocol = def.protocol,
                    baseUrl = config.baseUrl(def),
                    apiKey = config.apiKey(def.id).ifBlank { null },
                    model = config.modelId,
                    system = AGENT_SYSTEM,
                    messages = requestMessages,
                    tools = tools,
                )
            },
            execute = ::executeTool,
            approve = ::approveTool,
            onText = { streamIntoAssistant(text = it) },
            onReasoning = { streamIntoAssistant(reasoning = it) },
            onToolStart = { call ->
                messages = messages + UiMessage("tool", call.name + " " + call.arguments.take(160))
            },
            onToolResult = { _, result -> completeToolCard(result) },
            onLimit = {
                messages = messages + UiMessage("assistant", getApplication<Application>().getString(R.string.agent_step_limit))
            },
        )
        loop.run(history)
    }

    private suspend fun executeTool(call: ToolCallRef): String = try {
        val args = Json.parseToJsonElement(call.arguments.ifBlank { "{}" }).jsonObject
        val validated = Tools.validate(call.name, args)
        when (call.name) {
            "device_observe" -> {
                val observation = DeviceBridge.observe(validated["screenshot"]?.jsonPrimitive?.booleanOrNull ?: false)
                pendingShot = observation.image
                observation.tree
            }
            "device_act" -> DeviceBridge.act(validated)
            else -> "Unknown tool: ${call.name}"
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        "Error: ${e.message}"
    }

    private suspend fun approveTool(call: ToolCallRef): Boolean {
        if (policy.decide(call.name) == ApprovalDecision.ALLOW) return true
        val deferred = CompletableDeferred<Boolean>()
        approvalDeferred = deferred
        pendingApproval = call
        val allowed = deferred.await()
        pendingApproval = null
        approvalDeferred = null
        return allowed
    }

    fun decideApproval(allow: Boolean, always: Boolean = false) {
        val call = pendingApproval ?: return
        if (allow && always) policy.allowAlways(call.name)
        approvalDeferred?.complete(allow)
    }

    fun stop() {
        approvalDeferred?.complete(false)
        job?.cancel()
    }

    fun newSession() {
        stop()
        messages = emptyList()
        sessionId = null
        error = null
        pendingShot = null
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

    private fun streamIntoAssistant(text: String = "", reasoning: String = "") {
        val list = messages.toMutableList()
        val last = list.lastOrNull()
        if (last != null && last.role == "assistant") {
            list[list.lastIndex] = last.copy(content = last.content + text, reasoning = last.reasoning + reasoning)
        } else {
            list += UiMessage("assistant", text, reasoning)
        }
        messages = list
    }

    private fun completeToolCard(result: String) {
        val list = messages.toMutableList()
        val index = list.indexOfLast { it.role == "tool" }
        if (index >= 0) {
            list[index] = list[index].copy(content = list[index].content + "\n→ " + result.take(400))
        }
        messages = list
    }

    private fun persist() {
        val stored = messages
            .filter { it.role == "user" || it.role == "assistant" }
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

        private const val AGENT_SYSTEM =
            "You are blank mobile, an agent that operates this Android phone through tools. " +
                "Workflow: call device_observe to read the screen, then device_act to interact, then observe again to verify. " +
                "Prefer selector (text/desc/id/index from the latest device_observe) over raw coordinates. " +
                "Use action=type for text input; Korean is fully supported. " +
                "Take small steps and verify the result of each action. " +
                "When the task is complete, reply with a brief summary in the user's language and no tool calls."
    }
}