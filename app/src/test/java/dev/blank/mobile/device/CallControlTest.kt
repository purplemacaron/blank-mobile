package dev.blank.mobile.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CallControlTest {
    @Test
    fun stateNames() {
        assertEquals("idle", CallControl.stateName(0))
        assertEquals("ringing", CallControl.stateName(1))
        assertEquals("offhook", CallControl.stateName(2))
        assertEquals("unknown", CallControl.stateName(7))
    }

    @Test
    fun endCallLabelsCoverDialerUiAndCallNotification() {
        val labels = CallControl.END_CALL_LABELS
        assertTrue(labels.contains("통화 종료"))
        assertTrue(labels.contains("끊기"))
        assertTrue(labels.contains("Hang up"))
        assertTrue(labels.contains("End call"))
    }
}