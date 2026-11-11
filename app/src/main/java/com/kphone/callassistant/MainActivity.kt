package com.kphone.callassistant

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KPhoneAppTheme {
                MainSettingsScreen()
            }
        }
    }
}

@Composable
fun KPhoneAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFA8C7FA),
            onPrimary = Color(0xFF003063),
            surface = Color(0xFF1E2229),
            background = Color(0xFF121418),
            onSurface = Color(0xFFE2E2E6),
            onBackground = Color(0xFFE2E2E6)
        ),
        content = content
    )
}

private fun checkFullScreenIntentGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 34) {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.canUseFullScreenIntent()
        } catch (e: Exception) {
            true
        }
    }
    return true
}

private fun checkOverlayGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var callerName by remember { mutableStateOf("Sếp Tổng") }
    var callerNumber by remember { mutableStateOf("0988 888 888") }
    var selectedDelaySeconds by remember { mutableIntStateOf(10) }
    var customRingtoneUri by remember { mutableStateOf<Uri?>(null) }
    var ringtoneTitle by remember { mutableStateOf<String?>(null) }

    val remainingSeconds by CallSchedulerService.remainingSeconds.collectAsState()

    var canUseFullScreen by remember { mutableStateOf(checkFullScreenIntentGranted(context)) }
    var canDrawOverlay by remember { mutableStateOf(checkOverlayGranted(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canUseFullScreen = checkFullScreenIntentGranted(context)
                canDrawOverlay = checkOverlayGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val defaultRingtoneText = stringResource(R.string.default_ringtone)
    val displayRingtoneTitle = ringtoneTitle ?: defaultRingtoneText

    // Launcher for file picker
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            customRingtoneUri = it

            var name = "Audio"
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
            ringtoneTitle = name
        }
    }

    // Permission launcher for Android 13+ Notifications
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, context.getString(R.string.permission_notification_toast), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF34A853))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1D23),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121418)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Optional Helper Card if FullScreenIntent or Overlay is not granted yet
            if (!canUseFullScreen || !canDrawOverlay) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2B231D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFFFFB74D),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.lockscreen_permission_title),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB74D)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.lockscreen_permission_desc),
                            fontSize = 13.sp,
                            color = Color(0xFFE2E2E6)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!canUseFullScreen && Build.VERSION.SDK_INT >= 34) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val fallback = Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            context.startActivity(fallback)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.btn_grant_fullscreen),
                                        color = Color(0xFF1E1A16),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (!canDrawOverlay) {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val fallback = Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            context.startActivity(fallback)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.btn_grant_overlay),
                                        color = Color(0xFFFFB74D),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Card 1: Caller Information
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2229)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.caller_info_section),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA8C7FA)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = callerName,
                        onValueChange = { callerName = it },
                        label = { Text(stringResource(R.string.caller_name_label)) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFFBDC1C6))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = callerNumber,
                        onValueChange = { callerNumber = it },
                        label = { Text(stringResource(R.string.caller_number_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        leadingIcon = {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFFBDC1C6))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 2: Timer Presets
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2229)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.timer_section),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA8C7FA)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    val delayOptions = listOf(
                        10 to stringResource(R.string.time_10s),
                        30 to stringResource(R.string.time_30s),
                        60 to stringResource(R.string.time_60s),
                        120 to stringResource(R.string.time_2m),
                        300 to stringResource(R.string.time_5m)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        delayOptions.take(3).forEach { (seconds, label) ->
                            FilterChip(
                                selected = selectedDelaySeconds == seconds,
                                onClick = { selectedDelaySeconds = seconds },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        delayOptions.drop(3).forEach { (seconds, label) ->
                            FilterChip(
                                selected = selectedDelaySeconds == seconds,
                                onClick = { selectedDelaySeconds = seconds },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 3: Ringtone Selection
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2229)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.ringtone_section),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA8C7FA)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF282D36))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFFA8C7FA),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = displayRingtoneTitle,
                            fontSize = 14.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                customRingtoneUri = null
                                ringtoneTitle = null
                                Toast.makeText(context, defaultRingtoneText, Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.btn_use_default_ringtone), fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                audioPickerLauncher.launch(arrayOf("audio/*"))
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.btn_pick_file), fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Action 1: Instant Call
            OutlinedButton(
                onClick = {
                    val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
                        putExtra(CallAlarmReceiver.EXTRA_CALLER_NAME, callerName)
                        putExtra(CallAlarmReceiver.EXTRA_CALLER_NUMBER, callerNumber)
                        putExtra(CallAlarmReceiver.EXTRA_RINGTONE_URI, customRingtoneUri?.toString())
                    }
                    context.startActivity(fullScreenIntent)
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.PhoneCallback, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_start_now), fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selected delay string
            val selectedDelayLabel = when (selectedDelaySeconds) {
                10 -> stringResource(R.string.time_10s)
                30 -> stringResource(R.string.time_30s)
                60 -> stringResource(R.string.time_60s)
                120 -> stringResource(R.string.time_2m)
                else -> stringResource(R.string.time_5m)
            }

            val isCountingDown = remainingSeconds > 0

            // Action 2: Schedule Call (or Cancel if currently counting down)
            Button(
                onClick = {
                    if (isCountingDown) {
                        CallSchedulerService.stop(context)
                        CallAlarmReceiver.cancelScheduledCall(context)
                        Toast.makeText(
                            context,
                            context.getString(R.string.schedule_cancelled_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }

                    try {
                        // 1. Start Foreground Service with CPU WakeLock
                        CallSchedulerService.start(
                            context = context,
                            seconds = selectedDelaySeconds,
                            callerName = callerName,
                            callerNumber = callerNumber,
                            ringtoneUri = customRingtoneUri?.toString()
                        )

                        // 2. Also register AlarmManager.setAlarmClock (wakes CPU from deep sleep + grants BAL exemption)
                        CallAlarmReceiver.scheduleCall(
                            context = context,
                            secondsInFuture = selectedDelaySeconds,
                            callerName = callerName,
                            callerNumber = callerNumber,
                            ringtoneUri = customRingtoneUri?.toString()
                        )

                        Toast.makeText(
                            context,
                            context.getString(R.string.schedule_success_toast, selectedDelayLabel),
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(
                            context,
                            context.getString(R.string.error_scheduling, e.localizedMessage ?: ""),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCountingDown) Color(0xFFE65100) else Color(0xFF34A853)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Icon(
                    imageVector = if (isCountingDown) Icons.Default.Timer else Icons.Default.Alarm,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCountingDown) {
                        stringResource(R.string.btn_counting_down_format, remainingSeconds)
                    } else {
                        stringResource(R.string.btn_schedule_format, selectedDelayLabel)
                    },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
