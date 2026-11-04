package com.demomart.register.identify

import android.app.Activity
import android.media.AudioManager
import android.media.ToneGenerator
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Handler
import android.os.Looper
import java.io.IOException

enum class NfcAvailability { Ready, Disabled, Unsupported }

class NfcReader(private val activity: Activity) {
    private val adapter = NfcAdapter.getDefaultAdapter(activity)
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var active = false

    fun availability(): NfcAvailability = when {
        adapter == null -> NfcAvailability.Unsupported
        !adapter.isEnabled -> NfcAvailability.Disabled
        else -> NfcAvailability.Ready
    }

    fun start(onResult: (NfcReadResult) -> Unit) {
        if (adapter == null || active) return
        val current = ++generation
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        adapter.enableReaderMode(activity, { tag ->
            val result = read(tag)
            activity.runOnUiThread {
                if (!active || current != generation) return@runOnUiThread
                if (result is NfcReadResult.Token) beep()
                onResult(result)
            }
        }, flags, null)
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
                it.timeout = 2000
                ApduCodec.parse(it.transceive(ApduCodec.selectCommand()))
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
