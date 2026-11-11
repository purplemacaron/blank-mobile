package dev.blank.mobile.agent

import dev.blank.mobile.provider.ChatMessage
import dev.blank.mobile.provider.StreamEvent
import dev.blank.mobile.provider.ToolCallRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentLoopTest {
    private class FakeProvider(private val steps: List<List<StreamEvent>>) {
        val calls = mutableListOf<List<ChatMessage>>()
        fun stream(messages: List<ChatMessage>, tools: List<ToolSpec>): Flow<StreamEvent> {
            calls += messages
            return flowOf(*steps[calls.lastIndex].toTypedArray())
        }
    }

    @Test
    fun toolCallExecutedAndResultFedBack() = runTest {
        val provider = FakeProvider(
            listOf(
                listOf(StreamEvent.ToolCall("c1", "device_observe", "{}"), StreamEvent.Done(true)),
                listOf(StreamEvent.Text("완료"), StreamEvent.Done(true)),
            ),
        )
        val executed = mutableListOf<ToolCallRef>()
        val results = mutableListOf<Pair<ToolCallRef, String>>()
        val loop = AgentLoop(
            stream = provider::stream,
            execute = { call -> executed += call; "[0] Switch \"다크 모드\"" },
            approve = { true },
            onToolResult = { call, result -> results += call to result },
        )
        val final = loop.run(listOf(ChatMessage("user", "다크 모드 켜줘")))
        assertEquals("완료", final)
        assertEquals(listOf("c1"), executed.map { it.id })
        assertEquals(2, provider.calls.size)
        val second = provider.calls[1]
        assertTrue(second.any { it.role == "tool" && it.toolCallId == "c1" && it.content.contains("다크 모드") })
        assertEquals(1, results.size)
    }

    @Test
    fun deniedToolNotExecuted() = runTest {
        val provider = FakeProvider(
            listOf(
                listOf(StreamEvent.ToolCall("c1", "device_act", "{}"), StreamEvent.Done(true)),
                listOf(StreamEvent.Text("알겠습니다"), StreamEvent.Done(true)),
            ),
        )
        var executed = false
        val loop = AgentLoop(
            stream = provider::stream,
            execute = { executed = true; "should not run" },
            approve = { false },
        )
        loop.run(listOf(ChatMessage("user", "탭해")))
        assertEquals(false, executed)
        assertTrue(provider.calls[1].any { it.role == "tool" && it.content.contains("denied") })
    }

    @Test
    fun stepLimitStopsLoop() = runTest {
        val provider = FakeProvider(
            List(10) { listOf(StreamEvent.ToolCall("c$it", "device_observe", "{}"), StreamEvent.Done(true)) },
        )
        val loop = AgentLoop(
            stream = provider::stream,
            execute = { "tree" },
            approve = { true },
            maxSteps = 3,
        )
        loop.run(listOf(ChatMessage("user", "계속 봐")))
        assertEquals(3, provider.calls.size)
    }

    @Test
    fun askUserBypassesApprovalAndFeedsChoiceBack() = runTest {
        val provider = FakeProvider(
            listOf(
                listOf(StreamEvent.ToolCall("q1", "ask_user", """{"question":"Which?","options":["A","B"]}"""), StreamEvent.Done(true)),
                listOf(StreamEvent.Text("A로 진행"), StreamEvent.Done(true)),
            ),
        )
        var asked: ToolCallRef? = null
        var approved = false
        val loop = AgentLoop(
            stream = provider::stream,
            execute = { "should not run" },
            approve = { approved = true; true },
            ask = { call -> asked = call; "User chose: A" },
        )
        assertEquals("A로 진행", loop.run(listOf(ChatMessage("user", "물어봐"))))
        assertEquals("q1", asked?.id)
        assertEquals(false, approved)
        assertTrue(provider.calls[1].any { it.role == "tool" && it.toolCallId == "q1" && it.content.contains("User chose: A") })
    }

    @Test
    fun plainReplyEndsImmediately() = runTest {
        val provider = FakeProvider(listOf(listOf(StreamEvent.Text("안녕"), StreamEvent.Done(true))))
        val loop = AgentLoop(stream = provider::stream, execute = { "x" }, approve = { true })
        assertEquals("안녕", loop.run(listOf(ChatMessage("user", "hi"))))
        assertEquals(1, provider.calls.size)
    }

    @Test
    fun onLimitFiresWhenStepLimitReached() = runTest {
        val provider = FakeProvider(
            List(5) { listOf(StreamEvent.ToolCall("c$it", "device_observe", "{}"), StreamEvent.Done(true)) },
        )
        var limited = false
        val loop = AgentLoop(
            stream = provider::stream,
            execute = { "tree" },
            approve = { true },
            maxSteps = 3,
            onLimit = { limited = true },
        )
        loop.run(listOf(ChatMessage("user", "계속 봐")))
        assertTrue(limited)
    }

    @Test
    fun onLimitNotFiredOnNormalCompletion() = runTest {
        val provider = FakeProvider(listOf(listOf(StreamEvent.Text("끝"), StreamEvent.Done(true))))
        var limited = false
        val loop = AgentLoop(
            stream = provider::stream,
            execute = { "x" },
            approve = { true },
            onLimit = { limited = true },
        )
        loop.run(listOf(ChatMessage("user", "hi")))
        assertEquals(false, limited)
    }
}