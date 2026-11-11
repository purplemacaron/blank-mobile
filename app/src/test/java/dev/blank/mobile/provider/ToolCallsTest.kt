package dev.blank.mobile.provider

import dev.blank.mobile.agent.Tools
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolCallsTest {
    private val toolHistory = listOf(
        ChatMessage("user", "화면 봐줘"),
        ChatMessage("assistant", "", toolCalls = listOf(ToolCallRef("c1", "device_observe", "{}"))),
        ChatMessage("tool", "[0] Switch \"다크 모드\"", toolCallId = "c1"),
    )

    @Test
    fun openaiBodyIncludesToolsAndToolMessages() {
        val body = OpenAiCompat.body("m", "sys", toolHistory, tools = Tools.all)
        val tools = body["tools"]!!.jsonArray
        assertEquals(2, tools.size)
        assertEquals("device_observe", tools[0].jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        val messages = body["messages"]!!.jsonArray
        val assistant = messages[2].jsonObject
        assertEquals("c1", assistant["tool_calls"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content)
        val tool = messages[3].jsonObject
        assertEquals("tool", tool["role"]!!.jsonPrimitive.content)
        assertEquals("c1", tool["tool_call_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun openaiDecoderAccumulatesFragmentedToolCalls() {
        val decoder = OpenAiCompat.Decoder()
        val first = decoder.decode(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"device_act","arguments":"{\"action\":"}}]}}]}""",
        )
        assertTrue(first.none { it is StreamEvent.ToolCall })
        decoder.decode("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"tap\"}"}}]}}]}""")
        val last = decoder.decode("""{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""")
        val call = last.filterIsInstance<StreamEvent.ToolCall>().single()
        assertEquals("c1", call.id)
        assertEquals("device_act", call.name)
        assertEquals("{\"action\":\"tap\"}", call.arguments)
    }

    @Test
    fun anthropicBodyToolBlocks() {
        val body = Anthropic.body("m", "sys", toolHistory, tools = Tools.all)
        val tools = body["tools"]!!.jsonArray
        assertEquals("device_observe", tools[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertTrue(tools[0].jsonObject.containsKey("input_schema"))
        val messages = body["messages"]!!.jsonArray
        val assistantBlocks = messages[1].jsonObject["content"]!!.jsonArray
        assertEquals("tool_use", assistantBlocks[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("c1", assistantBlocks[0].jsonObject["id"]!!.jsonPrimitive.content)
        val toolBlocks = messages[2].jsonObject["content"]!!.jsonArray
        assertEquals("tool_result", toolBlocks[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("c1", toolBlocks[0].jsonObject["tool_use_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun anthropicDecoderEmitsToolUseOnBlockStop() {
        val decoder = Anthropic.Decoder()
        decoder.decode("""{"type":"content_block_start","content_block":{"type":"tool_use","id":"t1","name":"device_observe"}}""")
        decoder.decode("""{"type":"content_block_delta","delta":{"type":"input_json_delta","partial_json":"{}"}}""")
        val events = decoder.decode("""{"type":"content_block_stop"}""")
        val call = events.filterIsInstance<StreamEvent.ToolCall>().single()
        assertEquals("t1", call.id)
        assertEquals("device_observe", call.name)
        assertEquals("{}", call.arguments)
    }

    @Test
    fun geminiBodyFunctionDeclarationsAndResponses() {
        val body = Gemini.body("m", "sys", toolHistory, tools = Tools.all)
        val declarations = body["tools"]!!.jsonArray[0].jsonObject["functionDeclarations"]!!.jsonArray
        assertEquals("device_observe", declarations[0].jsonObject["name"]!!.jsonPrimitive.content)
        val contents = body["contents"]!!.jsonArray
        val modelParts = contents[1].jsonObject["parts"]!!.jsonArray
        assertEquals("device_observe", modelParts[0].jsonObject["functionCall"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        val responseParts = contents[2].jsonObject["parts"]!!.jsonArray
        val response = responseParts[0].jsonObject["functionResponse"]!!.jsonObject
        assertEquals("device_observe", response["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun geminiDecoderEmitsFunctionCall() {
        val decoder = Gemini.Decoder()
        val events = decoder.decode(
            """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"device_act","args":{"action":"tap","x":1,"y":2}}}]}}]}""",
        )
        val call = events.filterIsInstance<StreamEvent.ToolCall>().single()
        assertEquals("device_act", call.name)
        assertTrue(call.arguments.contains("\"tap\""))
    }
}