package com.comai.scheduling

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Handles playing and stopping audible alarms for scheduled Alarm reminders.
 * Audio is routed via the alarm audio stream (USAGE_ALARM).
 */
object AlarmAudioPlayer {

    private var activeRingtone: Ringtone? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable { stop() }

    @Synchronized
    fun play(context: Context) {
        stop()
        try {
            val alertUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val ringtone = RingtoneManager.getRingtone(context.applicationContext, alertUri)
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ringtone.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    ringtone.streamType = AudioManager.STREAM_ALARM
                }
                activeRingtone = ringtone
                ringtone.play()

                // Auto-stop after 60 seconds if untouched
                mainHandler.postDelayed(autoStopRunnable, 60000L)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun stop() {
        try {
            mainHandler.removeCallbacks(autoStopRunnable)
            activeRingtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
            activeRingtone = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isPlaying(): Boolean {
        return activeRingtone?.isPlaying == true
    }
}
