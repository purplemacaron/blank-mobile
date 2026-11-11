package dev.blank.mobile.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StreamParsersTest {
    @Test
    fun `openai deltas map to text reasoning usage and done`() {
        assertEquals(
            listOf(StreamEvent.Text("Hi")),
            OpenAiCompat.parseFrame("""{"choices":[{"delta":{"content":"Hi"}}]}"""),
        )
        assertEquals(
            listOf(StreamEvent.Reasoning("hmm")),
            OpenAiCompat.parseFrame("""{"choices":[{"delta":{"reasoning_content":"hmm"}}]}"""),
        )
        assertEquals(
            listOf(StreamEvent.Reasoning("hmm")),
            OpenAiCompat.parseFrame("""{"choices":[{"delta":{"reasoning":"hmm"}}]}"""),
        )
        assertEquals(
            listOf(StreamEvent.Usage(10, 5)),
            OpenAiCompat.parseFrame("""{"usage":{"prompt_tokens":10,"completion_tokens":5}}"""),
        )
        assertEquals(
            listOf(StreamEvent.Done(true)),
            OpenAiCompat.parseFrame("""{"choices":[{"delta":{},"finish_reason":"stop"}]}"""),
        )
        assertEquals(emptyList<StreamEvent>(), OpenAiCompat.parseFrame("""{"choices":[{"delta":{}}]}"""))
    }

    @Test
    fun `openai error frame throws provider exception`() {
        try {
            OpenAiCompat.parseFrame("""{"error":{"message":"bad key"}}""")
            fail("expected ProviderException")
        } catch (e: ProviderException) {
            assertTrue(e.message!!.contains("bad key"))
        }
    }

    @Test
    fun `anthropic events map to usage text reasoning and done`() {
        assertEquals(
            listOf(StreamEvent.Usage(12, 0)),
            Anthropic.parseFrame("""{"type":"message_start","message":{"usage":{"input_tokens":12}}}"""),
        )
        assertEquals(
            listOf(StreamEvent.Text("Hi")),
            Anthropic.parseFrame("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi"}}"""),
        )
        assertEquals(
            listOf(StreamEvent.Reasoning("think")),
            Anthropic.parseFrame("""{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"think"}}"""),
        )
        assertEquals(
            listOf(StreamEvent.Usage(0, 7), StreamEvent.Done(true)),
            Anthropic.parseFrame("""{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":7}}"""),
        )
        assertEquals(
            emptyList<StreamEvent>(),
            Anthropic.parseFrame("""{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}"""),
        )
    }

    @Test
    fun `anthropic error event throws provider exception`() {
        try {
            Anthropic.parseFrame("""{"type":"error","error":{"message":"boom"}}""")
            fail("expected ProviderException")
        } catch (e: ProviderException) {
            assertTrue(e.message!!.contains("boom"))
        }
    }

    @Test
    fun `gemini parts split thought text and usage`() {
        assertEquals(
            listOf(StreamEvent.Usage(3, 4), StreamEvent.Reasoning("think"), StreamEvent.Text("hello")),
            Gemini.parseFrame(
                """{"candidates":[{"content":{"parts":[{"text":"think","thought":true},{"text":"hello"}]}}],"usageMetadata":{"promptTokenCount":3,"candidatesTokenCount":4}}""",
            ),
        )
        assertEquals(emptyList<StreamEvent>(), Gemini.parseFrame("""{"candidates":[]}"""))
    }

    @Test
    fun `gemini error frame throws provider exception`() {
        try {
            Gemini.parseFrame("""{"error":{"message":"nope"}}""")
            fail("expected ProviderException")
        } catch (e: ProviderException) {
            assertTrue(e.message!!.contains("nope"))
        }
    }
}