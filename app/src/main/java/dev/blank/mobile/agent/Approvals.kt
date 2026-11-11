package dev.blank.mobile.agent

enum class ApprovalDecision { ALLOW, ASK }

class ApprovalPolicy {
    private val always = mutableSetOf<String>()

    fun decide(tool: String): ApprovalDecision =
        if (tool in always) ApprovalDecision.ALLOW else default(tool)

    fun allowAlways(tool: String) {
        always += tool
    }

    fun isAlways(tool: String): Boolean = tool in always

    private fun default(tool: String): ApprovalDecision = when (tool) {
        "device_observe", "ask_user" -> ApprovalDecision.ALLOW
        else -> ApprovalDecision.ASK
    }
}