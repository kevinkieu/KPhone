package com.kphone.callassistant

import android.app.KeyguardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class InCallActivity : ComponentActivity(), SensorEventListener {

    private var callerName: String = "Sếp Tổng"
    private var callerNumber: String = "+84 988 888 888"

    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        turnScreenOnAndKeyguard()
        super.onCreate(savedInstanceState)

        SoundVibrationManager.stopRinging()
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(CallAlarmReceiver.NOTIFICATION_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        CallSchedulerService.stop(this)

        callerName = intent.getStringExtra(CallAlarmReceiver.EXTRA_CALLER_NAME) ?: "Sếp Tổng"
        callerNumber = intent.getStringExtra(CallAlarmReceiver.EXTRA_CALLER_NUMBER) ?: "+84 988 888 888"

        setupProximitySensor()

        setContent {
            MaterialTheme {
                Android16InCallScreen(
                    callerName = callerName,
                    callerNumber = callerNumber,
                    onEndCall = {
                        finish()
                    }
                )
            }
        }
    }

    private fun turnScreenOnAndKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
    }

    private fun setupProximitySensor() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val field = PowerManager::class.java.getField("PROXIMITY_SCREEN_OFF_WAKE_LOCK")
            val proximityWakeLock = field.getInt(null)
            wakeLock = powerManager.newWakeLock(proximityWakeLock, "kphone:in_call_proximity")
            wakeLock?.acquire()
        } catch (e: Exception) {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            proximitySensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {}

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun Android16InCallScreen(
    callerName: String,
    callerNumber: String,
    onEndCall: () -> Unit
) {
    var callSeconds by remember { mutableIntStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var isHold by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            callSeconds++
        }
    }

    val minutes = callSeconds / 60
    val seconds = callSeconds % 60
    val durationFormatted = String.format("%02d:%02d", minutes, seconds)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF191D24),
                        Color(0xFF111317),
                        Color(0xFF090A0D)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header info: Duration, Avatar, Caller Details
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Text(
                    text = durationFormatted,
                    color = Color(0xFFA8C7FA),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(28.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF282D36))
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFFD2E3FC),
                        modifier = Modifier.size(72.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = callerName,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = callerNumber,
                    color = Color(0xFFBDC1C6),
                    fontSize = 17.sp
                )
            }

            // Material 3 In-Call Action Grid (Minimal, No Labels, Pure Modern Android 16 look)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MinimalCallButton(
                        icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = stringResource(R.string.action_mute),
                        isActive = isMuted,
                        onClick = { isMuted = !isMuted }
                    )
                    MinimalCallButton(
                        icon = Icons.Default.Dialpad,
                        contentDescription = stringResource(R.string.action_keypad),
                        isActive = false,
                        onClick = {}
                    )
                    MinimalCallButton(
                        icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute,
                        contentDescription = stringResource(R.string.action_speaker),
                        isActive = isSpeakerOn,
                        onClick = { isSpeakerOn = !isSpeakerOn }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MinimalCallButton(
                        icon = Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add_call),
                        isActive = false,
                        onClick = {}
                    )
                    MinimalCallButton(
                        icon = Icons.Default.Pause,
                        contentDescription = stringResource(R.string.action_hold),
                        isActive = isHold,
                        onClick = { isHold = !isHold }
                    )
                    MinimalCallButton(
                        icon = Icons.Default.Videocam,
                        contentDescription = stringResource(R.string.action_video),
                        isActive = false,
                        onClick = {}
                    )
                }
            }

            // End Call Action (Clean Red Circular Button)
            Box(
                modifier = Modifier
                    .padding(bottom = 36.dp)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEA4335))
                    .clickable { onEndCall() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = stringResource(R.string.action_end_call),
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

@Composable
fun MinimalCallButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(if (isActive) Color.White else Color(0xFF282D36))
            .clickable { onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) Color(0xFF14171D) else Color(0xFFE2E2E6),
            modifier = Modifier.size(28.dp)
        )
    }
}
