/*
 * Derived from Google smart-tap-sample-app, Copyright 2022 Google LLC, Apache License 2.0.
 * Modified: ported to Kotlin and restructured for DemoMart.
 */
// Signed data: terminal nonce || mobile nonce || collector ID (4 bytes BE) || compressed terminal point.
// HKDF info: signed data || raw DER signature. Salt: the exact dpk point bytes from Wallet.
// sig = 04 || DER. cld = 04 || collector ID. cpr = terminal nonce || 01 || compressed point
// || key version (4 bytes BE) || NDEF(sig, cld).
package com.demomart.register.smarttap

import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters

data class SessionKeys(val aes: ByteArray, val hmac: ByteArray)

internal fun be4(value: Long): ByteArray {
    require(value in 0..0xffff_ffffL)
    return ByteBuffer.allocate(4).putInt(value.toInt()).array()
}

internal fun hkdfInfo(
    terminalNonce: ByteArray, mobileNonce: ByteArray, collectorId: ByteArray,
    ephemeralCompressed: ByteArray, signature: ByteArray,
): ByteArray = terminalNonce + mobileNonce + collectorId + ephemeralCompressed + signature

class SmartTapCrypto(private val random: SecureRandom = SecureRandom()) {
    internal fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    fun newEphemeral(): KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"), random)
        generateKeyPair()
    }

    fun compressed(pub: ECPublicKey): ByteArray {
        val coordinate = pub.w.affineX.toByteArray()
        val from = maxOf(0, coordinate.size - 32)
        val point = ByteArray(33)
        point[0] = if (pub.w.affineY.testBit(0)) 3 else 2
        coordinate.copyInto(point, point.size - (coordinate.size - from), from)
        return point
    }

    fun sign(
        longTerm: PrivateKey, terminalNonce: ByteArray, mobileNonce: ByteArray,
        collectorId: ByteArray, ephemeralCompressed: ByteArray,
    ): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(longTerm, random)
        update(hkdfInfo(terminalNonce, mobileNonce, collectorId, ephemeralCompressed, byteArrayOf()))
        sign()
    }

    fun deriveKeys(ephemeralPrivate: PrivateKey, mobilePublicUncompressed: ByteArray, info: ByteArray): SessionKeys {
        val curve = SECNamedCurves.getByName("secp256r1").curve
        val dpk = mobilePublicUncompressed
        require(
            (dpk.size == 65 && dpk[0] == 4.toByte()) ||
                (dpk.size == 33 && dpk[0] in 2.toByte()..3.toByte()),
        ) { "Invalid mobile EC point encoding" }
        val point = curve.decodePoint(dpk).normalize()
        val mobilePublic = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(
            ECPoint(point.affineXCoord.toBigInteger(), point.affineYCoord.toBigInteger()),
            (ephemeralPrivate as ECPrivateKey).params,
        ))
        val secret = KeyAgreement.getInstance("ECDH").run {
            init(ephemeralPrivate)
            doPhase(mobilePublic, true)
            generateSecret()
        }
        val key = ByteArray(48)
        HKDFBytesGenerator(SHA256Digest()).apply {
            init(HKDFParameters(secret, dpk, info))
            generateBytes(key, 0, key.size)
        }
        return SessionKeys(key.copyOf(16), key.copyOfRange(16, 48))
    }

    fun decryptReb(reb: ByteArray, keys: SessionKeys): ByteArray {
        if (reb.size < 45 || reb[0] != 1.toByte()) throw SmartTapException(SmartTapException.MALFORMED)
        val macOffset = reb.size - 32
        val expected = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(keys.hmac, "HmacSHA256"))
            update(reb, 1, macOffset - 1)
            doFinal()
        }
        if (!MessageDigest.isEqual(expected, reb.copyOfRange(macOffset, reb.size))) {
            throw SmartTapException(SmartTapException.INTEGRITY)
        }
        val iv = reb.copyOfRange(1, 13) + ByteArray(4)
        return Cipher.getInstance("AES/CTR/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(keys.aes, "AES"), IvParameterSpec(iv))
            doFinal(reb, 13, macOffset - 13)
        }
    }

    fun redemptionValue(decrypted: ByteArray): String? {
        val services = Ndef.findExternal(Ndef.decode(decrypted), "asv") ?: return null
        val loyalty = Ndef.findExternal(Ndef.decode(services.payload), "ly") ?: return null
        val value = Ndef.decode(loyalty.payload).firstOrNull {
            it.id.contentEquals(byteArrayOf(0x6e)) && it.payload.size > 1
        } ?: return null
        return value.payload.decodeToString(startIndex = 1, throwOnInvalidSequence = true)
    }
}
