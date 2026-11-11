package dev.blank.mobile.provider

data class ImageAttachment(val mime: String, val data: String)

data class ChatMessage(
    val role: String,
    val content: String = "",
    val images: List<ImageAttachment> = emptyList(),
)

sealed interface StreamEvent {
    data class Text(val text: String) : StreamEvent
    data class Reasoning(val text: String) : StreamEvent
    data class Usage(val input: Int, val output: Int) : StreamEvent
    data class Done(val finished: Boolean) : StreamEvent
}

class ProviderException(message: String, val status: Int? = null) : Exception(message)

data class ModelInfo(
    val id: String,
    val name: String,
    val context: Int,
    val vision: Boolean?,
)