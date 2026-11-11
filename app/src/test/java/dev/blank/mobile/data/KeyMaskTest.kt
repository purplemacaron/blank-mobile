package dev.blank.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyMaskTest {
    @Test
    fun `long key shows only last four characters`() {
        assertEquals("••••abcd", maskKey("sk-1234567890abcd"))
    }

    @Test
    fun `short key is fully masked`() {
        assertEquals("••••", maskKey("abcd"))
        assertEquals("••••", maskKey("ab"))
    }

    @Test
    fun `empty key stays empty`() {
        assertEquals("", maskKey(""))
    }
}