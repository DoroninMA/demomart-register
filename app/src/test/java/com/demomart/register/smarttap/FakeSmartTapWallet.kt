package com.demomart.register.smarttap

import com.demomart.register.identify.ApduCodec
import java.io.IOException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.KeyAgreement
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters

internal class FakeSmartTapWallet(
    private val terminalPublicKey: PublicKey,
    private val collectorId: Long,
    private val redemptionValue: String,
    private val mode: Mode = Mode.Normal,
) : Transceiver {
    enum class Mode { Normal, Refuse9500, BadHmac, NoSmartTapAid, DropAfterNegotiate }

    val commands = mutableListOf<ByteArray>()
    val terminalNonces = mutableListOf<ByteArray>()
    private val crypto = SmartTapCrypto()
    private var mobileNonce = byteArrayOf()
    private var sessionId = byteArrayOf()
    private lateinit var keys: SessionKeys

    override fun transceive(apdu: ByteArray): ByteArray {
        commands += apdu.copyOf()
        return when {
            apdu.contentEquals(ApduCodec.selectCommand()) -> bytes("6a82")
            apdu.contentEquals(SmartTapCommands.SELECT_OSE) -> if (mode == Mode.NoSmartTapAid) {
                bytes("6F0AA508BF0C0561034F01009000")
            } else bytes("6F12A510BF0C0D610B4F09A000000476D00001119000")
            apdu.contentEquals(SmartTapCommands.SELECT_SMART_TAP) -> {
                mobileNonce = crypto.randomBytes(32)
                bytes("00010001") + message(externalRecord("mdn", byteArrayOf(4) + mobileNonce)) + bytes("9000")
            }
            apdu[1] == 0x53.toByte() -> negotiate(apdu)
            apdu[1] == 0x50.toByte() -> data(apdu)
            else -> error("Unexpected APDU")
        }
    }

    private fun request(apdu: ByteArray, type: String): List<NdefRecord> {
        check((apdu[4].toInt() and 255) == apdu.size - 6 && apdu.last() == 0.toByte())
        val request = Ndef.decode(apdu.copyOfRange(5, apdu.lastIndex)).single()
        check(request.type.contentEquals(type.encodeToByteArray()))
        check(request.payload.copyOf(2).contentEquals(bytes("0001")))
        return Ndef.decode(request.payload.copyOfRange(2, request.payload.size))
    }

    private fun negotiate(apdu: ByteArray): ByteArray {
        if (mode == Mode.Refuse9500) return bytes("9500")
        val request = request(apdu, "ngr")
        val session = checkNotNull(Ndef.findExternal(request, "ses")).payload
        check(session.size == 10 && session[8] == 1.toByte() && session[9] == 1.toByte())
        sessionId = session.copyOf(8)
        val params = checkNotNull(Ndef.findExternal(request, "cpr")).payload
        val terminalNonce = params.copyOf(32)
        terminalNonces += terminalNonce
        check(params[32] == 1.toByte())
        val terminalPoint = params.copyOfRange(33, 66)
        check(params.copyOfRange(66, 70).contentEquals(bytes("00000001")))
        val records = Ndef.decode(params.copyOfRange(70, params.size))
        val cld = checkNotNull(Ndef.findExternal(records, "cld")).payload
        check(cld.contentEquals(byteArrayOf(4) + be4(collectorId)))
        val sig = checkNotNull(Ndef.findExternal(records, "sig")).payload
        check(sig[0] == 4.toByte())
        val signature = sig.copyOfRange(1, sig.size)
        val transcript = terminalNonce + mobileNonce + cld.copyOfRange(1, 5) + terminalPoint
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(terminalPublicKey)
        verifier.update(transcript)
        check(verifier.verify(signature))

        val mobile = crypto.newEphemeral()
        val mobilePublic = mobile.public as ECPublicKey
        val point = SECNamedCurves.getByName("secp256r1").curve.decodePoint(terminalPoint).normalize()
        val terminalKey = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(
            ECPoint(point.affineXCoord.toBigInteger(), point.affineYCoord.toBigInteger()), mobilePublic.params,
        ))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(mobile.private)
        agreement.doPhase(terminalKey, true)
        val dpk = uncompressed(mobilePublic)
        val material = ByteArray(48)
        HKDFBytesGenerator(SHA256Digest()).apply {
            init(HKDFParameters(agreement.generateSecret(), dpk, transcript + signature))
            generateBytes(material, 0, material.size)
        }
        keys = SessionKeys(material.copyOf(16), material.copyOfRange(16, 48))
        return message(externalRecord("nrs", message(
            externalRecord("ses", sessionId + bytes("0201")), externalRecord("dpk", dpk),
        ))) + bytes("9000")
    }

    private fun data(apdu: ByteArray): ByteArray {
        val request = request(apdu, "srq")
        check(Ndef.findExternal(request, "ses")?.payload?.contentEquals(sessionId + bytes("0301")) == true)
        val merchant = checkNotNull(Ndef.findExternal(request, "mer"))
        check(Ndef.findExternal(Ndef.decode(merchant.payload), "cld")?.payload
            ?.contentEquals(byteArrayOf(4) + be4(collectorId)) == true)
        val list = checkNotNull(Ndef.findExternal(request, "slr"))
        check(Ndef.findExternal(Ndef.decode(list.payload), "str")?.payload?.contentEquals(bytes("03")) == true)
        if (mode == Mode.DropAfterNegotiate) throw IOException("Tag removed")
        val value = externalRecord("txt", byteArrayOf(2) + redemptionValue.encodeToByteArray(), "n")
        val plaintext = message(externalRecord("asv", message(externalRecord("ly", message(value)))))
        val encrypted = encryptReb(plaintext, keys)
        if (mode == Mode.BadHmac) encrypted[13] = (encrypted[13].toInt() xor 1).toByte()
        return message(externalRecord("srs", message(externalRecord("reb", encrypted)))) + bytes("9000")
    }

    private fun message(vararg records: NdefRecord): ByteArray = Ndef.encode(records.toList())
}
