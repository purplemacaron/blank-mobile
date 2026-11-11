package dev.blank.mobile.provider

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestBuildersTest {
    private val image = ImageAttachment("image/png", "QUJD")

    @Test
    fun `openai body has system first and plain text user content`() {
        val body = OpenAiCompat.body(
            model = "gpt-4o",
            system = "be nice",
            messages = listOf(ChatMessage("user", "hello")),
        )
        assertEquals("gpt-4o", body["model"]!!.jsonPrimitive.content)
        assertTrue(body["stream"]!!.jsonPrimitive.boolean)
        assertTrue(body["stream_options"]!!.jsonObject["include_usage"]!!.jsonPrimitive.boolean)
        val messages = body["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("be nice", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("hello", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `openai body encodes images as data url parts`() {
        val body = OpenAiCompat.body(
            model = "m",
            system = "s",
            messages = listOf(ChatMessage("user", "look", listOf(image))),
        )
        val content = body["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertEquals("text", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("look", content[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("image_url", content[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(
            "data:image/png;base64,QUJD",
            content[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `openai body caps max tokens when asked`() {
        val body = OpenAiCompat.body("m", "s", listOf(ChatMessage("user", "x")), maxTokens = 256)
        assertEquals(256, body["max_tokens"]!!.jsonPrimitive.int)
    }

    @Test
    fun `anthropic body defaults max tokens and uses image blocks`() {
        val body = Anthropic.body(
            model = "claude",
            system = "sys",
            messages = listOf(
                ChatMessage("user", "see", listOf(image)),
                ChatMessage("assistant", "ok"),
            ),
        )
        assertEquals(8192, body["max_tokens"]!!.jsonPrimitive.int)
        assertEquals("sys", body["system"]!!.jsonPrimitive.content)
        assertTrue(body["stream"]!!.jsonPrimitive.boolean)
        val messages = body["messages"]!!.jsonArray
        val userContent = messages[0].jsonObject["content"]!!.jsonArray
        assertEquals("text", userContent[0].jsonObject["type"]!!.jsonPrimitive.content)
        val source = userContent[1].jsonObject["source"]!!.jsonObject
        assertEquals("base64", source["type"]!!.jsonPrimitive.content)
        assertEquals("image/png", source["media_type"]!!.jsonPrimitive.content)
        assertEquals("QUJD", source["data"]!!.jsonPrimitive.content)
        val assistantContent = messages[1].jsonObject["content"]!!.jsonArray
        assertEquals("ok", assistantContent[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `gemini body uses system instruction and inline data`() {
        val body = Gemini.body(
            model = "gemini-2.5-pro",
            system = "sys",
            messages = listOf(
                ChatMessage("user", "hi", listOf(image)),
                ChatMessage("assistant", "yo"),
            ),
            maxTokens = 1024,
        )
        assertEquals(
            "sys",
            body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content,
        )
        assertEquals(1024, body["generationConfig"]!!.jsonObject["maxOutputTokens"]!!.jsonPrimitive.int)
        val contents = body["contents"]!!.jsonArray
        assertEquals("user", contents[0].jsonObject["role"]!!.jsonPrimitive.content)
        val parts = contents[0].jsonObject["parts"]!!.jsonArray
        assertEquals("hi", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
        val inline = parts[1].jsonObject["inlineData"]!!.jsonObject
        assertEquals("image/png", inline["mimeType"]!!.jsonPrimitive.content)
        assertEquals("QUJD", inline["data"]!!.jsonPrimitive.content)
        assertEquals("model", contents[1].jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun `gemini body always has at least one text part`() {
        val body = Gemini.body("m", "s", listOf(ChatMessage("user", "")))
        val parts = body["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        assertEquals(1, parts.size)
        assertEquals("", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
    }
}