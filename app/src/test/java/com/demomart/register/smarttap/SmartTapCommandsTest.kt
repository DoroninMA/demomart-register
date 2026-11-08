package com.demomart.register.smarttap

import org.junit.Assert.*
import org.junit.Test

internal fun bytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

class SmartTapCommandsTest {
    @Test fun selectOseBytes() {
        assertArrayEquals(bytes("00A404000A4F53452E5641532E303100"), SmartTapCommands.SELECT_OSE)
    }

    @Test fun selectSmartTapBytes() {
        assertArrayEquals(bytes("00A4040009A000000476D000011100"), SmartTapCommands.SELECT_SMART_TAP)
    }

    @Test fun negotiateFraming() {
        assertArrayEquals(bytes("9053000002010200"), SmartTapCommands.negotiate(byteArrayOf(1, 2)))
        assertArrayEquals(bytes("9050000002010200"), SmartTapCommands.getData(byteArrayOf(1, 2)))
    }

    @Test fun parseSelectOseFindsAid() {
        assertTrue(SmartTapCommands.parseSelectOse(bytes("6F12A510BF0C0D610B4F09A000000476D00001119000")))
        assertFalse(SmartTapCommands.parseSelectOse(bytes("6F0AA508BF0C0561034F01009000")))
    }

    @Test fun status9500Throws() {
        val error = assertThrows(SmartTapException::class.java) {
            SmartTapCommands.parseNegotiate(bytes("9500"))
        }
        assertEquals(0x9500, error.sw)
    }
}
