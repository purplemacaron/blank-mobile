package dev.blank.mobile.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneNumberTest {
    @Test
    fun normalizeStripsSeparators() {
        assertEquals("01012345678", PhoneNumber.normalize("010-1234-5678"))
        assertEquals("+821012345678", PhoneNumber.normalize("+82 10-1234-5678"))
        assertEquals("119", PhoneNumber.normalize("119"))
    }

    @Test
    fun normalizeRejectsInvalid() {
        assertNull(PhoneNumber.normalize(""))
        assertNull(PhoneNumber.normalize("   "))
        assertNull(PhoneNumber.normalize("010-abcd-5678"))
        assertNull(PhoneNumber.normalize("1"))
        assertNull(PhoneNumber.normalize("01234567890123456"))
        assertNull(PhoneNumber.normalize("+"))
    }

    @Test
    fun maskKeepsLastFour() {
        assertEquals("•••••••5678", PhoneNumber.mask("01012345678"))
        assertEquals("••••••5678", PhoneNumber.mask("1012345678"))
    }

    @Test
    fun maskShortNumbersFully() {
        assertEquals("•••", PhoneNumber.mask("119"))
        assertEquals("••••", PhoneNumber.mask("1234"))
    }
}