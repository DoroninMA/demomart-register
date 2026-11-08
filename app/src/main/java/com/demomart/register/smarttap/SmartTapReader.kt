/*
 * Derived from Google smart-tap-sample-app, Copyright 2022 Google LLC, Apache License 2.0.
 * Modified: ported to Kotlin and restructured for DemoMart.
 */
package com.demomart.register.smarttap

import com.demomart.register.identify.NfcReadResult
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.security.GeneralSecurityException
import java.security.interfaces.ECPublicKey

fun interface Transceiver {
    fun transceive(apdu: ByteArray): ByteArray
}

class SmartTapReader(
    private val config: SmartTapConfig,
    private val crypto: SmartTapCrypto,
) {
    fun read(card: Transceiver): NfcReadResult = try {
        readValue(card)?.let(NfcReadResult::Token) ?: NfcReadResult.WalletUnreadable
    } catch (_: SmartTapException) {
        NfcReadResult.WalletUnreadable
    } catch (_: GeneralSecurityException) {
        NfcReadResult.WalletUnreadable
    } catch (_: IllegalArgumentException) {
        NfcReadResult.WalletUnreadable
    } catch (_: CharacterCodingException) {
        NfcReadResult.WalletUnreadable
    } catch (_: IOException) {
        NfcReadResult.Failure(-1)
    }

    private fun readValue(card: Transceiver): String? {
        if (!SmartTapCommands.parseSelectOse(card.transceive(SmartTapCommands.SELECT_OSE))) return null
        val mobileNonce = SmartTapCommands.parseSelectSmartTap(card.transceive(SmartTapCommands.SELECT_SMART_TAP))

        val sessionId = crypto.randomBytes(8)
        val terminalNonce = crypto.randomBytes(32)
        val ephemeral = crypto.newEphemeral()
        val terminalPoint = crypto.compressed(ephemeral.public as ECPublicKey)
        val collectorId = be4(config.collectorId)
        val signature = crypto.sign(config.privateKey, terminalNonce, mobileNonce, collectorId, terminalPoint)
        val collector = externalRecord("cld", byteArrayOf(4) + collectorId)
        val sig = externalRecord("sig", byteArrayOf(4) + signature)
        val version = be4(config.keyVersion.toLong() and 0xffff_ffffL)
        val params = externalRecord("cpr", terminalNonce + 1.toByte() + terminalPoint + version +
            Ndef.encode(listOf(sig, collector)))

        val negotiation = request("ngr", session(sessionId, 1), params)
        val reply = SmartTapCommands.parseNegotiate(card.transceive(SmartTapCommands.negotiate(negotiation)))
        val info = hkdfInfo(terminalNonce, mobileNonce, collectorId, terminalPoint, signature)
        val keys = crypto.deriveKeys(ephemeral.private, reply.dpk, info)

        val merchant = externalRecord("mer", Ndef.encode(listOf(collector)))
        val services = externalRecord("slr", Ndef.encode(listOf(externalRecord("str", byteArrayOf(3)))))
        val data = request("srq", session(sessionId, reply.sequence + 1), merchant, services)
        val reb = SmartTapCommands.parseGetData(card.transceive(SmartTapCommands.getData(data)))
        return crypto.redemptionValue(crypto.decryptReb(reb, keys))
    }

    private fun session(id: ByteArray, sequence: Int) =
        externalRecord("ses", id + byteArrayOf(sequence.toByte(), 1))

    private fun request(type: String, vararg records: NdefRecord) =
        Ndef.encode(listOf(externalRecord(type, byteArrayOf(0, 1) + Ndef.encode(records.toList()))))
}
