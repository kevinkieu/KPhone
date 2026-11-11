package com.kphone.callassistant

import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class CallAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DECLINE_CALL) {
            SoundVibrationManager.stopRinging()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
            CallSchedulerService.stop(context)
            return
        }

        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Sếp Tổng"
        val callerNumber = intent.getStringExtra(EXTRA_CALLER_NUMBER) ?: "+84 988 888 888"
        val ringtoneUri = intent.getStringExtra(EXTRA_RINGTONE_URI)

        triggerIncomingCallNow(context, callerName, callerNumber, ringtoneUri)
    }

    companion object {
        const val NOTIFICATION_ID = 9999
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        const val EXTRA_RINGTONE_URI = "extra_ringtone_uri"
        const val ACTION_TRIGGER_CALL = "com.kphone.callassistant.ACTION_TRIGGER_CALL"
        const val ACTION_DECLINE_CALL = "com.kphone.callassistant.ACTION_DECLINE_CALL"

        fun createIncomingCallIntent(
            context: Context,
            callerName: String,
            callerNumber: String,
            ringtoneUriStr: String?
        ): Intent {
            return Intent(context.applicationContext, IncomingCallActivity::class.java).apply {
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_CALLER_NUMBER, callerNumber)
                putExtra(EXTRA_RINGTONE_URI, ringtoneUriStr)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_NO_USER_ACTION
                )
            }
        }

        fun createCreatorActivityOptions(): android.os.Bundle? {
            if (Build.VERSION.SDK_INT >= 34) {
                try {
                    return ActivityOptions.makeBasic()
                        .setPendingIntentCreatorBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                        .toBundle()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return null
        }

        fun createSenderActivityOptions(): android.os.Bundle? {
            if (Build.VERSION.SDK_INT >= 34) {
                try {
                    return ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                        .toBundle()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return null
        }

        fun buildIncomingCallNotification(
            context: Context,
            callerName: String,
            callerNumber: String,
            ringtoneUriStr: String?
        ): Notification {
            val appContext = context.applicationContext
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "kphone_incoming_call_channel_v6"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    appContext.getString(R.string.incoming_call),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Incoming Call FullScreen Alert"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                    setSound(null, null)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val fullScreenIntent = createIncomingCallIntent(appContext, callerName, callerNumber, ringtoneUriStr)
            val creatorOptions = createCreatorActivityOptions()

            val fullScreenPendingIntent = PendingIntent.getActivity(
                appContext,
                1001,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                creatorOptions
            )

            return NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(callerName)
                .setContentText("$callerNumber • ${appContext.getString(R.string.incoming_call)}")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .setOngoing(true)
                .setAutoCancel(true)
                .build()
        }

        fun triggerIncomingCallNow(
            context: Context,
            callerName: String,
            callerNumber: String,
            ringtoneUriStr: String?
        ) {
            val appContext = context.applicationContext

            // 1. Physically wake up screen out of AOD & start ringing + vibrating immediately
            SoundVibrationManager.acquireScreenWakeLock(appContext)
            SoundVibrationManager.startRinging(appContext, ringtoneUriStr)

            // 2. Post standard high-priority CATEGORY_CALL notification with fullScreenIntent
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = buildIncomingCallNotification(appContext, callerName, callerNumber, ringtoneUriStr)
            notificationManager.notify(NOTIFICATION_ID, notification)

            // 3. Launch IncomingCallActivity directly
            val fullScreenIntent = createIncomingCallIntent(appContext, callerName, callerNumber, ringtoneUriStr)
            val senderOptions = createSenderActivityOptions()
            try {
                if (senderOptions != null) {
                    appContext.startActivity(fullScreenIntent, senderOptions)
                } else {
                    appContext.startActivity(fullScreenIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun scheduleCall(
            context: Context,
            secondsInFuture: Int,
            callerName: String,
            callerNumber: String,
            ringtoneUri: String?
        ) {
            val appContext = context.applicationContext
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val fullScreenIntent = createIncomingCallIntent(appContext, callerName, callerNumber, ringtoneUri)
            val creatorOptions = createCreatorActivityOptions()

            // Direct Activity PendingIntent for setAlarmClock:
            // Android System Server (AlarmManagerService) launches IncomingCallActivity directly with BAL_ALLOW_ALARM_CLOCK privilege!
            val directActivityPendingIntent = PendingIntent.getActivity(
                appContext,
                1002,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                creatorOptions
            )

            val showIntent = Intent(appContext, MainActivity::class.java)
            val showPendingIntent = PendingIntent.getActivity(
                appContext,
                1005,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerTime = System.currentTimeMillis() + (secondsInFuture * 1000L)

            try {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, directActivityPendingIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun cancelScheduledCall(context: Context) {
            try {
                val appContext = context.applicationContext
                val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val fullScreenIntent = Intent(appContext, IncomingCallActivity::class.java)
                val directActivityPendingIntent = PendingIntent.getActivity(
                    appContext,
                    1002,
                    fullScreenIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(directActivityPendingIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
