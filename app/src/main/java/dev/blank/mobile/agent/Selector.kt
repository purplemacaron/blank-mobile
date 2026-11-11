package dev.blank.mobile.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

object Selector {
    fun match(nodes: List<UiNode>, selector: JsonObject): UiNode? {
        val text = selector["text"]?.jsonPrimitive?.takeIf { it.isString }?.content
        if (!text.isNullOrBlank()) {
            nodes.firstOrNull { it.text == text }?.let { return it }
            nodes.firstOrNull { it.text.contains(text) }?.let { return it }
        }
        val desc = selector["desc"]?.jsonPrimitive?.takeIf { it.isString }?.content
        if (!desc.isNullOrBlank()) {
            nodes.firstOrNull { it.desc == desc }?.let { return it }
            nodes.firstOrNull { it.desc.contains(desc) }?.let { return it }
        }
        val id = selector["id"]?.jsonPrimitive?.takeIf { it.isString }?.content
        if (!id.isNullOrBlank()) {
            nodes.firstOrNull { it.resourceId == id || it.resourceId.endsWith("/$id") || it.resourceId.endsWith(id) }?.let { return it }
        }
        val index = selector["index"]?.jsonPrimitive?.intOrNull
        if (index != null) return nodes.getOrNull(index)
        return null
    }
}