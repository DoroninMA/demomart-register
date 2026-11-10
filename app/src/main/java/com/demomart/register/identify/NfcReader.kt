package com.demomart.register.identify

import android.app.Activity
import android.media.AudioManager
import android.media.ToneGenerator
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.demomart.register.BuildConfig
import com.demomart.register.smarttap.SmartTapConfig
import com.demomart.register.smarttap.SmartTapCrypto
import com.demomart.register.smarttap.SmartTapReader
import com.demomart.register.smarttap.Transceiver
import java.io.IOException

internal const val NFC_READER_FLAGS =
    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
        NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

enum class NfcAvailability { Ready, Disabled, Unsupported }

fun readTag(card: Transceiver, smartTap: SmartTapReader?): NfcReadResult = try {
    val hce = ApduCodec.parse(card.transceive(ApduCodec.selectCommand()))
    if (hce == NfcReadResult.AppNotFound && smartTap != null) smartTap.read(card) else hce
} catch (_: IOException) {
    NfcReadResult.Failure(-1)
}

class NfcReader(private val activity: Activity) {
    private val adapter = NfcAdapter.getDefaultAdapter(activity)
    private val smartTap = runCatching { SmartTapConfig.fromBuildConfig() }
        .onFailure { Log.w("SmartTap", "Invalid terminal configuration") }
        .getOrNull()?.let { SmartTapReader(it, SmartTapCrypto()) }
    private val verboseLog = BuildConfig.DEBUG && BuildConfig.SMART_TAP_COLLECTOR_ID == 20180608L
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var active = false
    val isActive get() = active

    fun availability(): NfcAvailability = when {
        adapter == null -> NfcAvailability.Unsupported
        !adapter.isEnabled -> NfcAvailability.Disabled
        else -> NfcAvailability.Ready
    }

    fun start(onResult: (NfcReadResult) -> Unit) {
        if (adapter == null || active) return
        val current = ++generation
        adapter.enableReaderMode(activity, { tag ->
            if (verboseLog) Log.d("SmartTap", "Tag discovered: ${tag.techList.joinToString()}")
            val result = read(tag)
            activity.runOnUiThread {
                if (!active || current != generation) return@runOnUiThread
                if (result is NfcReadResult.Token) beep()
                onResult(result)
            }
        }, NFC_READER_FLAGS, null)
        active = true
    }

    fun stop() {
        if (!active) return
        active = false
        generation++
        adapter?.disableReaderMode(activity)
    }

    private fun read(tag: Tag): NfcReadResult {
        val isoDep = IsoDep.get(tag) ?: return NfcReadResult.AppNotFound
        return try {
            isoDep.use {
                it.connect()
                it.timeout = if (smartTap == null) 2000 else 3000
                val started = System.nanoTime()
                val result = readTag(Transceiver { apdu ->
                    it.transceive(apdu).also { response ->
                        if (verboseLog && response.size >= 2) {
                            val ins = apdu[1].toInt() and 0xff
                            val sw = ((response[response.size - 2].toInt() and 0xff) shl 8) or
                                (response.last().toInt() and 0xff)
                            Log.d("SmartTap", "APDU ${ins.toString(16)} -> ${sw.toString(16)}")
                        }
                    }
                }, smartTap)
                if (verboseLog && result == NfcReadResult.Token("2018")) {
                    val elapsedMs = (System.nanoTime() - started) / 1_000_000
                    Log.d("SmartTap", "SmartTap value: 2018 (${elapsedMs}ms)")
                }
                result
            }
        } catch (_: IOException) {
            NfcReadResult.Failure(-1)
        } catch (_: SecurityException) {
            NfcReadResult.Failure(-1)
        }
    }

    private fun beep() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 65)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            main.postDelayed(tone::release, 180)
        }
    }
}
