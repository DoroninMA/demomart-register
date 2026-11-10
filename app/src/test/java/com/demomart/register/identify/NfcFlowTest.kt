package com.demomart.register.identify

import android.nfc.NfcAdapter
import com.demomart.register.smarttap.SmartTapConfig
import com.demomart.register.smarttap.SmartTapCrypto
import com.demomart.register.smarttap.SmartTapReader
import com.demomart.register.smarttap.Transceiver
import com.demomart.register.smarttap.bytes
import org.junit.Assert.*
import org.junit.Test

class NfcFlowTest {
    @Test fun pollsBothIsoDepTechnologies() {
        assertTrue(NFC_READER_FLAGS and NfcAdapter.FLAG_READER_NFC_A != 0)
        assertTrue(NFC_READER_FLAGS and NfcAdapter.FLAG_READER_NFC_B != 0)
        assertTrue(NFC_READER_FLAGS and NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK != 0)
    }

    @Test fun hceAnswerSkipsSmartTap() {
        val crypto = SmartTapCrypto()
        val reader = SmartTapReader(SmartTapConfig(20180608, 1, crypto.newEphemeral().private), crypto)
        val token = "DM1:48210093:877905"
        var commands = 0
        val tag = Transceiver {
            commands++
            assertArrayEquals(ApduCodec.selectCommand(), it)
            token.encodeToByteArray() + bytes("9000")
        }
        assertEquals(NfcReadResult.Token(token), readTag(tag, reader))
        assertEquals(1, commands)
    }

    @Test fun smartTapDisabledKeepsPlan02() {
        assertEquals(NfcReadResult.AppNotFound, readTag(Transceiver { bytes("6a82") }, null))
    }
}
