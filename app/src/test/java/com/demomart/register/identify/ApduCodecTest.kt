package com.demomart.register.identify

import org.junit.Assert.assertEquals
import org.junit.Test

class ApduCodecTest {
    @Test fun selectCommandBytes() = assertEquals(
        "00A4040007F0444D4C4F590100",
        ApduCodec.selectCommand().joinToString("") { "%02X".format(it.toInt() and 0xFF) },
    )

    @Test fun parseToken() {
        val token = "DM1:48210093:123456"
        assertEquals(NfcReadResult.Token(token), ApduCodec.parse(token.toByteArray() + byteArrayOf(0x90.toByte(), 0)))
    }

    @Test fun parseNotSignedIn() =
        assertEquals(NfcReadResult.NotSignedIn, ApduCodec.parse(byteArrayOf(0x69, 0x85.toByte())))

    @Test fun parseAppNotFound() =
        assertEquals(NfcReadResult.AppNotFound, ApduCodec.parse(byteArrayOf(0x6A, 0x82.toByte())))

    @Test fun parseGarbage() = assertEquals(NfcReadResult.Failure(-1), ApduCodec.parse(byteArrayOf(0x90.toByte())))

    @Test fun parseEmptyOk() =
        assertEquals(NfcReadResult.Failure(0x9000), ApduCodec.parse(byteArrayOf(0x90.toByte(), 0)))
}
