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
    private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

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
        ToolSpec(
            "phone_call",
            "Control phone calls. dial opens the dialer prefilled; call places the call directly (requires user approval per number); answer accepts a ringing call; end hangs up the active call; state reports idle/ringing/offhook.",
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("action") {
                        put("type", "string")
                        putJsonArray("enum") { listOf("dial", "call", "answer", "end", "state").forEach { add(it) } }
                    }
                    putJsonObject("number") { put("type", "string"); put("description", "Phone number, e.g. 010-1234-5678 or +82...") }
                }
                putJsonArray("required") { add("action") }
            },
        ),
        ToolSpec(
            "app_control",
            "Manage apps. launch opens an app; list shows launchable apps with package names; install opens the system install confirmation — omit path to let the user pick the APK with the file chooser, or pass a readable .apk path; uninstall shows the system uninstall confirmation; force_stop opens app info and taps force stop.",
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("action") {
                        put("type", "string")
                        putJsonArray("enum") { listOf("launch", "list", "install", "uninstall", "force_stop").forEach { add(it) } }
                    }
                    putJsonObject("package") { put("type", "string"); put("description", "Android package name, e.g. com.android.settings") }
                    putJsonObject("path") { put("type", "string"); put("description", "Absolute path to a readable .apk file (install only; omit to open the file chooser)") }
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
        "phone_call" -> validatePhone(args)
        "app_control" -> validateApp(args)
        else -> throw IllegalArgumentException("unknown tool: $name")
    }

    fun redact(name: String, args: JsonObject): String {
        if (name != "phone_call") return args.toString()
        val raw = args["number"]?.jsonPrimitive?.let { if (it.isString) it.content else null } ?: return args.toString()
        val normalized = PhoneNumber.normalize(raw) ?: return args.toString()
        return buildJsonObject {
            args.forEach { (key, value) ->
                if (key == "number") put(key, PhoneNumber.mask(normalized)) else put(key, value)
            }
        }.toString()
    }

    private fun validatePhone(args: JsonObject): JsonObject {
        val action = args.str("action") ?: throw IllegalArgumentException("action required")
        if (action !in listOf("dial", "call", "answer", "end", "state")) {
            throw IllegalArgumentException("invalid action: $action")
        }
        return buildJsonObject {
            put("action", action)
            if (action == "dial" || action == "call") {
                val raw = args.str("number") ?: throw IllegalArgumentException("number required")
                put("number", PhoneNumber.normalize(raw) ?: throw IllegalArgumentException("invalid number: $raw"))
            }
        }
    }

    private fun validateApp(args: JsonObject): JsonObject {
        val action = args.str("action") ?: throw IllegalArgumentException("action required")
        return buildJsonObject {
            put("action", action)
            when (action) {
                "list" -> {}
                "launch", "uninstall", "force_stop" -> {
                    val pkg = args.str("package") ?: throw IllegalArgumentException("package required")
                    if (!PACKAGE_NAME.matches(pkg)) throw IllegalArgumentException("invalid package: $pkg")
                    put("package", pkg)
                }
                "install" -> {
                    val path = args.str("path")
                    if (path != null) {
                        if (!path.endsWith(".apk")) throw IllegalArgumentException("path must be an .apk file")
                        put("path", path)
                    }
                }
                else -> throw IllegalArgumentException("invalid action: $action")
            }
        }
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