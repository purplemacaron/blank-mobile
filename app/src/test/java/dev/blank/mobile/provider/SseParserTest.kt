package dev.blank.mobile.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class SseParserTest {
    @Test
    fun `splits frames on blank line across chunk boundaries`() {
        val parser = SseParser()
        val first = parser.feed("data: one\n\ndata: tw")
        assertEquals(listOf(SseFrame("message", "one")), first)
        val second = parser.feed("o\n\n")
        assertEquals(listOf(SseFrame("message", "two")), second)
    }

    @Test
    fun `normalizes crlf and parses event and data lines`() {
        val parser = SseParser()
        val frames = parser.feed("event: ping\r\ndata: hello\r\n\r\n")
        assertEquals(listOf(SseFrame("ping", "hello")), frames)
    }

    @Test
    fun `strips one leading space and joins multiline data`() {
        val parser = SseParser()
        val frames = parser.feed("data:  spaced\ndata: second\n\n")
        assertEquals(listOf(SseFrame("message", " spaced\nsecond")), frames)
    }

    @Test
    fun `flush emits trailing frame without blank line`() {
        val parser = SseParser()
        assertEquals(emptyList<SseFrame>(), parser.feed("data: tail"))
        assertEquals(listOf(SseFrame("message", "tail")), parser.flush())
        assertEquals(emptyList<SseFrame>(), parser.flush())
    }

    @Test
    fun `ignores empty frames and comment lines`() {
        val parser = SseParser()
        val frames = parser.feed(": keep-alive\n\n\n\ndata: x\n\n")
        assertEquals(listOf(SseFrame("message", "x")), frames)
    }
}