package com.demomart.register.smarttap

import java.io.StringWriter
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.asn1.sec.SECObjectIdentifiers
import org.bouncycastle.asn1.sec.ECPrivateKey as Sec1Key
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import org.junit.Assert.*
import org.junit.Test

internal fun uncompressed(publicKey: ECPublicKey): ByteArray = byteArrayOf(4) +
    listOf(publicKey.w.affineX, publicKey.w.affineY).flatMap { coordinate ->
        coordinate.toString(16).padStart(64, '0').chunked(2).map { it.toInt(16).toByte() }
    }.toByteArray()

internal fun encryptReb(plaintext: ByteArray, keys: SessionKeys): ByteArray {
    val iv = ByteArray(12) { it.toByte() }
    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keys.aes, "AES"), IvParameterSpec(iv + ByteArray(4)))
    val authenticated = iv + cipher.doFinal(plaintext)
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(keys.hmac, "HmacSHA256"))
    return byteArrayOf(1) + authenticated + mac.doFinal(authenticated)
}

class SmartTapCryptoTest {
    private val crypto = SmartTapCrypto()

    @Test fun compressedKeyIs33Bytes() {
        val pub = crypto.newEphemeral().public as ECPublicKey
        val compressed = crypto.compressed(pub)
        assertEquals(33, compressed.size)
        assertEquals(if (pub.w.affineY.testBit(0)) 3 else 2, compressed[0].toInt())
        assertArrayEquals(uncompressed(pub).copyOfRange(1, 33), compressed.copyOfRange(1, 33))
    }

    @Test fun intEncodings() {
        assertArrayEquals(bytes("0133EE80"), be4(20180608))
        assertArrayEquals(bytes("00000001"), be4(1))
        assertArrayEquals(bytes("FFFFFFFF"), be4(0xffff_ffffL))
    }

    @Test fun infoMatchesDocumentedLayout() {
        val fields = bytes("01") to bytes("02")
        assertArrayEquals(bytes("01020133EE800330"), hkdfInfo(fields.first, fields.second, be4(20180608),
            bytes("03"), bytes("30")))
    }

    @Test fun signatureVerifiesWithPublicKey() {
        val pair = crypto.newEphemeral()
        val signature = crypto.sign(pair.private, bytes("01"), bytes("02"), be4(20180608), bytes("03"))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(pair.public)
        verifier.update(bytes("01020133EE8003"))
        assertTrue(verifier.verify(signature))
    }

    @Test fun deriveKeysMatchesBothSides() {
        val terminal = crypto.newEphemeral()
        val mobile = crypto.newEphemeral()
        val mobilePublic = uncompressed(mobile.public as ECPublicKey)
        val info = bytes("11223344")
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(mobile.private)
        agreement.doPhase(terminal.public, true)
        fun hmac(key: ByteArray, value: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(value)
        }
        val prk = hmac(mobilePublic, agreement.generateSecret())
        val first = hmac(prk, info + 1.toByte())
        val second = hmac(prk, first + info + 2.toByte())
        val keys = crypto.deriveKeys(terminal.private, mobilePublic, info)
        assertArrayEquals((first + second).copyOf(48), keys.aes + keys.hmac)
    }

    @Test fun decryptRoundTrip() {
        val keys = SessionKeys(ByteArray(16) { 1 }, ByteArray(32) { 2 })
        val plaintext = "hello".encodeToByteArray()
        assertArrayEquals(plaintext, crypto.decryptReb(encryptReb(plaintext, keys), keys))
    }

    @Test fun tamperedPayloadRejected() {
        val keys = SessionKeys(ByteArray(16) { 1 }, ByteArray(32) { 2 })
        val encrypted = encryptReb("hello".encodeToByteArray(), keys)
        encrypted[13] = (encrypted[13].toInt() xor 1).toByte()
        val failure = assertThrows(SmartTapException::class.java) { crypto.decryptReb(encrypted, keys) }
        assertEquals(SmartTapException.INTEGRITY, failure.sw)
    }

    @Test fun truncatedPayloadRejected() {
        assertThrows(SmartTapException::class.java) {
            crypto.decryptReb(ByteArray(44), SessionKeys(ByteArray(16), ByteArray(32)))
        }
    }

    @Test fun redemptionValueFromNdef() {
        val value = "DMW1:48210093:ABCDEFGH23"
        val record = externalRecord("txt", byteArrayOf(2) + value.encodeToByteArray(), "n")
        val loyalty = externalRecord("ly", Ndef.encode(listOf(record)))
        assertEquals(value, crypto.redemptionValue(Ndef.encode(listOf(externalRecord("asv",
            Ndef.encode(listOf(loyalty)))))))
    }

    @Test fun configDisabledWithoutCollector() {
        if (com.demomart.register.BuildConfig.SMART_TAP_COLLECTOR_ID == 0L) {
            assertNull(SmartTapConfig.fromBuildConfig())
        }
    }

    @Test fun parsesBase64Pem() {
        val original = crypto.newEphemeral().private as ECPrivateKey
        val sec1 = Sec1Key(256, original.s, SECObjectIdentifiers.secp256r1).encoded
        val out = StringWriter()
        PemWriter(out).use { it.writeObject(PemObject("EC PRIVATE KEY", sec1)) }
        val config = SmartTapConfig.parse(20180608, 1, Base64.getEncoder().encodeToString(out.toString().encodeToByteArray()))
        assertEquals("EC", config.privateKey.algorithm)
        assertEquals(original.s, (config.privateKey as ECPrivateKey).s)
    }
}
