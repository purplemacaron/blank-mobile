package dev.blank.mobile.agent

data class UiRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class UiNode(
    val text: String = "",
    val desc: String = "",
    val resourceId: String = "",
    val className: String = "",
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val bounds: UiRect? = null,
)

object TreeSerializer {
    fun serialize(nodes: List<UiNode>, max: Int = 120): String {
        val interesting = nodes.filter {
            it.clickable || it.editable || it.text.isNotBlank() || it.desc.isNotBlank()
        }
        if (interesting.isEmpty()) return "(empty screen)"
        return interesting.take(max).mapIndexed { index, node ->
            buildString {
                append("[$index] ")
                append(node.className.substringAfterLast('.').ifBlank { "Node" })
                if (node.text.isNotBlank()) append(" \"${node.text}\"")
                if (node.desc.isNotBlank() && node.desc != node.text) append(" desc=\"${node.desc}\"")
                if (node.resourceId.isNotBlank()) append(" id=${node.resourceId}")
                node.bounds?.let { append(" (${it.left},${it.top},${it.right},${it.bottom})") }
                val flags = listOfNotNull(
                    "clickable".takeIf { node.clickable },
                    "editable".takeIf { node.editable },
                )
                if (flags.isNotEmpty()) append(" ${flags.joinToString(",")}")
            }
        }.joinToString("\n")
    }
}