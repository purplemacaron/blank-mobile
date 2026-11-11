package dev.blank.mobile.agent

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelectorTest {
    private val nodes = listOf(
        UiNode(text="다크 모드", className="android.widget.Switch", clickable = true),
        UiNode(text="디스플레이", className="android.widget.TextView", clickable = true),
        UiNode(desc="검색", className="android.widget.ImageButton", clickable = true),
        UiNode(resourceId="com.android.settings:id/save", className="android.widget.Button", clickable = true),
        UiNode(text="저장", className="android.widget.Button", clickable = true),
    )

    @Test
    fun matchesExactTextFirst() {
        val selector = buildJsonObject { put("text", "다크 모드") }
        assertEquals("다크 모드", Selector.match(nodes, selector)?.text)
    }

    @Test
    fun fallsBackToContainsText() {
        val selector = buildJsonObject { put("text", "다크") }
        assertEquals("다크 모드", Selector.match(nodes, selector)?.text)
    }

    @Test
    fun matchesContentDescription() {
        val selector = buildJsonObject { put("desc", "검색") }
        assertEquals("검색", Selector.match(nodes, selector)?.desc)
    }

    @Test
    fun matchesResourceIdSuffix() {
        val selector = buildJsonObject { put("id", "save") }
        assertEquals("com.android.settings:id/save", Selector.match(nodes, selector)?.resourceId)
    }

    @Test
    fun matchesByIndex() {
        val selector = buildJsonObject { put("index", 4) }
        assertEquals("저장", Selector.match(nodes, selector)?.text)
    }

    @Test
    fun returnsNullWhenNothingMatches() {
        val selector = buildJsonObject { put("text", "없는 항목") }
        assertNull(Selector.match(nodes, selector))
    }

    @Test
    fun returnsNullForOutOfRangeIndex() {
        val selector = buildJsonObject { put("index", 99) }
        assertNull(Selector.match(nodes, selector))
    }
}