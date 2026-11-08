package com.demomart.register.smarttap

import com.demomart.register.BuildConfig
import java.security.PrivateKey
import java.util.Base64
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter

data class SmartTapConfig(val collectorId: Long, val keyVersion: Int, val privateKey: PrivateKey) {
    val enabled get() = collectorId != 0L

    companion object {
        fun parse(collectorId: Long, keyVersion: Int, pemBase64: String): SmartTapConfig {
            require(collectorId in 1..0xffff_ffffL) { "Invalid Smart Tap collector ID" }
            val pem = Base64.getDecoder().decode(pemBase64).decodeToString()
            val pair = PEMParser(pem.reader()).use { it.readObject() as? PEMKeyPair }
            requireNotNull(pair) { "Expected a Smart Tap EC PRIVATE KEY PEM" }
            val key = JcaPEMKeyConverter().getPrivateKey(pair.privateKeyInfo)
            require(key.algorithm == "EC") { "Expected a Smart Tap EC private key" }
            return SmartTapConfig(collectorId, keyVersion, key)
        }

        fun fromBuildConfig(): SmartTapConfig? =
            if (BuildConfig.SMART_TAP_COLLECTOR_ID == 0L) null else parse(
                BuildConfig.SMART_TAP_COLLECTOR_ID,
                BuildConfig.SMART_TAP_KEY_VERSION,
                BuildConfig.SMART_TAP_PRIVATE_KEY_PEM_B64,
            )
    }
}
