package dev.blank.mobile.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ModelCatalog {
    fun parse(json: String): List<ModelInfo> {
        val root = Json.parseToJsonElement(json).jsonObject
        val raw = root["data"]?.jsonArray ?: root["models"]?.jsonArray ?: return emptyList()
        return raw.mapNotNull { element ->
            val entry = element.jsonObject
            val id = (entry["id"]?.jsonPrimitive?.content ?: entry["name"]?.jsonPrimitive?.content ?: "")
                .removePrefix("models/")
            if (id.isEmpty()) return@mapNotNull null
            ModelInfo(
                id = id,
                name = entry["display_name"]?.jsonPrimitive?.content
                    ?: entry["displayName"]?.jsonPrimitive?.content
                    ?: entry["name"]?.jsonPrimitive?.content
                    ?: id,
                context = entry["context_window"]?.jsonPrimitive?.intOrNull
                    ?: entry["context_length"]?.jsonPrimitive?.intOrNull
                    ?: entry["inputTokenLimit"]?.jsonPrimitive?.intOrNull
                    ?: 128000,
                vision = visionHint(entry),
            )
        }
    }

    private fun visionHint(entry: JsonObject): Boolean? {
        val modalities = entry["architecture"]?.jsonObject?.get("input_modalities")?.jsonArray
            ?: entry["input_modalities"]?.jsonArray
        if (modalities != null) {
            return modalities.any { it.jsonPrimitive.content.lowercase() == "image" }
        }
        entry["supports_vision"]?.jsonPrimitive?.booleanOrNull?.let { return it }
        val capabilities = entry["capabilities"]?.jsonObject ?: return null
        capabilities["vision"]?.jsonPrimitive?.booleanOrNull?.let { return it }
        return capabilities["image_input"]?.jsonPrimitive?.booleanOrNull
    }
}