package dev.blank.mobile.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TreeSerializerTest {
    @Test
    fun filtersUninterestingNodes() {
        val nodes = listOf(
            UiNode(className = "android.widget.FrameLayout"),
            UiNode(text = "설정", className = "android.widget.TextView", clickable = true),
        )
        val out = TreeSerializer.serialize(nodes)
        assertFalse(out.contains("FrameLayout"))
        assertTrue(out.contains("설정"))
    }

    @Test
    fun capsAtMaxPreservingOrder() {
        val nodes = (1..200).map { UiNode(text = "item$it", clickable = true) }
        val out = TreeSerializer.serialize(nodes, max = 120)
        assertEquals(120, out.lines().size)
        assertTrue(out.lines().first().contains("item1"))
        assertTrue(out.lines().last().contains("item120"))
        assertFalse(out.contains("item121"))
    }

    @Test
    fun lineIncludesIndexClassTextBoundsAndFlags() {
        val node = UiNode(
            text = "다크 모드",
            resourceId = "com.android.settings:id/switch",
            className = "android.widget.Switch",
            clickable = true,
            editable = false,
            bounds = UiRect(10, 20, 300, 400),
        )
        val out = TreeSerializer.serialize(listOf(node))
        assertTrue(out.startsWith("[0] Switch "))
        assertTrue(out.contains("\"다크 모드\""))
        assertTrue(out.contains("id=com.android.settings:id/switch"))
        assertTrue(out.contains("(10,20,300,400)"))
        assertTrue(out.contains("clickable"))
        assertFalse(out.contains("editable"))
    }

    @Test
    fun editableFlagAndDescRendered() {
        val node = UiNode(desc = "검색 입력", className = "android.widget.EditText", editable = true)
        val out = TreeSerializer.serialize(listOf(node))
        assertTrue(out.contains("desc=\"검색 입력\""))
        assertTrue(out.contains("editable"))
    }

    @Test
    fun emptyScreenPlaceholder() {
        assertEquals("(empty screen)", TreeSerializer.serialize(emptyList()))
        assertEquals("(empty screen)", TreeSerializer.serialize(listOf(UiNode(className = "android.widget.LinearLayout"))))
    }
}