package dev.blank.mobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class ProviderDef(
    val id: String,
    val label: String,
    val protocol: String,
    val defaultBaseUrl: String,
    val needsKey: Boolean,
)

object Providers {
    val all = listOf(
        ProviderDef("openai", "OpenAI", "openai", "https://api.openai.com/v1", true),
        ProviderDef("anthropic", "Anthropic", "anthropic", "https://api.anthropic.com", true),
        ProviderDef("gemini", "Gemini", "gemini", "https://generativelanguage.googleapis.com/v1beta", true),
        ProviderDef("ollama", "Ollama (LAN)", "openai", "http://10.0.2.2:11434/v1", false),
        ProviderDef("lmstudio", "LM Studio (LAN)", "openai", "http://10.0.2.2:1234/v1", false),
        ProviderDef("custom", "Custom (OpenAI)", "openai", "", true),
    )

    fun byId(id: String): ProviderDef = all.firstOrNull { it.id == id } ?: all[0]
}

class ConfigStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "blank_mobile_config",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var providerId: String
        get() = prefs.getString("provider", "openai") ?: "openai"
        set(value) = prefs.edit().putString("provider", value).apply()

    var modelId: String
        get() = prefs.getString("model", "") ?: ""
        set(value) = prefs.edit().putString("model", value).apply()

    fun provider(): ProviderDef = Providers.byId(providerId)

    fun baseUrl(def: ProviderDef): String =
        prefs.getString("base_${def.id}", null)?.ifBlank { null } ?: def.defaultBaseUrl

    fun setBaseUrl(id: String, value: String) {
        prefs.edit().putString("base_$id", value.trim()).apply()
    }

    fun apiKey(id: String): String = prefs.getString("key_$id", "") ?: ""

    fun setApiKey(id: String, value: String) {
        prefs.edit().putString("key_$id", value.trim()).apply()
    }

    fun maskedKey(id: String): String = maskKey(apiKey(id))

    val configured: Boolean
        get() {
            val def = provider()
            return modelId.isNotBlank() && baseUrl(def).isNotBlank() && (!def.needsKey || apiKey(def.id).isNotBlank())
        }
}