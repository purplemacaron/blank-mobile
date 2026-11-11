package dev.blank.mobile.agent

import dev.blank.mobile.provider.ChatMessage
import dev.blank.mobile.provider.StreamEvent
import dev.blank.mobile.provider.ToolCallRef
import kotlinx.coroutines.flow.Flow

class AgentLoop(
    private val stream: (List<ChatMessage>, List<ToolSpec>) -> Flow<StreamEvent>,
    private val execute: suspend (ToolCallRef) -> String,
    private val approve: suspend (ToolCallRef) -> Boolean,
    private val onText: (String) -> Unit = {},
    private val onReasoning: (String) -> Unit = {},
    private val onToolStart: (ToolCallRef) -> Unit = {},
    private val onToolResult: (ToolCallRef, String) -> Unit = { _, _ -> },
    private val onLimit: () -> Unit = {},
    private val maxSteps: Int = 12,
) {
    suspend fun run(history: List<ChatMessage>): String {
        val messages = history.toMutableList()
        var lastText = ""
        repeat(maxSteps) {
            val text = StringBuilder()
            val calls = mutableListOf<ToolCallRef>()
            stream(messages, Tools.all).collect { event ->
                when (event) {
                    is StreamEvent.Text -> {
                        text.append(event.text)
                        onText(event.text)
                    }
                    is StreamEvent.Reasoning -> onReasoning(event.text)
                    is StreamEvent.ToolCall -> calls += ToolCallRef(event.id, event.name, event.arguments)
                    else -> {}
                }
            }
            lastText = text.toString()
            messages += ChatMessage("assistant", lastText, toolCalls = calls)
            if (calls.isEmpty()) return lastText
            for (call in calls) {
                onToolStart(call)
                val result = if (approve(call)) execute(call) else "User denied this action."
                onToolResult(call, result)
                messages += ChatMessage("tool", result, toolCallId = call.id)
            }
        }
        onLimit()
        return lastText
    }
}