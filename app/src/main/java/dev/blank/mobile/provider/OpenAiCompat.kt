package dev.blank.mobile.provider

import dev.blank.mobile.agent.ToolSpec
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
    fun body(
        model: String,
        system: String,
        messages: List<ChatMessage>,
        maxTokens: Int? = null,
        tools: List<ToolSpec> = emptyList(),
    ): JsonObject =
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
                        when {
                            message.role == "tool" -> {
                                put("tool_call_id", message.toolCallId ?: "")
                                put("content", message.content)
                            }
                            message.toolCalls.isNotEmpty() -> {
                                put("content", message.content)
                                put("tool_calls", buildJsonArray {
                                    for (call in message.toolCalls) {
                                        add(buildJsonObject {
                                            put("id", call.id)
                                            put("type", "function")
                                            putJsonObject("function") {
                                                put("name", call.name)
                                                put("arguments", call.arguments)
                                            }
                                        })
                                    }
                                })
                            }
                            message.role == "user" && message.images.isNotEmpty() -> {
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
                            }
                            else -> put("content", message.content)
                        }
                    })
                }
            })
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    for (tool in tools) {
                        add(buildJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.parameters)
                            }
                        })
                    }
                })
            }
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

    class Decoder : FrameDecoder {
        private class Partial {
            var id: String? = null
            var name: String? = null
            val args = StringBuilder()
        }

        private val calls = sortedMapOf<Int, Partial>()

        override fun decode(data: String): List<StreamEvent> {
            val events = mutableListOf<StreamEvent>()
            val json = Json.parseToJsonElement(data).jsonObject
            val choice = json["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            choice?.get("delta")?.jsonObject?.get("tool_calls")?.jsonArray?.forEach { element ->
                val obj = element.jsonObject
                val index = obj["index"]?.jsonPrimitive?.intOrNull ?: 0
                val partial = calls.getOrPut(index) { Partial() }
                obj["id"]?.jsonPrimitive?.content?.let { partial.id = it }
                val function = obj["function"]?.jsonObject
                function?.get("name")?.jsonPrimitive?.content?.let { partial.name = it }
                function?.get("arguments")?.jsonPrimitive?.content?.let { partial.args.append(it) }
            }
            if (choice?.get("finish_reason")?.jsonPrimitive?.content == "tool_calls") {
                for ((index, partial) in calls) {
                    events.add(
                        StreamEvent.ToolCall(
                            partial.id ?: "call_$index",
                            partial.name ?: "",
                            partial.args.toString(),
                        ),
                    )
                }
                calls.clear()
            }
            events.addAll(parseFrame(data))
            return events
        }
    }
}