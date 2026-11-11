package dev.blank.mobile.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ToolsTest {
    private fun args(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(block)

    @Test
    fun specsCoverDeviceTools() {
        val names = Tools.all.map { it.name }
        assertTrue(names.containsAll(listOf("device_observe", "device_act")))
    }

    @Test
    fun unknownToolRejected() {
        assertFailsWith<IllegalArgumentException> { Tools.validate("nope", args {}) }
    }

    @Test
    fun observeDefaultsScreenshotFalse() {
        val out = Tools.validate("device_observe", args {})
        assertEquals(false, out["screenshot"]?.let { it.toString() == "true" })
    }

    @Test
    fun tapRequiresSelectorOrCoordinates() {
        assertFailsWith<IllegalArgumentException> { Tools.validate("device_act", args { put("action", "tap") }) }
        Tools.validate("device_act", args { put("action", "tap"); put("x", 10); put("y", 20) })
        Tools.validate("device_act", args {
            put("action", "tap")
            put("selector", buildJsonObject { put("text", "설정") })
        })
    }

    @Test
    fun selectorNeedsAtLeastOneField() {
        assertFailsWith<IllegalArgumentException> {
            Tools.validate("device_act", args {
                put("action", "tap")
                put("selector", buildJsonObject {})
            })
        }
    }

    @Test
    fun swipeNeedsDirectionOrQuad() {
        assertFailsWith<IllegalArgumentException> { Tools.validate("device_act", args { put("action", "swipe") }) }
        Tools.validate("device_act", args { put("action", "swipe"); put("direction", "up") })
        assertFailsWith<IllegalArgumentException> {
            Tools.validate("device_act", args { put("action", "swipe"); put("direction", "sideways") })
        }
        Tools.validate("device_act", args { put("action", "swipe"); put("x", 1); put("y", 2); put("x2", 3); put("y2", 4) })
    }

    @Test
    fun typeRequiresText() {
        assertFailsWith<IllegalArgumentException> { Tools.validate("device_act", args { put("action", "type") }) }
        assertFailsWith<IllegalArgumentException> {
            Tools.validate("device_act", args { put("action", "type"); put("text", "  ") })
        }
        Tools.validate("device_act", args { put("action", "type"); put("text", "안녕하세요") })
    }

    @Test
    fun keyEnumEnforced() {
        assertFailsWith<IllegalArgumentException> { Tools.validate("device_act", args { put("action", "key") }) }
        assertFailsWith<IllegalArgumentException> {
            Tools.validate("device_act", args { put("action", "key"); put("key", "power") })
        }
        Tools.validate("device_act", args { put("action", "key"); put("key", "back") })
    }

    @Test
    fun openUrlRequiresHttp() {
        assertFailsWith<IllegalArgumentException> { Tools.validate("device_act", args { put("action", "open_url") }) }
        assertFailsWith<IllegalArgumentException> {
            Tools.validate("device_act", args { put("action", "open_url"); put("url", "file:///etc") })
        }
        Tools.validate("device_act", args { put("action", "open_url"); put("url", "https://example.com") })
    }

    @Test
    fun waitClampedToRange() {
        val out = Tools.validate("device_act", args { put("action", "wait") })
        assertEquals(1000, out["ms"].toString().toInt())
        val low = Tools.validate("device_act", args { put("action", "wait"); put("ms", 1) })
        assertEquals(100, low["ms"].toString().toInt())
        val high = Tools.validate("device_act", args { put("action", "wait"); put("ms", 999999) })
        assertEquals(10000, high["ms"].toString().toInt())
    }

    @Test
    fun unknownActionRejected() {
        assertFailsWith<IllegalArgumentException> {
            Tools.validate("device_act", args { put("action", "explode") })
        }
    }
}