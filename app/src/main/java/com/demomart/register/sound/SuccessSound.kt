package com.demomart.register.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.demomart.register.R

fun playSuccessSound(context: Context) {
    val player = MediaPlayer.create(
        context.applicationContext,
        R.raw.payment_success,
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build(),
        0,
    ) ?: return
    player.setOnCompletionListener { it.release() }
    player.start()
}
