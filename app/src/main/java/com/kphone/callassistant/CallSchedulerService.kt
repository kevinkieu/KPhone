package com.kphone.callassistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.ceil

class CallSchedulerService : Service() {

    private var countDownTimer: CountDownTimer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopCountdownAndService()
            return START_NOT_STICKY
        }

        val seconds = intent?.getIntExtra(EXTRA_SECONDS, 10) ?: 10
        val callerName = intent?.getStringExtra(CallAlarmReceiver.EXTRA_CALLER_NAME) ?: "Sếp Tổng"
        val callerNumber = intent?.getStringExtra(CallAlarmReceiver.EXTRA_CALLER_NUMBER) ?: "+84 988 888 888"
        val ringtoneUri = intent?.getStringExtra(CallAlarmReceiver.EXTRA_RINGTONE_URI)

        acquireWakeLock(seconds)
        startForegroundNotification(seconds)
        startCountdown(seconds, callerName, callerNumber, ringtoneUri)

        return START_REDELIVER_INTENT
    }

    private fun acquireWakeLock(seconds: Int) {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "kphone:scheduler_cpu_wakelock"
            ).apply {
                setReferenceCounted(false)
                acquire((seconds + 30) * 1000L)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startForegroundNotification(secondsLeft: Int) {
        try {
            val channelId = "kphone_scheduler_channel"
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "KPhone Timer",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Đếm ngược cuộc gọi hẹn giờ"
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("KPhone")
                .setContentText("Cuộc gọi sẽ đến sau ${secondsLeft}s...")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    SERVICE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(SERVICE_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val channelId = "kphone_scheduler_channel"
                val notification = NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("KPhone")
                    .setContentText("Đang hẹn giờ cuộc gọi...")
                    .build()
                startForeground(SERVICE_NOTIFICATION_ID, notification)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun startCountdown(
        seconds: Int,
        callerName: String,
        callerNumber: String,
        ringtoneUri: String?
    ) {
        countDownTimer?.cancel()
        _remainingSeconds.value = seconds

        countDownTimer = object : CountDownTimer(seconds * 1000L, 500L) {
            override fun onTick(millisUntilFinished: Long) {
                val secLeft = ceil(millisUntilFinished / 1000.0).toInt().coerceAtLeast(1)
                if (_remainingSeconds.value != secLeft) {
                    _remainingSeconds.value = secLeft
                }
            }

            override fun onFinish() {
                _remainingSeconds.value = 0
                CallAlarmReceiver.triggerIncomingCallNow(
                    this@CallSchedulerService,
                    callerName,
                    callerNumber,
                    ringtoneUri
                )
                // Keep foreground service & CPU wakelock alive for 4 seconds while IncomingCallActivity wakes the screen
                mainHandler.postDelayed({
                    stopCountdownAndService()
                }, 4000L)
            }
        }.start()
    }

    private fun stopCountdownAndService() {
        countDownTimer?.cancel()
        countDownTimer = null
        _remainingSeconds.value = 0
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        _remainingSeconds.value = 0
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val SERVICE_NOTIFICATION_ID = 8888
        const val EXTRA_SECONDS = "extra_seconds"
        const val ACTION_STOP_SERVICE = "com.kphone.callassistant.ACTION_STOP_SERVICE"

        private val _remainingSeconds = MutableStateFlow(0)
        val remainingSeconds: StateFlow<Int> = _remainingSeconds

        fun start(
            context: Context,
            seconds: Int,
            callerName: String,
            callerNumber: String,
            ringtoneUri: String?
        ) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, CallSchedulerService::class.java).apply {
                putExtra(EXTRA_SECONDS, seconds)
                putExtra(CallAlarmReceiver.EXTRA_CALLER_NAME, callerName)
                putExtra(CallAlarmReceiver.EXTRA_CALLER_NUMBER, callerNumber)
                putExtra(CallAlarmReceiver.EXTRA_RINGTONE_URI, ringtoneUri)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            _remainingSeconds.value = 0
            try {
                val appContext = context.applicationContext
                appContext.stopService(Intent(appContext, CallSchedulerService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
