package com.kphone.callassistant

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class IncomingCallActivity : ComponentActivity() {

    private var callerName: String = "Sếp Tổng"
    private var callerNumber: String = "+84 988 888 888"
    private var ringtoneUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        turnScreenOnAndKeyguard()
        super.onCreate(savedInstanceState)

        callerName = intent.getStringExtra(CallAlarmReceiver.EXTRA_CALLER_NAME) ?: "Sếp Tổng"
        callerNumber = intent.getStringExtra(CallAlarmReceiver.EXTRA_CALLER_NUMBER) ?: "+84 988 888 888"
        ringtoneUri = intent.getStringExtra(CallAlarmReceiver.EXTRA_RINGTONE_URI)

        SoundVibrationManager.acquireScreenWakeLock(this)
        SoundVibrationManager.startRinging(this, ringtoneUri)

        setContent {
            MaterialTheme {
                Android16IncomingCallScreen(
                    callerName = callerName,
                    callerNumber = callerNumber,
                    onAccept = {
                        acceptCall()
                    },
                    onDecline = {
                        declineCall()
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        turnScreenOnAndKeyguard()
        SoundVibrationManager.acquireScreenWakeLock(this)
    }

    private fun acceptCall() {
        SoundVibrationManager.stopRinging()
        dismissNotification()
        CallSchedulerService.stop(this)

        val intent = Intent(this, InCallActivity::class.java).apply {
            putExtra(CallAlarmReceiver.EXTRA_CALLER_NAME, callerName)
            putExtra(CallAlarmReceiver.EXTRA_CALLER_NUMBER, callerNumber)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun declineCall() {
        SoundVibrationManager.stopRinging()
        dismissNotification()
        CallSchedulerService.stop(this)
        finish()
    }

    private fun dismissNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(CallAlarmReceiver.NOTIFICATION_ID)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            SoundVibrationManager.stopRinging()
        }
    }
}

@Composable
fun Android16IncomingCallScreen(
    callerName: String,
    callerNumber: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Pulsing subtle animation for avatar
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Vertical swipe animation state for Answer slider (Swipe Up to Answer)
    val swipeOffset = remember { Animatable(0f) }
    val maxSwipeDistancePx = with(density) { 150.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1B1F27),
                        Color(0xFF13151A),
                        Color(0xFF0C0E12)
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
            // Top Section: Clean Caller Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Text(
                    text = stringResource(R.string.incoming_call),
                    color = Color(0xFF9AA0A6),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Avatar container
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(120.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(Color(0xFF2B303A))
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
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.mobile_label),
                    color = Color(0xFF80868B),
                    fontSize = 14.sp
                )
            }

            // Quick reply minimal pill button
            IconButton(
                onClick = onDecline,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x1FFFFFFF))
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Message,
                    contentDescription = stringResource(R.string.action_quick_reply),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Bottom Section: Android 16 Google Phone Style Dual Floating Actions (Decline vs Swipe-to-answer)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 36.dp, start = 20.dp, end = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Decline Button (Red Circle, no label, clean Material 3)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEA4335))
                            .clickable { onDecline() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = stringResource(R.string.action_decline),
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Answer Button Container: Vertical Swipe Up Track (Android 16 Style)
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .height(180.dp)
                            .clip(RoundedCornerShape(36.dp))
                            .background(Color(0x2234A853)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // Draggable Green Call Button (Swipe Up)
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(0, swipeOffset.value.roundToInt()) }
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF34A853))
                                .draggable(
                                    orientation = Orientation.Vertical,
                                    state = rememberDraggableState { delta ->
                                        // delta is negative when dragging up
                                        val newValue = (swipeOffset.value + delta).coerceIn(-maxSwipeDistancePx, 0f)
                                        coroutineScope.launch {
                                            swipeOffset.snapTo(newValue)
                                        }
                                    },
                                    onDragStopped = {
                                        if (swipeOffset.value <= -maxSwipeDistancePx * 0.6f) {
                                            onAccept()
                                        } else {
                                            coroutineScope.launch {
                                                swipeOffset.animateTo(0f, tween(200))
                                            }
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = stringResource(R.string.action_answer),
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
