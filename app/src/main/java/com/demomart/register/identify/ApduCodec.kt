package com.demomart.register.identify

sealed interface NfcReadResult {
    data class Token(val text: String) : NfcReadResult
    data object NotSignedIn : NfcReadResult
    data object AppNotFound : NfcReadResult
    data class Failure(val sw: Int) : NfcReadResult
}

object ApduCodec {
    val AID: ByteArray
        get() = byteArrayOf(0xF0.toByte(), 0x44, 0x4D, 0x4C, 0x4F, 0x59, 0x01)

    fun selectCommand(): ByteArray = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x07) + AID + 0x00.toByte()

    fun parse(response: ByteArray): NfcReadResult {
        if (response.size < 2) return NfcReadResult.Failure(-1)
        val sw = ((response[response.lastIndex - 1].toInt() and 0xFF) shl 8) or
            (response.last().toInt() and 0xFF)
        return when (sw) {
            0x6985 -> NfcReadResult.NotSignedIn
            0x6A82 -> NfcReadResult.AppNotFound
            0x9000 -> if (response.size > 2) {
                NfcReadResult.Token(String(response, 0, response.size - 2, Charsets.US_ASCII))
            } else NfcReadResult.Failure(sw)
            else -> NfcReadResult.Failure(sw)
        }
    }
}
