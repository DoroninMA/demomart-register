/*
 * Derived from Google smart-tap-sample-app, Copyright 2022 Google LLC, Apache License 2.0.
 * Modified: ported to Kotlin and restructured for DemoMart.
 */
package com.demomart.register.smarttap

import java.nio.ByteBuffer

class SmartTapException(val sw: Int) : RuntimeException("Smart Tap status ${sw.toString(16)}") {
    companion object {
        const val MALFORMED = -2
        const val INTEGRITY = -3
    }
}

data class NegotiateResult(val dpk: ByteArray, val sequence: Int)

object SmartTapCommands {
    private val smartTapAid = byteArrayOf(0xa0.toByte(), 0, 0, 4, 0x76, 0xd0.toByte(), 0, 1, 0x11)

    val SELECT_OSE: ByteArray get() = select("OSE.VAS.01".toByteArray(Charsets.US_ASCII))
    val SELECT_SMART_TAP: ByteArray get() = select(smartTapAid)

    fun negotiate(ngr: ByteArray): ByteArray = command(0x53, ngr)
    fun getData(srq: ByteArray): ByteArray = command(0x50, srq)

    fun parseSelectOse(resp: ByteArray): Boolean = parseResponse(resp) { payload ->
        val directory = readTlvs(payload).firstValue(0x6f)
            ?.let { readTlvs(it).firstValue(0xa5) }
            ?.let { readTlvs(it).firstValue(0xbf0c) }
            ?: return@parseResponse false
        readTlvs(directory).any { (tag, entry) ->
            tag == 0x61 && readTlvs(entry).firstValue(0x4f)?.contentEquals(smartTapAid) == true
        }
    }

    fun parseSelectSmartTap(resp: ByteArray): ByteArray = parseResponse(resp) { payload ->
        require(payload.size >= 4)
        val nonce = Ndef.decode(payload.copyOfRange(4, payload.size)).first().payload
        require(nonce.size == 33)
        nonce.copyOfRange(1, nonce.size)
    }

    fun parseNegotiate(resp: ByteArray): NegotiateResult = parseResponse(resp) { payload ->
        val records = Ndef.decode(requiredRecord(Ndef.decode(payload), "nrs").payload)
        val session = requiredRecord(records, "ses").payload
        require(session.size >= 10)
        val key = requiredRecord(records, "dpk").payload
        require(key.isNotEmpty())
        NegotiateResult(key, session[8].toInt() and 0xff)
    }

    fun parseGetData(resp: ByteArray): ByteArray = parseResponse(resp) { payload ->
        val records = Ndef.decode(requiredRecord(Ndef.decode(payload), "srs").payload)
        requiredRecord(records, "reb").payload
    }

    private fun select(aid: ByteArray) =
        byteArrayOf(0, 0xa4.toByte(), 4, 0, aid.size.toByte()) + aid + 0.toByte()

    private fun command(ins: Int, payload: ByteArray): ByteArray {
        require(payload.size in 1..255) { "Invalid short APDU length" }
        return byteArrayOf(0x90.toByte(), ins.toByte(), 0, 0, payload.size.toByte()) + payload + 0.toByte()
    }

    private fun requiredRecord(records: List<NdefRecord>, type: String): NdefRecord =
        requireNotNull(Ndef.findExternal(records, type)) { "Missing Smart Tap record" }

    private inline fun <T> parseResponse(response: ByteArray, parse: (ByteArray) -> T): T {
        if (response.size < 2) throw SmartTapException(SmartTapException.MALFORMED)
        val sw = ((response[response.size - 2].toInt() and 0xff) shl 8) or (response.last().toInt() and 0xff)
        if (sw ushr 12 != 9 || sw == 0x9500) throw SmartTapException(sw)
        return try {
            parse(response.copyOf(response.size - 2))
        } catch (_: IllegalArgumentException) {
            throw SmartTapException(SmartTapException.MALFORMED)
        }
    }

    private fun List<Pair<Int, ByteArray>>.firstValue(tag: Int): ByteArray? =
        firstOrNull { it.first == tag }?.second

    private fun readTlvs(payload: ByteArray): List<Pair<Int, ByteArray>> {
        val input = ByteBuffer.wrap(payload)
        val values = mutableListOf<Pair<Int, ByteArray>>()
        while (input.hasRemaining()) {
            var tag = input.u8()
            if (tag and 0x1f == 0x1f) {
                do {
                    val next = input.u8()
                    require(tag <= 0xffffff)
                    tag = (tag shl 8) or next
                } while (next and 0x80 != 0)
            }
            val firstLength = input.u8()
            var length = firstLength
            if (firstLength and 0x80 != 0) {
                val count = firstLength and 0x7f
                require(count in 1..3)
                length = 0
                repeat(count) { length = (length shl 8) or input.u8() }
            }
            values += tag to input.takeBytes(length)
        }
        return values
    }
}
