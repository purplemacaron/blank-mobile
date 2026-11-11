package dev.blank.mobile.ui

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenSaverTest {
    private val scope = SaverScope { true }

    private fun roundTrip(screen: Screen): Screen? {
        val saved = with(ScreenSaver) { scope.save(screen) }
        @Suppress("UNCHECKED_CAST")
        return ScreenSaver.restore(saved as String)
    }

    @Test
    fun `every screen survives save and restore`() {
        assertEquals(Screen.Chat, roundTrip(Screen.Chat))
        assertEquals(Screen.Sessions, roundTrip(Screen.Sessions))
        assertEquals(Screen.Settings, roundTrip(Screen.Settings))
    }

    @Test
    fun `unknown saved name restores to null`() {
        assertNull(ScreenSaver.restore("bogus"))
    }
}