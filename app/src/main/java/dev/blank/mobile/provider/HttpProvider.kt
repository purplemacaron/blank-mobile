package dev.blank.mobile.provider

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer

class HttpProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .build(),
) {
    suspend fun listModels(protocol: String, baseUrl: String, apiKey: String?): List<ModelInfo> =
        withContext(Dispatchers.IO) {
            val base = baseUrl.trimEnd('/')
            val url = if (protocol == "gemini") "$base/models?pageSize=200" else "$base/models"
            val request = Request.Builder().url(url).get().apply { authHeaders(protocol, apiKey) }.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ProviderException("${response.code} ${response.message}", response.code)
                }
                ModelCatalog.parse(response.body!!.string())
            }
        }

    fun stream(
        protocol: String,
        baseUrl: String,
        apiKey: String?,
        model: String,
        system: String,
        messages: List<ChatMessage>,
        maxTokens: Int? = null,
    ): Flow<StreamEvent> = flow {
        val base = baseUrl.trimEnd('/')
        val body = when (protocol) {
            "anthropic" -> Anthropic.body(model, system, messages, maxTokens ?: 8192)
            "gemini" -> Gemini.body(model, system, messages, maxTokens)
            else -> OpenAiCompat.body(model, system, messages, maxTokens)
        }
        val url = when (protocol) {
            "anthropic" -> "$base/messages"
            "gemini" -> "$base/models/$model:streamGenerateContent?alt=sse"
            else -> "$base/chat/completions"
        }
        val request = Request.Builder().url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .apply { authHeaders(protocol, apiKey) }
            .build()
        val call = client.newCall(request)
        currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        call.execute().use { response ->
            if (!response.isSuccessful) {
                val snippet = runCatching { response.body?.string()?.take(500) }.getOrNull() ?: ""
                throw ProviderException(
                    "${response.code} ${response.message}${if (snippet.isNotBlank()) " — $snippet" else ""}",
                    response.code,
                )
            }
            val parser = SseParser()
            val source = response.body!!.source()
            val buffer = Buffer()
            var done = false
            while (true) {
                if (source.read(buffer, 8192) == -1L) break
                for (frame in parser.feed(buffer.readUtf8())) {
                    for (event in eventsFor(protocol, frame.data)) {
                        if (event is StreamEvent.Done) done = true
                        emit(event)
                    }
                }
            }
            for (frame in parser.flush()) {
                for (event in eventsFor(protocol, frame.data)) {
                    if (event is StreamEvent.Done) done = true
                    emit(event)
                }
            }
            if (!done) emit(StreamEvent.Done(true))
        }
    }.flowOn(Dispatchers.IO)

    private fun eventsFor(protocol: String, data: String): List<StreamEvent> {
        if (data.isEmpty() || data == "[DONE]") return emptyList()
        return when (protocol) {
            "anthropic" -> Anthropic.parseFrame(data)
            "gemini" -> Gemini.parseFrame(data)
            else -> OpenAiCompat.parseFrame(data)
        }
    }

    private fun Request.Builder.authHeaders(protocol: String, apiKey: String?) {
        when (protocol) {
            "anthropic" -> {
                header("x-api-key", apiKey ?: "")
                header("anthropic-version", Anthropic.VERSION)
            }
            "gemini" -> header("x-goog-api-key", apiKey ?: "")
            else -> if (!apiKey.isNullOrBlank()) header("authorization", "Bearer $apiKey")
        }
    }
}