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
import androidx.core.app.Person

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

        @Volatile
        private var lastTriggerTimeMillis: Long = 0L

        @Synchronized
        fun triggerIncomingCallNow(
            context: Context,
            callerName: String,
            callerNumber: String,
            ringtoneUriStr: String?
        ) {
            val now = System.currentTimeMillis()
            if (now - lastTriggerTimeMillis < 3500L) {
                return
            }
            lastTriggerTimeMillis = now

            val appContext = context.applicationContext

            // 1. Physically wake up screen & start ringing + vibrating immediately
            SoundVibrationManager.acquireScreenWakeLock(appContext)
            SoundVibrationManager.startRinging(appContext, ringtoneUriStr)

            // 2. Build the IncomingCallActivity Intent
            val fullScreenIntent = Intent(appContext, IncomingCallActivity::class.java).apply {
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_CALLER_NUMBER, callerNumber)
                putExtra(EXTRA_RINGTONE_URI, ringtoneUriStr)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NO_USER_ACTION
                )
            }

            // 3. Create NotificationChannel with max priority and lockscreen visibility
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "kphone_incoming_call_channel_v5"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    appContext.getString(R.string.incoming_call),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Incoming Call Alert"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                    setSound(null, null)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val activityOptionsBundle = if (Build.VERSION.SDK_INT >= 34) {
                try {
                    ActivityOptions.makeBasic()
                        .setPendingIntentCreatorBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                        .toBundle()
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }

            val fullScreenPendingIntent = PendingIntent.getActivity(
                appContext,
                1001,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                activityOptionsBundle
            )

            // Answer Intent (opens InCallActivity)
            val answerIntent = Intent(appContext, InCallActivity::class.java).apply {
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_CALLER_NUMBER, callerNumber)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val answerPendingIntent = PendingIntent.getActivity(
                appContext,
                1003,
                answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                activityOptionsBundle
            )

            // Decline Intent
            val declineIntent = Intent(appContext, CallAlarmReceiver::class.java).apply {
                action = ACTION_DECLINE_CALL
            }
            val declinePendingIntent = PendingIntent.getBroadcast(
                appContext,
                1004,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(callerName)
                .setContentText(callerNumber)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)

            try {
                val callerPerson = Person.Builder()
                    .setName(callerName.ifBlank { "Unknown" })
                    .setImportant(true)
                    .build()
                builder.setStyle(
                    NotificationCompat.CallStyle.forIncomingCall(
                        callerPerson,
                        declinePendingIntent,
                        answerPendingIntent
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            notificationManager.notify(NOTIFICATION_ID, builder.build())

            // 4. Direct launch of IncomingCallActivity (works via setAlarmClock BAL exemption or Overlay)
            try {
                if (activityOptionsBundle != null) {
                    appContext.startActivity(fullScreenIntent, activityOptionsBundle)
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
            val intent = Intent(appContext, CallAlarmReceiver::class.java).apply {
                action = ACTION_TRIGGER_CALL
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_CALLER_NUMBER, callerNumber)
                putExtra(EXTRA_RINGTONE_URI, ringtoneUri)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                1002,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
                // setAlarmClock is NEVER deferred by Doze mode when screen is off AND grants BAL exemption on Android 14-16!
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } catch (e: SecurityException) {
                try {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } catch (ex: Exception) {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun cancelScheduledCall(context: Context) {
            try {
                val appContext = context.applicationContext
                val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(appContext, CallAlarmReceiver::class.java).apply {
                    action = ACTION_TRIGGER_CALL
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    1002,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
