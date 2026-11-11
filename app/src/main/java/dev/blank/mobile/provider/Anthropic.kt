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

object Anthropic {
    const val VERSION = "2023-06-01"

    fun body(
        model: String,
        system: String,
        messages: List<ChatMessage>,
        maxTokens: Int = 8192,
        tools: List<ToolSpec> = emptyList(),
    ): JsonObject =
        buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("system", system)
            put("messages", buildJsonArray {
                for (message in messages) {
                    add(buildJsonObject {
                        put("role", if (message.role == "tool") "user" else message.role)
                        when {
                            message.role == "tool" -> {
                                put("content", buildJsonArray {
                                    add(buildJsonObject {
                                        put("type", "tool_result")
                                        put("tool_use_id", message.toolCallId ?: "")
                                        put("content", message.content)
                                    })
                                })
                            }
                            message.toolCalls.isNotEmpty() -> {
                                put("content", buildJsonArray {
                                    if (message.content.isNotEmpty()) {
                                        add(buildJsonObject {
                                            put("type", "text")
                                            put("text", message.content)
                                        })
                                    }
                                    for (call in message.toolCalls) {
                                        add(buildJsonObject {
                                            put("type", "tool_use")
                                            put("id", call.id)
                                            put("name", call.name)
                                            put("input", parseArgs(call.arguments))
                                        })
                                    }
                                })
                            }
                            message.role == "user" -> {
                                if (message.images.isEmpty()) {
                                    put("content", message.content)
                                } else {
                                    put("content", buildJsonArray {
                                        if (message.content.isNotEmpty()) {
                                            add(buildJsonObject {
                                                put("type", "text")
                                                put("text", message.content)
                                            })
                                        }
                                        for (image in message.images) {
                                            add(buildJsonObject {
                                                put("type", "image")
                                                putJsonObject("source") {
                                                    put("type", "base64")
                                                    put("media_type", image.mime)
                                                    put("data", image.data)
                                                }
                                            })
                                        }
                                    })
                                }
                            }
                            else -> {
                                put("content", buildJsonArray {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", message.content.ifEmpty { " " })
                                    })
                                })
                            }
                        }
                    })
                }
            })
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    for (tool in tools) {
                        add(buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", tool.parameters)
                        })
                    }
                })
            }
            put("stream", true)
        }

    private fun parseArgs(arguments: String): JsonObject =
        runCatching { Json.parseToJsonElement(arguments).jsonObject }.getOrDefault(buildJsonObject {})

    fun parseFrame(data: String): List<StreamEvent> {
        val json = Json.parseToJsonElement(data).jsonObject
        val events = mutableListOf<StreamEvent>()
        when (json["type"]?.jsonPrimitive?.content) {
            "error" -> {
                val message = json["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "Provider error"
                throw ProviderException(message)
            }
            "message_start" -> {
                val input = json["message"]?.jsonObject?.get("usage")?.jsonObject?.get("input_tokens")?.jsonPrimitive?.intOrNull ?: 0
                if (input > 0) events.add(StreamEvent.Usage(input, 0))
            }
            "content_block_delta" -> {
                val delta = json["delta"]?.jsonObject ?: return events
                when (delta["type"]?.jsonPrimitive?.content) {
                    "text_delta" -> delta["text"]?.jsonPrimitive?.content?.let { events.add(StreamEvent.Text(it)) }
                    "thinking_delta" -> delta["thinking"]?.jsonPrimitive?.content?.let { events.add(StreamEvent.Reasoning(it)) }
                }
            }
            "message_delta" -> {
                json["usage"]?.jsonObject?.get("output_tokens")?.jsonPrimitive?.intOrNull?.let {
                    if (it > 0) events.add(StreamEvent.Usage(0, it))
                }
                if (json["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.content != null) {
                    events.add(StreamEvent.Done(true))
                }
            }
        }
        return events
    }

    class Decoder : FrameDecoder {
        private var currentId: String? = null
        private var currentName: String? = null
        private val args = StringBuilder()

        override fun decode(data: String): List<StreamEvent> {
            if (data.isEmpty() || data == "[DONE]") return emptyList()
            val json = Json.parseToJsonElement(data).jsonObject
            val events = mutableListOf<StreamEvent>()
            when (json["type"]?.jsonPrimitive?.content) {
                "content_block_start" -> {
                    val block = json["content_block"]?.jsonObject
                    if (block?.get("type")?.jsonPrimitive?.content == "tool_use") {
                        currentId = block["id"]?.jsonPrimitive?.content
                        currentName = block["name"]?.jsonPrimitive?.content
                        args.clear()
                    }
                }
                "content_block_delta" -> {
                    val delta = json["delta"]?.jsonObject
                    if (delta?.get("type")?.jsonPrimitive?.content == "input_json_delta") {
                        delta["partial_json"]?.jsonPrimitive?.content?.let { args.append(it) }
                    }
                }
                "content_block_stop" -> {
                    currentId?.let { id ->
                        events.add(StreamEvent.ToolCall(id, currentName ?: "", args.toString()))
                    }
                    currentId = null
                    currentName = null
                    args.clear()
                }
            }
            events.addAll(parseFrame(data))
            return events
        }
    }
}