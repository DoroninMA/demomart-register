package com.demomart.register.smarttap

import org.junit.Assert.*
import org.junit.Test

class NdefTest {
    @Test fun ndefRoundTrip() {
        val records = listOf(externalRecord("ses", byteArrayOf(1)), externalRecord("cpr", byteArrayOf(2), "a"))
        val encoded = Ndef.encode(records)
        assertEquals(0x94, encoded[0].toInt() and 0xff)
        assertEquals(0x5c, encoded[7].toInt() and 0xff)
        val decoded = Ndef.decode(encoded)
        assertEquals(records, decoded)
        assertArrayEquals(records[1].id, decoded[1].id)
        assertArrayEquals(encoded, Ndef.encode(decoded))
    }

    @Test fun ndefLongRecord() {
        val payload = ByteArray(300) { it.toByte() }
        val encoded = Ndef.encode(listOf(externalRecord("reb", payload)))
        assertEquals(0xc4, encoded[0].toInt() and 0xff)
        assertArrayEquals(byteArrayOf(0, 0, 1, 44), encoded.copyOfRange(2, 6))
        assertArrayEquals(payload, Ndef.findExternal(Ndef.decode(encoded), "reb")?.payload)
    }

    @Test fun rejectsTruncatedRecord() {
        val encoded = Ndef.encode(listOf(externalRecord("ses", byteArrayOf(1))))
        assertThrows(IllegalArgumentException::class.java) { Ndef.decode(encoded.dropLast(1).toByteArray()) }
    }
}
