package dev.blank.mobile.provider

data class ImageAttachment(val mime: String, val data: String)

data class ToolCallRef(val id: String, val name: String, val arguments: String)

data class ChatMessage(
    val role: String,
    val content: String = "",
    val images: List<ImageAttachment> = emptyList(),
    val toolCalls: List<ToolCallRef> = emptyList(),
    val toolCallId: String? = null,
)

sealed interface StreamEvent {
    data class Text(val text: String) : StreamEvent
    data class Reasoning(val text: String) : StreamEvent
    data class Usage(val input: Int, val output: Int) : StreamEvent
    data class ToolCall(val id: String, val name: String, val arguments: String) : StreamEvent
    data class Done(val finished: Boolean) : StreamEvent
}

interface FrameDecoder {
    fun decode(data: String): List<StreamEvent>
}

class ProviderException(message: String, val status: Int? = null) : Exception(message)

data class ModelInfo(
    val id: String,
    val name: String,
    val context: Int,
    val vision: Boolean?,
)