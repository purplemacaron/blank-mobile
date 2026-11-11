package dev.blank.mobile.agent

import dev.blank.mobile.provider.ToolCallRef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class ApprovalDecision { ALLOW, ASK }

class ApprovalPolicy {
    private val always = mutableSetOf<String>()

    fun decide(tool: String): ApprovalDecision =
        if (tool in always) ApprovalDecision.ALLOW else default(tool)

    fun allowAlways(tool: String) {
        always += tool
    }

    fun isAlways(tool: String): Boolean = tool in always

    fun decide(call: ToolCallRef): ApprovalDecision = decide(keyFor(call))

    fun allowAlways(call: ToolCallRef) {
        allowAlways(keyFor(call))
    }

    fun isAlways(call: ToolCallRef): Boolean = isAlways(keyFor(call))

    private fun default(tool: String): ApprovalDecision = when (tool) {
        "device_observe", "ask_user" -> ApprovalDecision.ALLOW
        else -> ApprovalDecision.ASK
    }

    companion object {
        fun keyFor(call: ToolCallRef): String {
            if (call.name == "phone_call") {
                val args = runCatching { Json.parseToJsonElement(call.arguments).jsonObject }.getOrNull()
                if (args?.get("action")?.jsonPrimitive?.contentOrNull == "call") {
                    val number = args["number"]?.jsonPrimitive?.contentOrNull?.let { PhoneNumber.normalize(it) }
                    if (number != null) return "phone_call:$number"
                }
            }
            return call.name
        }
    }
}