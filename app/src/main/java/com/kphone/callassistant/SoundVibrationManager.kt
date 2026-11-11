package com.kphone.callassistant

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object SoundVibrationManager {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var screenWakeLock: PowerManager.WakeLock? = null
    private var isCurrentlyRinging: Boolean = false

    @Synchronized
    fun acquireScreenWakeLock(context: Context) {
        try {
            if (screenWakeLock?.isHeld == true) return
            val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            screenWakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "kphone:incoming_call_screen_wakeup"
            ).apply {
                setReferenceCounted(false)
                acquire(60_000L) // Hold screen bright for up to 60s while ringing
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun startRinging(context: Context, customRingtoneUri: String?) {
        if (isCurrentlyRinging && mediaPlayer?.isPlaying == true) {
            return
        }
        stopRinging()
        isCurrentlyRinging = true
        val appContext = context.applicationContext

        acquireScreenWakeLock(appContext)

        // Setup Vibration
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                @Suppress("DEPRECATION")
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0), audioAttrs)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Setup Ringtone Audio
        try {
            val ringtoneUri = if (!customRingtoneUri.isNullOrEmpty()) {
                Uri.parse(customRingtoneUri)
            } else {
                RingtoneManager.getActualDefaultRingtoneUri(appContext, RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(appContext, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mediaPlayer = MediaPlayer.create(appContext, defaultUri)?.apply {
                    isLooping = true
                    start()
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    @Synchronized
    fun stopRinging() {
        isCurrentlyRinging = false
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
        }

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            vibrator = null
        }

        try {
            if (screenWakeLock?.isHeld == true) {
                screenWakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            screenWakeLock = null
        }
    }
}
