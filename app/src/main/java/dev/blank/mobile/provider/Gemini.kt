package dev.blank.mobile.provider

import dev.blank.mobile.agent.ToolSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object Gemini {
    fun body(
        model: String,
        system: String,
        messages: List<ChatMessage>,
        maxTokens: Int? = null,
        tools: List<ToolSpec> = emptyList(),
    ): JsonObject {
        val callNames = mutableMapOf<String, String>()
        for (message in messages) {
            for (call in message.toolCalls) callNames[call.id] = call.name
        }
        return buildJsonObject {
            put("contents", buildJsonArray {
                for (message in messages) {
                    add(buildJsonObject {
                        put("role", if (message.role == "assistant") "model" else "user")
                        put("parts", buildJsonArray {
                            when {
                                message.role == "tool" -> {
                                    add(buildJsonObject {
                                        putJsonObject("functionResponse") {
                                            put("name", callNames[message.toolCallId] ?: message.toolCallId ?: "")
                                            put("response", parseArgs(message.content))
                                        }
                                    })
                                }
                                message.toolCalls.isNotEmpty() -> {
                                    if (message.content.isNotEmpty()) {
                                        add(buildJsonObject { put("text", message.content) })
                                    }
                                    for (call in message.toolCalls) {
                                        add(buildJsonObject {
                                            putJsonObject("functionCall") {
                                                put("name", call.name)
                                                put("args", parseArgs(call.arguments))
                                            }
                                        })
                                    }
                                }
                                message.role == "user" -> {
                                    if (message.content.isNotEmpty()) {
                                        add(buildJsonObject { put("text", message.content) })
                                    }
                                    for (image in message.images) {
                                        add(buildJsonObject {
                                            putJsonObject("inlineData") {
                                                put("mimeType", image.mime)
                                                put("data", image.data)
                                            }
                                        })
                                    }
                                    if (message.content.isEmpty() && message.images.isEmpty()) {
                                        add(buildJsonObject { put("text", "") })
                                    }
                                }
                                else -> {
                                    if (message.content.isNotEmpty()) {
                                        add(buildJsonObject { put("text", message.content) })
                                    }
                                }
                            }
                        })
                    })
                }
            })
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    add(buildJsonObject {
                        put("functionDeclarations", buildJsonArray {
                            for (tool in tools) {
                                add(buildJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("parameters", tool.parameters)
                                })
                            }
                        })
                    })
                })
            }
            putJsonObject("systemInstruction") {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", system) })
                })
            }
            putJsonObject("generationConfig") {
                if (maxTokens != null) put("maxOutputTokens", maxTokens)
            }
        }
    }

    private fun parseArgs(arguments: String): JsonObject =
        runCatching { Json.parseToJsonElement(arguments).jsonObject }
            .getOrDefault(buildJsonObject { put("result", arguments) })

    fun parseFrame(data: String): List<StreamEvent> {
        val json = Json.parseToJsonElement(data).jsonObject
        json["error"]?.let { error ->
            val message = error.jsonObject["message"]?.jsonPrimitive?.content ?: "Provider error"
            throw ProviderException(message)
        }
        val events = mutableListOf<StreamEvent>()
        json["usageMetadata"]?.jsonObject?.let { usage ->
            events.add(
                StreamEvent.Usage(
                    usage["promptTokenCount"]?.jsonPrimitive?.intOrNull ?: 0,
                    usage["candidatesTokenCount"]?.jsonPrimitive?.intOrNull ?: 0,
                ),
            )
        }
        val candidate = json["candidates"]?.jsonArray?.firstOrNull()?.jsonObject ?: return events
        for (part in candidate["content"]?.jsonObject?.get("parts")?.jsonArray ?: emptyList()) {
            val obj = part.jsonObject
            val text = obj["text"]?.jsonPrimitive?.content ?: continue
            if (obj["thought"]?.jsonPrimitive?.booleanOrNull == true) events.add(StreamEvent.Reasoning(text))
            else events.add(StreamEvent.Text(text))
        }
        return events
    }

    class Decoder : FrameDecoder {
        override fun decode(data: String): List<StreamEvent> {
            val json = Json.parseToJsonElement(data).jsonObject
            val events = mutableListOf<StreamEvent>()
            val candidate = json["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            for (part in candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: emptyList()) {
                val call = part.jsonObject["functionCall"]?.jsonObject ?: continue
                val name = call["name"]?.jsonPrimitive?.content ?: continue
                events.add(
                    StreamEvent.ToolCall(
                        name,
                        name,
                        (call["args"]?.jsonObject ?: buildJsonObject {}).toString(),
                    ),
                )
            }
            events.addAll(parseFrame(data))
            return events
        }
    }
}