package dev.blank.mobile.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class ToolSpec(val name: String, val description: String, val parameters: JsonObject)

object Tools {
    val all: List<ToolSpec> = listOf(
        ToolSpec(
            "device_observe",
            "Read the current screen as a compact indexed UI tree. Optionally attach a screenshot for vision models.",
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("screenshot") {
                        put("type", "boolean")
                        put("description", "Attach a screenshot image.")
                    }
                }
            },
        ),
        ToolSpec(
            "device_act",
            "Perform one device action. Prefer selector (text/desc/id/index from the last device_observe) over raw coordinates. Korean text input is supported via type.",
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("action") {
                        put("type", "string")
                        putJsonArray("enum") {
                            listOf("tap", "long_press", "swipe", "type", "key", "open_url", "wait").forEach { add(it) }
                        }
                    }
                    putJsonObject("selector") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("text") { put("type", "string") }
                            putJsonObject("desc") { put("type", "string") }
                            putJsonObject("id") { put("type", "string") }
                            putJsonObject("index") { put("type", "integer") }
                        }
                    }
                    putJsonObject("x") { put("type", "integer") }
                    putJsonObject("y") { put("type", "integer") }
                    putJsonObject("x2") { put("type", "integer") }
                    putJsonObject("y2") { put("type", "integer") }
                    putJsonObject("direction") {
                        put("type", "string")
                        putJsonArray("enum") { listOf("up", "down", "left", "right").forEach { add(it) } }
                    }
                    putJsonObject("text") { put("type", "string") }
                    putJsonObject("key") {
                        put("type", "string")
                        putJsonArray("enum") { listOf("back", "home", "recents", "enter", "delete").forEach { add(it) } }
                    }
                    putJsonObject("url") { put("type", "string") }
                    putJsonObject("ms") { put("type", "integer") }
                }
                putJsonArray("required") { add("action") }
            },
        ),
    )

    fun validate(name: String, args: JsonObject): JsonObject = when (name) {
        "device_observe" -> buildJsonObject {
            put("screenshot", args["screenshot"]?.jsonPrimitive?.booleanOrNull ?: false)
        }
        "device_act" -> validateAct(args)
        else -> throw IllegalArgumentException("unknown tool: $name")
    }

    private fun validateAct(args: JsonObject): JsonObject {
        val action = args.str("action") ?: throw IllegalArgumentException("action required")
        return buildJsonObject {
            put("action", action)
            when (action) {
                "tap", "long_press" -> {
                    val selector = args["selector"]?.let { validateSelector(it as? JsonObject ?: throw IllegalArgumentException("selector must be object")) }
                    val x = args.int("x")
                    val y = args.int("y")
                    if (selector == null && (x == null || y == null)) {
                        throw IllegalArgumentException("$action requires selector or x/y")
                    }
                    selector?.let { put("selector", it) }
                    if (x != null && y != null) {
                        put("x", x)
                        put("y", y)
                    }
                }
                "swipe" -> {
                    val direction = args.str("direction")
                    val quad = listOf(args.int("x"), args.int("y"), args.int("x2"), args.int("y2"))
                    if (direction == null && quad.any { it == null }) {
                        throw IllegalArgumentException("swipe requires direction or x/y/x2/y2")
                    }
                    if (direction != null) {
                        if (direction !in listOf("up", "down", "left", "right")) {
                            throw IllegalArgumentException("invalid direction: $direction")
                        }
                        put("direction", direction)
                    } else {
                        put("x", quad[0]!!)
                        put("y", quad[1]!!)
                        put("x2", quad[2]!!)
                        put("y2", quad[3]!!)
                    }
                }
                "type" -> {
                    val text = args.str("text")
                    if (text.isNullOrBlank()) throw IllegalArgumentException("type requires text")
                    put("text", text)
                    args["selector"]?.let { put("selector", validateSelector(it as? JsonObject ?: throw IllegalArgumentException("selector must be object"))) }
                }
                "key" -> {
                    val key = args.str("key") ?: throw IllegalArgumentException("key required")
                    if (key !in listOf("back", "home", "recents", "enter", "delete")) {
                        throw IllegalArgumentException("invalid key: $key")
                    }
                    put("key", key)
                }
                "open_url" -> {
                    val url = args.str("url") ?: throw IllegalArgumentException("url required")
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        throw IllegalArgumentException("url must be http(s)")
                    }
                    put("url", url)
                }
                "wait" -> put("ms", (args.int("ms") ?: 1000).coerceIn(100, 10000))
                else -> throw IllegalArgumentException("invalid action: $action")
            }
        }
    }

    private fun validateSelector(selector: JsonObject): JsonObject {
        val text = selector.str("text")
        val desc = selector.str("desc")
        val id = selector.str("id")
        val index = selector.int("index")
        if (text.isNullOrBlank() && desc.isNullOrBlank() && id.isNullOrBlank() && index == null) {
            throw IllegalArgumentException("selector needs text, desc, id or index")
        }
        return buildJsonObject {
            if (!text.isNullOrBlank()) put("text", text)
            if (!desc.isNullOrBlank()) put("desc", desc)
            if (!id.isNullOrBlank()) put("id", id)
            if (index != null) put("index", index)
        }
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.let { if (it.isString) it.content else null }

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
}