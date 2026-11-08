/*
 * Derived from Google smart-tap-sample-app, Copyright 2022 Google LLC, Apache License 2.0.
 * Modified: ported to Kotlin and restructured for DemoMart.
 */
package com.demomart.register.smarttap

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

const val TNF_EXTERNAL = 0x04
private const val MB = 0x80
private const val ME = 0x40
private const val CF = 0x20
private const val SR = 0x10
private const val IL = 0x08

data class NdefRecord(val tnf: Int, val type: ByteArray, val id: ByteArray, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean = other is NdefRecord && tnf == other.tnf &&
        type.contentEquals(other.type) && id.contentEquals(other.id) && payload.contentEquals(other.payload)

    override fun hashCode(): Int {
        var hash = tnf
        hash = 31 * hash + type.contentHashCode()
        hash = 31 * hash + id.contentHashCode()
        return 31 * hash + payload.contentHashCode()
    }
}

internal fun externalRecord(type: String, payload: ByteArray, id: String = "") =
    NdefRecord(TNF_EXTERNAL, type.toByteArray(Charsets.US_ASCII), id.toByteArray(Charsets.US_ASCII), payload)

internal fun ByteBuffer.u8(): Int {
    require(hasRemaining()) { "Truncated record" }
    return get().toInt() and 0xff
}

internal fun ByteBuffer.takeBytes(size: Int): ByteArray {
    require(size in 0..remaining()) { "Invalid record length" }
    return ByteArray(size).also { get(it) }
}

object Ndef {
    fun encode(records: List<NdefRecord>): ByteArray {
        require(records.isNotEmpty())
        val out = ByteArrayOutputStream()
        records.forEachIndexed { index, record ->
            require(record.tnf in 0..5 && record.type.size <= 255 && record.id.size <= 255)
            val short = record.payload.size <= 255
            val hasId = record.id.isNotEmpty()
            var flags = record.tnf
            if (index == 0) flags = flags or MB
            if (index == records.lastIndex) flags = flags or ME
            if (short) flags = flags or SR
            if (hasId) flags = flags or IL
            out.write(flags)
            out.write(record.type.size)
            if (short) out.write(record.payload.size)
            else out.write(ByteBuffer.allocate(4).putInt(record.payload.size).array())
            if (hasId) out.write(record.id.size)
            out.write(record.type)
            out.write(record.id)
            out.write(record.payload)
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): List<NdefRecord> {
        val input = ByteBuffer.wrap(bytes)
        val records = mutableListOf<NdefRecord>()
        while (input.hasRemaining()) {
            val flags = input.u8()
            require((flags and MB != 0) == records.isEmpty() && flags and CF == 0) { "Invalid NDEF flags" }
            val tnf = flags and 7
            require(tnf <= 5) { "Invalid NDEF TNF" }
            val typeLength = input.u8()
            val payloadLength = if (flags and SR != 0) input.u8() else {
                require(input.remaining() >= 4) { "Truncated NDEF length" }
                input.int
            }
            val idLength = if (flags and IL != 0) input.u8() else 0
            records += NdefRecord(tnf, input.takeBytes(typeLength), input.takeBytes(idLength),
                input.takeBytes(payloadLength))
            if (flags and ME != 0) {
                require(!input.hasRemaining()) { "Bytes after final NDEF record" }
                return records
            }
        }
        throw IllegalArgumentException("Missing final NDEF record")
    }

    fun findExternal(records: List<NdefRecord>, type: String): NdefRecord? {
        val encoded = type.toByteArray(Charsets.US_ASCII)
        return records.firstOrNull { it.tnf == TNF_EXTERNAL && it.type.contentEquals(encoded) }
    }
}
