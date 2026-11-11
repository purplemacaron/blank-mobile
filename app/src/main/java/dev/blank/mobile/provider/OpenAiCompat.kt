package dev.blank.mobile.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object OpenAiCompat {
    fun body(model: String, system: String, messages: List<ChatMessage>, maxTokens: Int? = null): JsonObject =
        buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", system)
                })
                for (message in messages) {
                    add(buildJsonObject {
                        put("role", message.role)
                        if (message.role == "user" && message.images.isNotEmpty()) {
                            put("content", buildJsonArray {
                                if (message.content.isNotEmpty()) {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", message.content)
                                    })
                                }
                                for (image in message.images) {
                                    add(buildJsonObject {
                                        put("type", "image_url")
                                        putJsonObject("image_url") {
                                            put("url", "data:${image.mime};base64,${image.data}")
                                        }
                                    })
                                }
                            })
                        } else {
                            put("content", message.content)
                        }
                    })
                }
            })
            put("stream", true)
            putJsonObject("stream_options") { put("include_usage", true) }
            if (maxTokens != null) put("max_tokens", maxTokens)
        }

    fun parseFrame(data: String): List<StreamEvent> {
        val json = Json.parseToJsonElement(data).jsonObject
        json["error"]?.let { error ->
            val message = error.jsonObject["message"]?.jsonPrimitive?.content ?: "Provider error"
            throw ProviderException(message)
        }
        val events = mutableListOf<StreamEvent>()
        json["usage"]?.jsonObject?.let { usage ->
            events.add(
                StreamEvent.Usage(
                    usage["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: usage["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                    usage["completion_tokens"]?.jsonPrimitive?.intOrNull ?: usage["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                ),
            )
        }
        val choice = json["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return events
        val delta = choice["delta"]?.jsonObject
        if (delta != null) {
            delta["reasoning_content"]?.jsonPrimitive?.content?.let { events.add(StreamEvent.Reasoning(it)) }
                ?: delta["reasoning"]?.jsonPrimitive?.content?.let { events.add(StreamEvent.Reasoning(it)) }
            delta["content"]?.jsonPrimitive?.content?.let { events.add(StreamEvent.Text(it)) }
        }
        if (choice["finish_reason"]?.jsonPrimitive?.content != null) events.add(StreamEvent.Done(true))
        return events
    }
}