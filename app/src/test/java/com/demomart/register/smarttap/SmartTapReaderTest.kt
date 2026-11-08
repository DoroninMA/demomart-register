package com.demomart.register.smarttap

import com.demomart.register.identify.ApduCodec
import com.demomart.register.identify.NfcReadResult
import com.demomart.register.identify.readTag
import org.junit.Assert.*
import org.junit.Test

class SmartTapReaderTest {
    private val crypto = SmartTapCrypto()
    private val key = crypto.newEphemeral()
    private val collector = 20180608L
    private val token = "DMW1:48210093:ABCDEFGH23"
    private val reader = SmartTapReader(SmartTapConfig(collector, 1, key.private), crypto)

    private fun wallet(mode: FakeSmartTapWallet.Mode = FakeSmartTapWallet.Mode.Normal) =
        FakeSmartTapWallet(key.public, collector, token, mode)

    @Test fun appNotFoundFallsBackToSmartTap() {
        assertEquals(NfcReadResult.Token(token), readTag(wallet(), reader))
    }

    @Test fun noSmartTapAidIsUnreadable() {
        assertEquals(NfcReadResult.WalletUnreadable, readTag(wallet(FakeSmartTapWallet.Mode.NoSmartTapAid), reader))
    }

    @Test fun authRefused9500() {
        assertEquals(NfcReadResult.WalletUnreadable, readTag(wallet(FakeSmartTapWallet.Mode.Refuse9500), reader))
    }

    @Test fun badHmacIsUnreadable() {
        assertEquals(NfcReadResult.WalletUnreadable, readTag(wallet(FakeSmartTapWallet.Mode.BadHmac), reader))
    }

    @Test fun ioExceptionMidSession() {
        val wallet = wallet(FakeSmartTapWallet.Mode.DropAfterNegotiate)
        repeat(2) { assertEquals(NfcReadResult.Failure(-1), readTag(wallet, reader)) }
        assertEquals(2, wallet.terminalNonces.size)
        assertFalse(wallet.terminalNonces[0].contentEquals(wallet.terminalNonces[1]))
    }

    @Test fun commandOrder() {
        val wallet = wallet()
        assertEquals(NfcReadResult.Token(token), readTag(wallet, reader))
        assertEquals(5, wallet.commands.size)
        assertArrayEquals(ApduCodec.selectCommand(), wallet.commands[0])
        assertArrayEquals(SmartTapCommands.SELECT_OSE, wallet.commands[1])
        assertArrayEquals(SmartTapCommands.SELECT_SMART_TAP, wallet.commands[2])
        assertArrayEquals(bytes("90530000"), wallet.commands[3].copyOf(4))
        assertArrayEquals(bytes("90500000"), wallet.commands[4].copyOf(4))
    }
}
