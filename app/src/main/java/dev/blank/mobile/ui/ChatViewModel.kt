package dev.blank.mobile.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import dev.blank.mobile.R
import dev.blank.mobile.AppState
import dev.blank.mobile.agent.AgentLoop
import dev.blank.mobile.agent.AgentRunService
import dev.blank.mobile.agent.ApprovalDecision
import dev.blank.mobile.agent.ApprovalPolicy
import dev.blank.mobile.agent.Session
import dev.blank.mobile.agent.SessionStore
import dev.blank.mobile.agent.StoredMessage
import dev.blank.mobile.agent.Tools
import dev.blank.mobile.agent.titleFor
import dev.blank.mobile.data.ConfigStore
import dev.blank.mobile.device.AppControl
import dev.blank.mobile.device.BlankAccessibilityService
import dev.blank.mobile.device.CallControl
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class UiMessage(val role: String, val content: String, val reasoning: String = "")

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    val config = ConfigStore(app)
    val sessions = SessionStore(File(app.filesDir, "sessions"))
    private val http = HttpProvider()
    private val policy = ApprovalPolicy()
    private val calls = CallControl(app)
    private val apps = AppControl(app)

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
    var phonePermissionsMissing by mutableStateOf(false)
        private set
    var pickRequest by mutableStateOf(false)
        private set
    var pendingQuestion by mutableStateOf<JsonObject?>(null)
        private set

    private var job: Job? = null
    private var createdAt = System.currentTimeMillis()
    private var approvalDeferred: CompletableDeferred<Boolean>? = null
    private var installDeferred: CompletableDeferred<Uri?>? = null
    private var questionDeferred: CompletableDeferred<String?>? = null
    private var pendingShot: ImageAttachment? = null

    init {
        refreshPermissions()
        AgentRunService.stopCallback = { stop() }
        viewModelScope.launch {
            BlankAccessibilityService.connected.collect { accessibilityConnected = it }
        }
    }

    override fun onCleared() {
        AgentRunService.stopCallback = null
        AgentRunService.stop(getApplication())
    }

    fun refreshPermissions() {
        phonePermissionsMissing = !hasPermission(Manifest.permission.CALL_PHONE) ||
            !hasPermission(Manifest.permission.ANSWER_PHONE_CALLS) ||
            !hasPermission(Manifest.permission.READ_PHONE_STATE)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), permission) == PackageManager.PERMISSION_GRANTED

    fun send(text: String) {
        if (streaming || text.isBlank() || !config.configured) return
        error = null
        val trimmed = text.trim()
        val history = (messages.filter { it.role == "user" || (it.role == "assistant" && it.content.isNotBlank()) } + UiMessage("user", trimmed))
            .map { ChatMessage(it.role, it.content) }
        messages = messages + UiMessage("user", trimmed) + UiMessage("assistant", "")
        streaming = true
        if (accessibilityConnected) AgentRunService.start(getApplication())
        job = viewModelScope.launch {
            try {
                if (accessibilityConnected) runAgent(history) else runChat(history)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = getApplication<Application>().getString(R.string.error_provider, e.message ?: "unknown")
            } finally {
                streaming = false
                pendingApproval = null
                approvalDeferred = null
                pickRequest = false
                installDeferred = null
                pendingQuestion = null
                questionDeferred = null
                AgentRunService.pendingAttention = null
                AgentRunService.stop(getApplication())
                AgentRunService.cancelAttention(getApplication())
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
            ask = ::askUser,
            onText = { streamIntoAssistant(text = it) },
            onReasoning = { streamIntoAssistant(reasoning = it) },
            onToolStart = { call ->
                messages = messages + UiMessage("tool", describeCall(call))
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
            "phone_call" -> executePhone(validated)
            "app_control" -> {
                val pkg = validated["package"]?.jsonPrimitive?.content.orEmpty()
                when (validated["action"]?.jsonPrimitive?.content) {
                    "list" -> apps.list()
                    "launch" -> apps.launch(pkg)
                    "install" -> {
                        val path = validated["path"]?.jsonPrimitive?.content
                        if (path != null) apps.install(path) else installViaPicker()
                    }
                    "uninstall" -> apps.uninstall(pkg)
                    else -> apps.forceStop(pkg)
                }
            }
            else -> "Unknown tool: ${call.name}"
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        "Error: ${e.message}"
    }

    private suspend fun executePhone(args: JsonObject): String {
        val number = args["number"]?.jsonPrimitive?.content.orEmpty()
        return when (args["action"]?.jsonPrimitive?.content) {
            "dial" -> calls.dial(number)
            "call" ->
                if (hasPermission(Manifest.permission.CALL_PHONE)) calls.call(number)
                else "Permission required: CALL_PHONE. Ask the user to grant the phone permission, then retry."
            "answer" -> calls.answer()
            "end" -> calls.end()
            else -> calls.state()
        }
    }

    private suspend fun installViaPicker(): String {
        val deferred = CompletableDeferred<Uri?>()
        installDeferred = deferred
        pickRequest = true
        val uri = deferred.await()
        pickRequest = false
        installDeferred = null
        if (uri == null) return "Install cancelled by the user"
        val app = getApplication<Application>()
        val target = File(app.cacheDir, "picked.apk")
        return try {
            val input = app.contentResolver.openInputStream(uri) ?: return "Cannot read the picked file"
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
            apps.install(target.absolutePath)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun completeInstallPick(uri: Uri?) {
        installDeferred?.complete(uri)
    }

    private suspend fun askUser(call: ToolCallRef): String {
        val args = Tools.validate("ask_user", Json.parseToJsonElement(call.arguments.ifBlank { "{}" }).jsonObject)
        val deferred = CompletableDeferred<String?>()
        questionDeferred = deferred
        pendingQuestion = args
        AgentRunService.pendingAttention = R.string.notification_question_title to R.string.notification_question_text
        if (!AppState.foreground) {
            AgentRunService.notifyAttention(getApplication(), R.string.notification_question_title, R.string.notification_question_text)
        }
        val choice = deferred.await()
        pendingQuestion = null
        questionDeferred = null
        AgentRunService.pendingAttention = null
        AgentRunService.cancelAttention(getApplication())
        return if (choice == null) "User dismissed the question." else "User chose: $choice"
    }

    fun answerQuestion(choice: String) {
        questionDeferred?.complete(choice)
    }

    fun describeCall(call: ToolCallRef): String {
        val args = try {
            Json.parseToJsonElement(call.arguments.ifBlank { "{}" }).jsonObject
        } catch (_: Exception) {
            return call.name + " " + call.arguments.take(160)
        }
        return (call.name + " " + Tools.redact(call.name, args)).take(200)
    }

    private suspend fun approveTool(call: ToolCallRef): Boolean {
        if (policy.decide(call) == ApprovalDecision.ALLOW) return true
        val deferred = CompletableDeferred<Boolean>()
        approvalDeferred = deferred
        pendingApproval = call
        AgentRunService.pendingAttention = R.string.notification_approval_title to R.string.notification_approval_text
        if (!AppState.foreground) {
            AgentRunService.notifyAttention(getApplication(), R.string.notification_approval_title, R.string.notification_approval_text)
        }
        val allowed = deferred.await()
        pendingApproval = null
        approvalDeferred = null
        AgentRunService.pendingAttention = null
        AgentRunService.cancelAttention(getApplication())
        return allowed
    }

    fun decideApproval(allow: Boolean, always: Boolean = false) {
        val call = pendingApproval ?: return
        if (allow && always) policy.allowAlways(call)
        approvalDeferred?.complete(allow)
    }

    fun stop() {
        approvalDeferred?.complete(false)
        installDeferred?.complete(null)
        questionDeferred?.complete(null)
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
                "phone_call handles dial/call/answer/end/state (number required for dial and call). " +
                "app_control handles launch/list/install/uninstall/force_stop. " +
                "For install, omit path to open the file chooser so the user picks the APK; only pass a path when the file is already readable by the app. " +
                "System install/uninstall confirmations belong to the user: never tap their OK/Install/Update buttons yourself; after opening one, tell the user to approve it on screen. " +
                "Use ask_user to ask the user a multiple-choice question when you need a decision; wait for their answer. " +
                "Take small steps and verify the result of each action. " +
                "When the task is complete, reply with a brief summary in the user's language and no tool calls."
    }
}