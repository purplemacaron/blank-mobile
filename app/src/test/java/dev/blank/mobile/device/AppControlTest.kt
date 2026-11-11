package dev.blank.mobile.device

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppControlTest {
    @Test
    fun missingApkReported() {
        val missing = File(System.getProperty("java.io.tmpdir"), "blank-missing-${System.nanoTime()}.apk")
        assertEquals("APK not found: ${missing.absolutePath}", AppControl.apkReadError(missing))
    }

    @Test
    fun readableApkPassesProbe() {
        val file = File.createTempFile("blank-probe", ".apk")
        try {
            file.writeText("PK test")
            assertNull(AppControl.apkReadError(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun emptyApkReported() {
        val file = File.createTempFile("blank-empty", ".apk")
        try {
            val error = AppControl.apkReadError(file)
            assertTrue(error != null && error.contains("empty"))
        } finally {
            file.delete()
        }
    }
}