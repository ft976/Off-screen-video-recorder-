package com.example.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.camera.core.CameraSelector
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.RecordingLog
import com.example.viewmodel.RecordingViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Style Accent Constants matching the "Clean Utility / Minimal" design theme
val CleanBackground = Color(0xFFFDFBFF)
val PrimaryIndigo = Color(0xFF4F52B2)
val SoftIndigoBG = Color(0xFFE1E0F9)
val SoftNeutralBG = Color(0xFFF2F0F4)
val CoreDarkText = Color(0xFF1B1B1F)
val BorderColor = Color(0xFFCAC4D0)
val MutedGreyText = Color(0xFF44464F)
val LightGreyText = Color(0xFF757780)
val AlertRed = Color(0xFFBA1A1A)

// Creative Palette additions for visual interest and Dev Profile
val BrightTeal = Color(0xFF0D9488)
val DarkNavyAccent = Color(0xFF1E1B4B)
val RichGold = Color(0xFFD97706)
val SoftGoldBG = Color(0xFFFEF3C7)
val GlowCrimson = Color(0xFFE11D48)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AppNavigationScaffold(viewModel: RecordingViewModel) {
    val permissionList = listOfNotNull(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.POST_NOTIFICATIONS
        } else null
    )
    val permissionsState = rememberMultiplePermissionsState(permissions = permissionList)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CleanBackground
    ) {
        MainRecordingFlow(viewModel = viewModel, permissionsState = permissionsState)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionWizard(permissionsState: MultiplePermissionsState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CleanBackground)
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = "Shield Icon",
            tint = PrimaryIndigo,
            modifier = Modifier
                .size(72.dp)
                .padding(bottom = 16.dp)
        )
        
        Text(
            text = "Hardware Authorization",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = CoreDarkText,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "OffScreen requires standard Android permissions to safely record video clips in the background operations.",
            fontSize = 14.sp,
            color = MutedGreyText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Dynamic status cards
        PermissionStatusRow(
            title = "Access camera hardware",
            description = "Allows background lens access when screen is black or off.",
            isGranted = permissionsState.permissions.any { it.permission == android.Manifest.permission.CAMERA && it.status is com.google.accompanist.permissions.PermissionStatus.Granted }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        PermissionStatusRow(
            title = "Capture audio streams",
            description = "Saves synchronized video microphone audio when toggled.",
            isGranted = permissionsState.permissions.any { it.permission == android.Manifest.permission.RECORD_AUDIO && it.status is com.google.accompanist.permissions.PermissionStatus.Granted }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionStatusRow(
                title = "Push background logs",
                description = "Shows required non-dismissible recording stopwatch notifications.",
                isGranted = permissionsState.permissions.any { it.permission == android.Manifest.permission.POST_NOTIFICATIONS && it.status is com.google.accompanist.permissions.PermissionStatus.Granted }
            )
            Spacer(modifier = Modifier.height(32.dp))
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = { permissionsState.launchMultiplePermissionRequest() },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.LockOpen, contentDescription = "Authorize Lock", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Authorize Capabilities", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
fun PermissionStatusRow(title: String, description: String, isGranted: Boolean) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Circle,
                contentDescription = if (isGranted) "Granted" else "Pending",
                tint = if (isGranted) PrimaryIndigo else LightGreyText,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1.0f)) {
                Text(title, color = CoreDarkText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(description, color = MutedGreyText, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainRecordingFlow(viewModel: RecordingViewModel, permissionsState: MultiplePermissionsState) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        modifier = Modifier.navigationBarsPadding(),
        bottomBar = {
            Column {
                HorizontalDivider(color = BorderColor, thickness = 1.dp)
                NavigationBar(
                    containerColor = SoftNeutralBG,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { 
                            Icon(
                                imageVector = if (selectedTab == 0) Icons.Default.Videocam else Icons.Outlined.Videocam, 
                                contentDescription = "Filmer Board"
                            ) 
                        },
                        label = { Text("Rec", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CoreDarkText,
                            selectedTextColor = CoreDarkText,
                            indicatorColor = SoftIndigoBG,
                            unselectedIconColor = MutedGreyText,
                            unselectedTextColor = MutedGreyText
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { 
                            Icon(
                                imageVector = if (selectedTab == 1) Icons.Default.VideoLibrary else Icons.Outlined.VideoLibrary, 
                                contentDescription = "Videos Shelf"
                            ) 
                        },
                        label = { Text("Library", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CoreDarkText,
                            selectedTextColor = CoreDarkText,
                            indicatorColor = SoftIndigoBG,
                            unselectedIconColor = MutedGreyText,
                            unselectedTextColor = MutedGreyText
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { 
                            Icon(
                                imageVector = if (selectedTab == 2) Icons.Default.Info else Icons.Outlined.Info, 
                                contentDescription = "Status Panel"
                            ) 
                        },
                        label = { Text("Status", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CoreDarkText,
                            selectedTextColor = CoreDarkText,
                            indicatorColor = SoftIndigoBG,
                            unselectedIconColor = MutedGreyText,
                            unselectedTextColor = MutedGreyText
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .background(CleanBackground)
            .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> RecordHubScreen(viewModel, permissionsState, onSettingsClick = { selectedTab = 2 })
                1 -> ClipsLibraryScreen(viewModel)
                2 -> StatusInfoScreen(viewModel)
            }
        }
    }
}

// Dynamic Battery Meter & Status Pill Components
@Composable
fun BatteryPill(level: Int, isCharging: Boolean) {
    val containerColor = when {
        isCharging -> Color(0xFFE8F5E9)       // Soft battery-saving green
        level <= 15 -> Color(0xFFFFEBEE)     // Soft crimson panic
        level <= 30 -> Color(0xFFFFF3E0)     // Soft amber alert
        else -> SoftNeutralBG                // Clean neutral slate
    }
    val contentColor = when {
        isCharging -> Color(0xFF2E7D32)
        level <= 15 -> Color(0xFFC62828)
        level <= 30 -> Color(0xFFEF6C00)
        else -> MutedGreyText
    }
    val icon = when {
        isCharging -> Icons.Default.Bolt
        level <= 15 -> Icons.Default.BatteryAlert
        else -> Icons.Default.BatteryFull
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.15f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Battery Metric",
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "$level%",
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BatteryLowCriticalAlert(level: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFFFCDD2))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFFFCDD2), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning Level",
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CRITICAL BATTERY THRESHOLD ($level%)",
                    color = Color(0xFFC62828),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Battery is running dangerously low. To extend operations, we highly recommend setting the Recording Frame Rate to '15 FPS' to halve CPU core overhead.",
                    color = Color(0xFFD32F2F),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RecordHubScreen(
    viewModel: RecordingViewModel, 
    permissionsState: MultiplePermissionsState, 
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isRecording by viewModel.isRecordingRunning.collectAsState()
    val activeDurationMs by viewModel.activeDurationMs.collectAsState()
    val cameraLens by viewModel.cameraLens.collectAsState()
    val videoQuality by viewModel.videoQuality.collectAsState()
    val frameRate by viewModel.frameRate.collectAsState()
    
    val audioEnabled by viewModel.audioEnabled.collectAsState()
    val silentMode by viewModel.silentMode.collectAsState()
    val hideAppOnStart by viewModel.hideAppOnStart.collectAsState()
    val batteryShutdown by viewModel.batteryShutdown.collectAsState()
    val logs by viewModel.recordingLogs.collectAsState()

    // Real-time battery flows
    val batteryLevel by viewModel.batteryLevel.collectAsState()
    val isBatteryCharging by viewModel.isBatteryCharging.collectAsState()

    var showPermissionPrompt by remember { mutableStateOf(false) }

    // Smooth pulsing circular animations
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isRecording) 1.06f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsescale"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App Identity Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "OffScreen Rec",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CoreDarkText
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BatteryPill(level = batteryLevel, isCharging = isBatteryCharging)

                    // Settings icon redirects to the rich status info page!
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SoftIndigoBG)
                            .clickable { onSettingsClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Status Details",
                            tint = CoreDarkText,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Real-time Critical Battery Warning Alert Banner
        if (batteryLevel <= 15 && !isBatteryCharging) {
            item {
                BatteryLowCriticalAlert(level = batteryLevel)
            }
        }

        // Circular Pulse Recorder Dashboard
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SoftIndigoBG),
                shape = RoundedCornerShape(32.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(192.dp)
                            .scale(pulseScale)
                            .background(Color.White.copy(alpha = 0.4f), shape = CircleShape)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .shadow(if (isRecording) 10.dp else 2.dp, shape = CircleShape, ambientColor = PrimaryIndigo, spotColor = PrimaryIndigo)
                                .background(Color.White, shape = CircleShape)
                                .clickable {
                                    if (isRecording) {
                                        viewModel.stopBackgroundRecording()
                                    } else {
                                        if (permissionsState.allPermissionsGranted) {
                                            viewModel.startBackgroundRecording()
                                            if (hideAppOnStart) {
                                                Toast.makeText(activity, "OffScreen background filming start. Window minimized.", Toast.LENGTH_LONG).show()
                                                activity?.finishAndRemoveTask()
                                            }
                                        } else {
                                            showPermissionPrompt = true
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(AlertRed, shape = CircleShape)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                val sec = activeDurationMs / 1000
                                val min = sec / 60
                                val timerText = String.format("%02d:%02d", min, sec % 60)
                                
                                Text(
                                    text = if (isRecording) timerText else "READY",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CoreDarkText,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isRecording) "TAP TO STOP" else "TAP TO START",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryIndigo
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = if (isRecording) "Currently Recording" else "System Active",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MutedGreyText
                    )
                    Text(
                        text = if (isRecording) "VID_${System.currentTimeMillis() / 1000}.mp4" else "Background Camera Ready",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = LightGreyText
                    )
                }
            }
        }

        // Lens Selection Card
        item {
            val enabled = !isRecording
            val alphaMod = if (isRecording) 0.6f else 1.0f
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(alphaMod),
                colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "CAMERA LENS", 
                            color = PrimaryIndigo, 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                        if (isRecording) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = AlertRed,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("LOCKED", color = AlertRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (enabled) {
                                    viewModel.setCameraLens(CameraSelector.LENS_FACING_BACK)
                                } else {
                                    Toast.makeText(context, "Cannot change camera lens during active recording", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (cameraLens == CameraSelector.LENS_FACING_BACK) PrimaryIndigo else SoftNeutralBG,
                                contentColor = if (cameraLens == CameraSelector.LENS_FACING_BACK) Color.White else CoreDarkText
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.0f),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Camera, 
                                contentDescription = "Rear Camera", 
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rear Lens", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = {
                                if (enabled) {
                                    viewModel.setCameraLens(CameraSelector.LENS_FACING_FRONT)
                                } else {
                                    Toast.makeText(context, "Cannot change camera lens during active recording", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (cameraLens == CameraSelector.LENS_FACING_FRONT) PrimaryIndigo else SoftNeutralBG,
                                contentColor = if (cameraLens == CameraSelector.LENS_FACING_FRONT) Color.White else CoreDarkText
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.0f),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Portrait, 
                                contentDescription = "Front Camera", 
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Front Lens", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // Quality presets
        item {
            val enabled = !isRecording
            val alphaMod = if (isRecording) 0.6f else 1.0f
            val qualityPresets = remember(cameraLens) { viewModel.getSupportedResolutions() }
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(alphaMod),
                colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "FILMING QUALITY PRESET", 
                            color = PrimaryIndigo, 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                        if (isRecording) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = AlertRed,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("LOCKED", color = AlertRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        qualityPresets.forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (videoQuality == preset) PrimaryIndigo else SoftNeutralBG)
                                    .clickable {
                                        if (enabled) {
                                            viewModel.setVideoQuality(preset)
                                        } else {
                                            Toast.makeText(context, "Cannot change video quality during active recording", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    preset,
                                    color = if (videoQuality == preset) Color.White else CoreDarkText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Frame Rate Selection Card to Conserve Battery
        item {
            val enabled = !isRecording
            val alphaMod = if (isRecording) 0.6f else 1.0f
            val fpsPresets = remember(cameraLens) { viewModel.getSupportedFrameRates() }
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(alphaMod),
                colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "RECORDING FRAME RATE", 
                            color = PrimaryIndigo, 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                        if (isRecording) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = AlertRed,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("LOCKED", color = AlertRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SoftIndigoBG)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "Battery Saver", 
                                    color = PrimaryIndigo, 
                                    fontSize = 9.sp, 
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Lower frame rates (like 15 FPS) significantly reduce device CPU cycles and conserve battery during long background filming sessions.",
                        fontSize = 11.sp,
                        color = MutedGreyText
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        fpsPresets.forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (frameRate == preset) PrimaryIndigo else SoftNeutralBG)
                                    .clickable {
                                        if (enabled) {
                                            viewModel.setFrameRate(preset)
                                        } else {
                                            Toast.makeText(context, "Cannot change frame rate during active recording", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    preset,
                                    color = if (frameRate == preset) Color.White else CoreDarkText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Advanced Tuning switch list
        item {
            val enabled = !isRecording
            val alphaMod = if (isRecording) 0.6f else 1.0f
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(alphaMod),
                colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "BACKGROUND RECORDING TUNING", 
                            color = PrimaryIndigo, 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                        if (isRecording) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = AlertRed,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("LOCKED", color = AlertRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    SettingsSwitchRow(
                        title = "Enable Audio Recording",
                        subtitle = "Include sound flow via microphone stream.",
                        checked = audioEnabled,
                        onCheckedChange = { 
                            if (enabled) viewModel.setAudioEnabled(it) 
                            else Toast.makeText(context, "Cannot change tuning during active recording", Toast.LENGTH_SHORT).show() 
                        }
                    )
                    
                    HorizontalDivider(color = SoftNeutralBG, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    SettingsSwitchRow(
                        title = "Silent Operation",
                        subtitle = "Mute shutter sound prompts & Toast indicators.",
                        checked = silentMode,
                        onCheckedChange = { 
                            if (enabled) viewModel.setSilentMode(it) 
                            else Toast.makeText(context, "Cannot change tuning during active recording", Toast.LENGTH_SHORT).show() 
                        }
                    )

                    HorizontalDivider(color = SoftNeutralBG, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    SettingsSwitchRow(
                        title = "Hide App on Filming Start",
                        subtitle = "Close activity UI instantly when backgrounding lens.",
                        checked = hideAppOnStart,
                        onCheckedChange = { 
                            if (enabled) viewModel.setHideAppOnStart(it) 
                            else Toast.makeText(context, "Cannot change tuning during active recording", Toast.LENGTH_SHORT).show() 
                        }
                    )

                    HorizontalDivider(color = SoftNeutralBG, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    SettingsSwitchRow(
                        title = "Smart Low-Battery Stop",
                        subtitle = "Auto-save clip when charge level drops under 5%.",
                        checked = batteryShutdown,
                        onCheckedChange = { 
                            if (enabled) viewModel.setBatteryShutdown(it) 
                            else Toast.makeText(context, "Cannot change tuning during active recording", Toast.LENGTH_SHORT).show() 
                        }
                    )
                }
            }
        }

        item {
            val enabled = !isRecording
            val alphaMod = if (isRecording) 0.6f else 1.0f
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(alphaMod),
                colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "DISCREET MODE",
                        color = PrimaryIndigo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    val hideNotification by viewModel.hideNotification.collectAsState()
                    SettingsSwitchRow(
                        title = "Hide Status Notification",
                        subtitle = "Attempt to hide ongoing record notifications. (Some devices may still show a silent icon)",
                        checked = hideNotification,
                        onCheckedChange = { 
                            if (enabled) viewModel.setHideNotification(it) 
                            else Toast.makeText(context, "Cannot change during active recording", Toast.LENGTH_SHORT).show() 
                        }
                    )
                }
            }
        }

        // Available Storage progress bar
        item {
            var storageState by remember { mutableStateOf(getStorageInfo(context)) }

            LaunchedEffect(logs.size, isRecording) {
                storageState = getStorageInfo(context)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SoftNeutralBG)
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AVAILABLE STORAGE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MutedGreyText,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "${storageState.freeGb} GB FREE / ${storageState.totalGb} GB TOTAL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryIndigo
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    val percentUsed = (1f - (storageState.freeGb / storageState.totalGb)).coerceIn(0.01f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(Color.White, shape = CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = percentUsed)
                                .background(PrimaryIndigo, shape = CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Real-time Disk Health: StatFs verified",
                        fontSize = 9.sp,
                        color = LightGreyText
                    )
                }
            }
        }

        // Action controllers
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (isRecording) {
                            viewModel.stopBackgroundRecording()
                        } else {
                            if (permissionsState.allPermissionsGranted) {
                                viewModel.startBackgroundRecording()
                                if (hideAppOnStart) {
                                    Toast.makeText(activity, "OffScreen background capturing active. Activity closed.", Toast.LENGTH_LONG).show()
                                    activity?.finishAndRemoveTask()
                                }
                            } else {
                                showPermissionPrompt = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) AlertRed else PrimaryIndigo,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .weight(1.0f)
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Videocam,
                        contentDescription = "Trigger Status"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRecording) "STOP FILMING" else "START BACKGROUND CAPTURE",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showPermissionPrompt) {
        PermissionPromptDialog(
            permissionsState = permissionsState,
            onDismiss = { showPermissionPrompt = false }
        )
    }
}

@Composable
fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.0f)) {
            Text(title, color = CoreDarkText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MutedGreyText, fontSize = 10.sp, lineHeight = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryIndigo,
                uncheckedThumbColor = LightGreyText,
                uncheckedTrackColor = SoftNeutralBG
            )
        )
    }
}

data class StorageInfo(val freeGb: Float, val totalGb: Float)

fun getStorageInfo(context: android.content.Context): StorageInfo {
    return try {
        val fileDir = context.getExternalFilesDir(null) ?: context.filesDir
        val stat = android.os.StatFs(fileDir.absolutePath)
        val blockSize = stat.blockSizeLong
        val availableBlocks = stat.availableBlocksLong
        val totalBlocks = stat.blockCountLong
        val freeBytes = availableBlocks * blockSize
        val totalBytes = totalBlocks * blockSize
        val freeGb = (freeBytes / (1024.0 * 1024.0 * 1024.0)).toFloat()
        val totalGb = (totalBytes / (1024.0 * 1024.0 * 1024.0)).toFloat()
        StorageInfo(
            freeGb = String.format(Locale.US, "%.1f", freeGb).toFloat(),
            totalGb = String.format(Locale.US, "%.1f", totalGb).toFloat()
        )
    } catch (e: Exception) {
        try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val freeGb = (stat.availableBytes / (1024.0 * 1024.0 * 1024.0)).toFloat()
            val totalGb = (stat.totalBytes / (1024.0 * 1024.0 * 1024.0)).toFloat()
            StorageInfo(
                freeGb = String.format(Locale.US, "%.1f", freeGb).toFloat(),
                totalGb = String.format(Locale.US, "%.1f", totalGb).toFloat()
            )
        } catch (ex: Exception) {
            StorageInfo(12.4f, 64.0f)
        }
    }
}

fun formatTotalLogsBytes(context: android.content.Context, logs: List<com.example.data.RecordingLog>): String {
    var sum = 0L
    for (log in logs) {
        if (log.filePath.startsWith("content://")) {
            try {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(log.filePath), "r")?.use { afd ->
                    sum += afd.length
                }
            } catch (e: Exception) {
                // ignore
            }
        } else {
            val file = File(log.filePath)
            if (file.exists()) {
                sum += file.length()
            }
        }
    }
    if (sum < 1024) return "$sum B"
    val kb = sum / 1024f
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024f
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024f
    return String.format(Locale.US, "%.1f GB", gb)
}

// FORMAT FILE SIZE HELPERS
fun formatFileSize(context: android.content.Context, filePath: String): String {
    if (filePath.startsWith("content://")) {
        try {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(filePath), "r")?.use { afd ->
                val bytes = afd.length
                if (bytes < 0) return "Unknown"
                if (bytes < 1024) return "$bytes B"
                val kb = bytes / 1024f
                if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
                val mb = kb / 1024f
                return String.format(Locale.US, "%.1f MB", mb)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    } else {
        val file = File(filePath)
        if (!file.exists()) return "0 KB"
        val bytes = file.length()
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024f
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024f
        return String.format(Locale.US, "%.1f MB", mb)
    }
    return "Unknown"
}

fun playVideoExternally(context: android.content.Context, filePath: String) {
    try {
        val uri = Uri.parse(filePath)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No default video player: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

// VIDEO THUMBNAIL GENERATOR (ASYNC BACKGROUND EXTRACTOR USING MEDIAMETADATARETRIEVER)
@Composable
fun VideoThumbnailLocal(filePath: String, isWide: Boolean = false, modifier: Modifier = Modifier) {
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var triedExtraction by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(filePath) {
        if (!triedExtraction) {
            triedExtraction = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var retriever: MediaMetadataRetriever? = null
                try {
                    retriever = MediaMetadataRetriever()
                    if (filePath.startsWith("content://")) {
                        retriever.setDataSource(context, Uri.parse(filePath))
                    } else {
                        val file = File(filePath)
                        if (file.exists()) {
                            retriever.setDataSource(file.absolutePath)
                        } else {
                            retriever.setDataSource(filePath)
                        }
                    }
                    val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (bitmap != null) {
                        val width = if (isWide) 480 else 160
                        val height = if (isWide) 270 else 160
                        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
                        thumbnail = scaled
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        retriever?.release()
                    } catch (ex: Exception) {}
                }
            }
        }
    }

    Box(
        modifier = modifier
            .background(SoftIndigoBG)
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = "Video Thumbnail Preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (!isWide) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MovieFilter,
                    contentDescription = "Loading preview...",
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(if (isWide) 48.dp else 28.dp)
                )
                if (isWide) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Loading visual preview...", color = LightGreyText, fontSize = 11.sp)
                }
            }
        }
    }
}

// NATIVE VIDEO PREVIEW DIALOG
@Composable
fun VideoPreviewDialog(filePath: String, fileName: String, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Video Clip Player",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CoreDarkText
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close preview", tint = CoreDarkText)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Text(
                    text = fileName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryIndigo,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val mediaController = MediaController(ctx)
                                mediaController.setAnchorView(this)
                                setMediaController(mediaController)
                                setOnPreparedListener { mediaPlayer ->
                                    mediaPlayer.isLooping = true
                                    start()
                                }
                                setOnErrorListener { _, _, _ ->
                                    Toast.makeText(ctx, "Format decoder exception, trying fallbacks...", Toast.LENGTH_SHORT).show()
                                    false
                                }
                            }
                        },
                        update = { videoView ->
                            try {
                                if (filePath.startsWith("content://")) {
                                    videoView.setVideoURI(Uri.parse(filePath))
                                } else {
                                    videoView.setVideoPath(filePath)
                                }
                                videoView.start()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                Button(
                    onClick = { playVideoExternally(context, filePath) },
                    colors = ButtonDefaults.buttonColors(containerColor = SoftIndigoBG, contentColor = CoreDarkText),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open externally", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play via System default player", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Path: $filePath",
                    fontSize = 9.sp,
                    color = LightGreyText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Dismiss player", color = PrimaryIndigo, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color.White
    )
}

@Composable
fun ClipsLibraryScreen(viewModel: RecordingViewModel) {
    val context = LocalContext.current
    val logs by viewModel.recordingLogs.collectAsState()
    
    var editingLogId by remember { mutableStateOf<Long?>(null) }
    var currentEditNotes by remember { mutableStateOf("") }

    var previewVideoPath by remember { mutableStateOf<String?>(null) }
    var previewVideoName by remember { mutableStateOf<String?>(null) }

    // Double confirmation deletion states
    var pendingDeleteLog by remember { mutableStateOf<com.example.data.RecordingLog?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    var showClearAllConfirmDialog by remember { mutableStateOf(false) }

    // Advanced search, sorting and filter states
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterCategory by remember { mutableStateOf("All") }
    var selectedSortOrder by remember { mutableStateOf("Newest") }

    if (previewVideoPath != null && previewVideoName != null) {
        VideoPreviewDialog(
            filePath = previewVideoPath!!,
            fileName = previewVideoName!!,
            onDismissRequest = {
                previewVideoPath = null
                previewVideoName = null
            }
        )
    }

    if (editingLogId != null) {
        AlertDialog(
            onDismissRequest = { editingLogId = null },
            title = { Text("Update Comments", color = CoreDarkText, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                OutlinedTextField(
                    value = currentEditNotes,
                    onValueChange = { currentEditNotes = it },
                    label = { Text("Notes & Metadata") },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = CoreDarkText,
                        unfocusedTextColor = CoreDarkText,
                        focusedLabelColor = PrimaryIndigo,
                        unfocusedContainerColor = SoftNeutralBG,
                        focusedContainerColor = SoftNeutralBG
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editingLogId?.let { id ->
                            viewModel.updateNotes(id, currentEditNotes)
                        }
                        editingLogId = null
                    }
                ) {
                    Text("Save Changes", color = PrimaryIndigo, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingLogId = null }) {
                    Text("Cancel", color = MutedGreyText)
                }
            },
            containerColor = Color.White
        )
    }

    // --- SINGLE VIDEO LOG DELETION CONFIRMATION ---
    if (showDeleteConfirmDialog && pendingDeleteLog != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteConfirmDialog = false 
                pendingDeleteLog = null
            },
            title = { 
                Text("Delete Video", color = CoreDarkText, fontWeight = FontWeight.Bold, fontSize = 16.sp) 
            },
            text = { 
                Text("Are you sure you want to permanently delete \"${pendingDeleteLog?.fileName}\"? This action cannot be undone.", fontSize = 14.sp) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteLog?.let { log ->
                            viewModel.deleteLog(log.id)
                            Toast.makeText(context, "Video deleted.", Toast.LENGTH_SHORT).show()
                        }
                        showDeleteConfirmDialog = false
                        pendingDeleteLog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showDeleteConfirmDialog = false 
                        pendingDeleteLog = null
                    }
                ) {
                    Text("Cancel", color = CoreDarkText)
                }
            },
            containerColor = Color.White
        )
    }

    // --- CLEAR ALL VIDEOS CONFIRMATION ---
    if (showClearAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirmDialog = false },
            title = { 
                Text("Clear All Videos", color = AlertRed, fontWeight = FontWeight.Bold, fontSize = 16.sp) 
            },
            text = { 
                Text("Are you sure you want to permanently delete all recorded video files from this device? This action cannot be undone.", fontSize = 14.sp) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllLogsFromSystem()
                        Toast.makeText(context, "All logs and storage wiped", Toast.LENGTH_SHORT).show()
                        showClearAllConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Clear All", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirmDialog = false }) {
                    Text("Cancel", color = CoreDarkText)
                }
            },
            containerColor = Color.White
        )
    }

    // Applying filter logic
    val filteredLogs = remember(logs, searchQuery, selectedFilterCategory) {
        logs.filter { log ->
            val matchQuery = searchQuery.isEmpty() || 
                    log.fileName.contains(searchQuery, ignoreCase = true) || 
                    log.notes.contains(searchQuery, ignoreCase = true)

            val matchCategory = when (selectedFilterCategory) {
                "With Notes" -> log.notes.isNotEmpty()
                "1080p High-Res" -> log.resolution == "1080p"
                else -> true
            }
            matchQuery && matchCategory
        }
    }

    // Applying sort logic
    val processedLogs = remember(filteredLogs, selectedSortOrder) {
        when (selectedSortOrder) {
            "Oldest" -> filteredLogs.sortedBy { it.timestamp }
            "Longest" -> filteredLogs.sortedByDescending { it.durationMs }
            else -> filteredLogs.sortedByDescending { it.timestamp } // Newest
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CleanBackground)
    ) {
        // Creative Header System
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RECORDED CLIPS VIRTUAL VAULT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryIndigo,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Media Library",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = CoreDarkText
                )
                Text(
                    text = "${filteredLogs.size} visible • ${logs.size} total videos logged",
                    fontSize = 11.sp,
                    color = MutedGreyText
                )
            }
            if (logs.isNotEmpty()) {
                IconButton(
                    onClick = {
                        showClearAllConfirmDialog = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep, 
                        contentDescription = "Clear absolute vault", 
                        tint = AlertRed,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Professional Search Box
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search title or annotations...", fontSize = 13.sp, color = LightGreyText) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = PrimaryIndigo, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear Search", tint = MutedGreyText, modifier = Modifier.size(16.dp))
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedTextColor = CoreDarkText,
                unfocusedTextColor = CoreDarkText,
                focusedLabelColor = PrimaryIndigo,
                unfocusedLabelColor = MutedGreyText,
                focusedIndicatorColor = PrimaryIndigo,
                unfocusedIndicatorColor = BorderColor.copy(alpha = 0.5f)
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Creative Horizontal Filters Row
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val categories = listOf("All", "1080p High-Res", "With Notes")
            items(categories) { category ->
                val isSelected = selectedFilterCategory == category
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(if (isSelected) PrimaryIndigo else SoftNeutralBG)
                        .clickable { selectedFilterCategory = category }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category,
                        color = if (isSelected) Color.White else CoreDarkText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Sort Selector Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SORT BY ORDER:",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MutedGreyText,
                letterSpacing = 1.sp
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val orders = listOf("Newest", "Oldest", "Longest")
                orders.forEach { order ->
                    val isSelected = selectedSortOrder == order
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) SoftIndigoBG else Color.Transparent)
                            .clickable { selectedSortOrder = order }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = order,
                            color = if (isSelected) PrimaryIndigo else LightGreyText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Library Listing rendering
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1.0f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(SoftIndigoBG, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Empty Metadata Database",
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Your Secure Vault is Empty",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = CoreDarkText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Initiate a background recording to populate clips.",
                        fontSize = 12.sp,
                        color = MutedGreyText,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (processedLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1.0f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = "No match results type",
                        tint = LightGreyText,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No results found",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CoreDarkText
                    )
                    Text(
                        text = "Try clearing your query or selected filters.",
                        fontSize = 12.sp,
                        color = MutedGreyText
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            searchQuery = ""
                            selectedFilterCategory = "All"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Reset Filters", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1.0f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(processedLogs, key = { log -> log.id }) { log ->
                    VideoLogItemCard(
                        log = log,
                        onEditNotesClick = {
                            editingLogId = log.id
                            currentEditNotes = log.notes
                        },
                        onDeleteClick = {
                            pendingDeleteLog = log
                            showDeleteConfirmDialog = true
                        },
                        onShareClick = {
                            try {
                                val uri = Uri.parse(log.filePath)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Video Log"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onPlayPreviewClick = {
                            previewVideoPath = log.filePath
                            previewVideoName = log.fileName
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VideoLogItemCard(
    log: RecordingLog,
    onEditNotesClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    onPlayPreviewClick: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            // Premium Widescreen 16:9 Thumbnail at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clickable { onPlayPreviewClick() }
            ) {
                VideoThumbnailLocal(
                    filePath = log.filePath,
                    isWide = true,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Dimming on state changes
                Box(modifier = Modifier.fillMaxSize().background(if (isExpanded) Color.Black.copy(alpha = 0.05f) else Color.Transparent))

                // Resolution Badge
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(log.resolution, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                
                // Duration Badge
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.BottomEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val sec = log.durationMs / 1000
                    val min = sec / 60
                    val durationStr = String.format("%02d:%02d", min, sec % 60)
                    Text(durationStr, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                // Centered Play Button overlay
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .align(Alignment.Center)
                        .background(PrimaryIndigo.copy(alpha = 0.9f), shape = CircleShape)
                        .shadow(4.dp, shape = CircleShape, ambientColor = PrimaryIndigo, spotColor = PrimaryIndigo),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Tap to preview",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp).padding(start = 2.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // Info block
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1.0f)) {
                        Text(
                            text = log.fileName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = CoreDarkText,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val dateText = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(log.timestamp))
                        Text(
                            text = dateText,
                            fontSize = 11.sp,
                            color = LightGreyText
                        )
                    }
                    val byteSizeStr = remember(log.filePath) { formatFileSize(context, log.filePath) }
                    Text(
                        text = byteSizeStr,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = PrimaryIndigo,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                if (log.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SoftNeutralBG)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = log.notes,
                            fontSize = 12.sp,
                            color = MutedGreyText,
                            maxLines = 2
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Inline primary + fallback controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPlayPreviewClick,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.0f)
                            .height(38.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play Clip", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { playVideoExternally(context, log.filePath) },
                        colors = ButtonDefaults.buttonColors(containerColor = SoftIndigoBG, contentColor = CoreDarkText),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(38.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Open natively", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sys Player", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onEditNotesClick,
                        colors = ButtonDefaults.buttonColors(containerColor = SoftNeutralBG, contentColor = CoreDarkText),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .height(38.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = "Edit Notes", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Notes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onDeleteClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFCE8E6), contentColor = AlertRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .height(38.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Removed Markers Parse

                if (isExpanded) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = SoftNeutralBG, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("File Path location:", fontSize = 10.sp, color = LightGreyText)
                        IconButton(onClick = onShareClick, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = PrimaryIndigo, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(log.filePath, fontSize = 9.sp, color = LightGreyText, maxLines = 2)
                }
            }
        }
    }
}

// STATUS & ABOUT IN DEPTH SCREENS WITH DETAILS
@Composable
fun StatusInfoScreen(viewModel: RecordingViewModel) {
    val context = LocalContext.current
    val isRecording by viewModel.isRecordingRunning.collectAsState()
    val activeDurationMs by viewModel.activeDurationMs.collectAsState()
    val cameraLens by viewModel.cameraLens.collectAsState()
    val videoQuality by viewModel.videoQuality.collectAsState()
    val audioEnabled by viewModel.audioEnabled.collectAsState()
    val silentMode by viewModel.silentMode.collectAsState()
    val hideAppOnStart by viewModel.hideAppOnStart.collectAsState()
    val batteryShutdown by viewModel.batteryShutdown.collectAsState()
    val logs by viewModel.recordingLogs.collectAsState()

    var currentSubScreen by remember { mutableStateOf("main") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CleanBackground)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
    ) {
        if (currentSubScreen != "main") {
            // Elegant back button bar for inner sub-screens
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                IconButton(
                    onClick = { currentSubScreen = "main" },
                    modifier = Modifier
                        .size(40.dp)
                        .background(SoftNeutralBG, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Return to Settings",
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = when (currentSubScreen) {
                        "tuning" -> "Tuning Switchboard"
                        "info" -> "System Info & State"
                        "about" -> "About OffScreen"
                        "documents" -> "Documents & Guides"
                        "dev_profile" -> "Developer Profile & Creed"
                        else -> "Settings"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CoreDarkText
                )
            }
        }

        AnimatedVisibility(
            visible = currentSubScreen == "main",
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                // Header block
                item {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "OFFSCREEN SYSTEM UTILITIES",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryIndigo,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "Settings & Dashboards",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = CoreDarkText
                        )
                    }
                }

                // Live status connection bar
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SoftIndigoBG),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(if (isRecording) AlertRed else PrimaryIndigo, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isRecording) "RECORDING IN BACKGROUND" else "LENS IDLE / DISARMED",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CoreDarkText
                                )
                                val sec = activeDurationMs / 1000
                                val min = sec / 60
                                val durationStr = String.format("%02d:%02d", min, sec % 60)
                                Text(
                                    text = if (isRecording) "Active capture duration: $durationStr" else "System armed and ready for stealth captures.",
                                    fontSize = 11.sp,
                                    color = MutedGreyText
                                )
                            }
                        }
                    }
                }

                // Master Menu items list
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsNavigationCard(
                            title = "Tuning Options & Switchboard",
                            subtitle = "Modify microphone capture, stealth status bar notifications, smart auto-minimize actions, and battery constraints.",
                            icon = Icons.Default.Tune,
                            onClick = { currentSubScreen = "tuning" }
                        )

                        SettingsNavigationCard(
                            title = "System Info & Diagnostics",
                            subtitle = "Audit available local sandbox storage, test internal SQLite Room DB persistence, inspect platform execution specs.",
                            icon = Icons.Default.CheckCircle,
                            onClick = { currentSubScreen = "info" }
                        )

                        SettingsNavigationCard(
                            title = "About OffScreen Recorder",
                            subtitle = "Read developer bios, offline security policy, system design, and open source licensing info.",
                            icon = Icons.Default.Info,
                            onClick = { currentSubScreen = "about" }
                        )

                        SettingsNavigationCard(
                            title = "User Documents & Guides",
                            subtitle = "Interactive background video capture instructions, safety manual, troubleshooting guides, FAQs.",
                            icon = Icons.Default.Description,
                            onClick = { currentSubScreen = "documents" }
                        )

                        SettingsNavigationCard(
                            title = "Developer Profile & Creed",
                            subtitle = "Read about Rehan Ahmad's vision, system tenets, personal notes, and inspiring work principles.",
                            icon = Icons.Default.AccountCircle,
                            onClick = { currentSubScreen = "dev_profile" }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = currentSubScreen == "tuning",
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "BACKGROUND FILMING TUNER",
                                color = PrimaryIndigo,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            SettingsSwitchRow(
                                title = "Enable Audio Recording",
                                subtitle = "Capture real-time audio streams alongside video.",
                                checked = audioEnabled,
                                onCheckedChange = { viewModel.setAudioEnabled(it) }
                            )

                            HorizontalDivider(color = SoftNeutralBG, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                            SettingsSwitchRow(
                                title = "Stealth Silent Operations",
                                subtitle = "Deactivate filming sound clicks and internal Toast popups.",
                                checked = silentMode,
                                onCheckedChange = { viewModel.setSilentMode(it) }
                            )

                            HorizontalDivider(color = SoftNeutralBG, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                            SettingsSwitchRow(
                                title = "Minimize App Instantly on Start",
                                subtitle = "Close app window right after background capture start.",
                                checked = hideAppOnStart,
                                onCheckedChange = { viewModel.setHideAppOnStart(it) }
                            )

                            HorizontalDivider(color = SoftNeutralBG, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                            SettingsSwitchRow(
                                title = "Low Battery Auto-Preservation",
                                subtitle = "Auto-save clip safely when charger levels slip below 5%.",
                                checked = batteryShutdown,
                                onCheckedChange = { viewModel.setBatteryShutdown(it) }
                            )

                            HorizontalDivider(color = SoftNeutralBG, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                            val hideNotification by viewModel.hideNotification.collectAsState()
                            SettingsSwitchRow(
                                title = "Hide Status Notification",
                                subtitle = "Attempt to hide ongoing record notifications. (Some devices may still show a silent tray icon)",
                                checked = hideNotification,
                                onCheckedChange = { viewModel.setHideNotification(it) }
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = currentSubScreen == "info",
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                // Feature capabilities checkpoints
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "INTEGRATED CAPABILITIES AUDITING",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryIndigo,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            CapabilityItem(label = "Process-Bounded Background Service", verified = true)
                            CapabilityItem(label = "CameraX Pipeline Resolution Binding", verified = true)
                            CapabilityItem(label = "Continuous Timeline Tick Sync", verified = true)
                            CapabilityItem(label = "Local SQLite Room DB Security", verified = true)
                            CapabilityItem(label = "Stereo AAC Microphone Sublayer", verified = audioEnabled)
                            CapabilityItem(label = "Battery Charge Sentinel Thread", verified = batteryShutdown)
                        }
                    }
                }

                // Storage diagnostics helper
                item {
                    var storageState by remember { mutableStateOf(getStorageInfo(context)) }

                    LaunchedEffect(logs.size, isRecording) {
                        storageState = getStorageInfo(context)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SoftNeutralBG)
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SANDBOX STORAGE METRIC",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MutedGreyText
                                )
                                Text(
                                    text = "${storageState.freeGb} GB / ${storageState.totalGb} GB Free",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryIndigo
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            val percentUsed = (1f - (storageState.freeGb / storageState.totalGb)).coerceIn(0.01f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .background(Color.White, shape = CircleShape)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction = percentUsed)
                                        .background(PrimaryIndigo, shape = CircleShape)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Recorded Files:", fontSize = 11.sp, color = MutedGreyText)
                                Text("${logs.size} videos logged", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CoreDarkText)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("App Directory Util:", fontSize = 11.sp, color = MutedGreyText)
                                Text(remember(logs) { formatTotalLogsBytes(context, logs) }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CoreDarkText)
                            }
                        }
                    }
                }

                // Hardware & build specs specs
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SoftNeutralBG)
                            .padding(16.dp)
                    ) {
                        Column {
                            Text("DEVICE & UTILITY SPECS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CoreDarkText)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Software Version", fontSize = 11.sp, color = MutedGreyText)
                                Text("v1.2.6 (Production Release)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryIndigo)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Android platform SDK", fontSize = 11.sp, color = MutedGreyText)
                                Text("API ${Build.VERSION.SDK_INT} (${Build.VERSION.CODENAME})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryIndigo)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Persistence Framework", fontSize = 11.sp, color = MutedGreyText)
                                Text("Room ORM SQLite v2.6.1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryIndigo)
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = currentSubScreen == "about",
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                // Majestic Developer Card: Rehan97
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                        border = BorderStroke(1.5.dp, PrimaryIndigo),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Beautiful initials avatar
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(PrimaryIndigo, shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "R97",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Rehan97",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CoreDarkText
                                    )
                                    Text(
                                        text = "Lead Android Systems Architect",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = PrimaryIndigo,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Rehan is a passionate systems architect specialising in local storage, background task automation, and secure sandboxed media frameworks on Android.",
                                fontSize = 11.5.sp,
                                color = MutedGreyText,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // GitHub Pill
                                Button(
                                    onClick = {
                                        try {
                                            uriHandler.openUri("https://github.com/Ft976")
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open browser URL", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24292E), contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = "GitHub Handle",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("GitHub", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }

                                // LinkedIn Pill
                                Button(
                                    onClick = {
                                        try {
                                            uriHandler.openUri("https://www.linkedin.com/in/rehan-ahmad-863386382?utm_source=share_via&utm_content=profile&utm_medium=member_android")
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open browser URL", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0077B5), contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Launch,
                                        contentDescription = "LinkedIn Handle",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("LinkedIn", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // Description card
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "THE OFFSCREEN METHODOLOGY",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CoreDarkText
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "OffScreen Rec is a highly optimized background video capture utility centered around privacy and automation. By leveraging process-bounded foreground services, Jetpack CameraX, and standard Android audio sublayers, it records seamless .mp4 video clips in a persistent background layer. The screen can be shut off or other app screens loaded without pausing the camera lens.",
                                fontSize = 12.sp,
                                color = MutedGreyText,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Sandbox Architecture Blueprint card
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "PRIVACY & OFFLINE BLUEPRINT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryIndigo,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Everything occurs 100% locally in the device sandbox. The application initiates zero telemetry, does not include third-party tracking APIs, and has absolutely no remote servers. Collected audio/video bytes are committed directly to standard local files via Android's MediaStore API, ensuring complete data ownership.",
                                fontSize = 11.sp,
                                color = MutedGreyText,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // Licensing parameters
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "LICENSING CONSTRAINTS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryIndigo,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Licensed under the open-source MIT License. You are free to distribute, inspect, modify, and build debug APK components for personal or commercial projects.",
                                fontSize = 11.sp,
                                color = MutedGreyText,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = currentSubScreen == "documents",
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                // Step-by-Step guides manual
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "STEP BY STEP RECORDING PROTOCOL",
                                color = PrimaryIndigo,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            DocumentStepItem(index = "1.", text = "Navigate to the home REC screen. Select preferred configurations like video camera lens (Front/Back) and quality preset level.")
                            Spacer(modifier = Modifier.height(8.dp))
                            DocumentStepItem(index = "2.", text = "Tap the large START BACKGROUND CAPTURE button. The background camera foreground service will initialize immediately.")
                            Spacer(modifier = Modifier.height(8.dp))
                            DocumentStepItem(index = "3.", text = "If Instant App Minimizing is active, the app window closes instantly. You can lock your device, turn off the physical screen, or write e-mails without interrupting recording.")
                            Spacer(modifier = Modifier.height(8.dp))
                            DocumentStepItem(index = "4.", text = "Locate the persistent background system notification. You can click 'Stop Recording' to finalize, or the service will stop securely if shut down.")
                            Spacer(modifier = Modifier.height(8.dp))
                            DocumentStepItem(index = "5.", text = "Review, note, share, or delete recorded clips anytime in the stored Clips Library tab.")
                        }
                    }
                }

                // Troubleshooting FAQs
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "TROUBLESHOOTING & COMMON QUESTIONS",
                                color = PrimaryIndigo,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            FAQItem(
                                question = "Why does the app minimize or quit upon starting?",
                                answer = "When 'Minimize App Instantly on Start' is active, the app minimizes itself as soon as the camera service links to provide seamless stealth filming."
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            FAQItem(
                                question = "Where are my mp4 clips stored?",
                                answer = "All recorded video clips are registered locally inside Android's standard sandbox gallery file database under Movies/OffScreenRecorder."
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            FAQItem(
                                question = "The recording keeps shutting down unexpectedly. Why?",
                                answer = "This is typically caused by Android's strict system energy optimizer sleeping background services. To resolve, go to device Settings -> App Management -> OffScreen, and toggle battery restriction to 'Unrestricted'."
                            )
                        }
                    }
                }

                // Safety parameters guidelines
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "SAFETY & STORAGE ADVISORY",
                                color = PrimaryIndigo,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Recording video in the background consumes significant CPU and camera power. Ensure your phone remains cool and avoids direct exposure to heat while under intensive filming sessions. The background service automatically shuts down operational recording under 5% battery to prevent any file metadata serialization corruption.",
                                fontSize = 11.sp,
                                color = MutedGreyText,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = currentSubScreen == "dev_profile",
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                // Slogan Hero Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkNavyAccent),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(RichGold, BrightTeal)
                                        ),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "R97",
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "REHAN AHMAD",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Lead Android Systems Architect",
                                color = BrightTeal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "gave it everything I had",
                                color = SoftGoldBG,
                                fontSize = 14.sp,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Inspiring Holy Verse & Creed Card
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = SoftGoldBG.copy(alpha = 0.6f)),
                        border = BorderStroke(1.5.dp, RichGold),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Spiritual Crest",
                                tint = RichGold,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "“And man will have nothing except what he strives for”",
                                color = DarkNavyAccent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(RichGold, shape = RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "AL-QURAN 53:39",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }

                // Interactive Bio & Core Tech Specs Card
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "ARCHITECTURAL TENETS & STACK",
                                color = BrightTeal,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Local Sandbox Storage", fontSize = 11.5.sp, color = MutedGreyText)
                                Text("Context-bound Private Directory", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = BrightTeal)
                            }
                            HorizontalDivider(color = SoftNeutralBG, modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Background Filming Engine", fontSize = 11.5.sp, color = MutedGreyText)
                                Text("Platform CameraX Framework", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = BrightTeal)
                            }
                            HorizontalDivider(color = SoftNeutralBG, modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("System Database", fontSize = 11.5.sp, color = MutedGreyText)
                                Text("Room SQLite Persistence Layer", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = BrightTeal)
                            }
                        }
                    }
                }

                // Social connections Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    uriHandler.openUri("https://github.com/Ft976")
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open browser URL", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24292E), contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "GitHub Handle",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GitHub", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                try {
                                    uriHandler.openUri("https://www.linkedin.com/in/rehan-ahmad-863386382?utm_source=share_via&utm_content=profile&utm_medium=member_android")
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open browser URL", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0077B5), contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Launch,
                                contentDescription = "LinkedIn Handle",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("LinkedIn", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsNavigationCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(SoftIndigoBG, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1.0f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CoreDarkText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MutedGreyText,
                    lineHeight = 14.sp
                )
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Navigate to $title",
                tint = LightGreyText,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun DocumentStepItem(index: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = index,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryIndigo,
            modifier = Modifier.width(20.dp)
        )
        Text(
            text = text,
            fontSize = 11.sp,
            color = MutedGreyText,
            lineHeight = 15.sp,
            modifier = Modifier.weight(1.0f)
        )
    }
}

@Composable
fun CapabilityItem(label: String, verified: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = CoreDarkText, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (verified) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (verified) PrimaryIndigo else LightGreyText,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (verified) "VERIFIED" else "DISABLED",
                color = if (verified) PrimaryIndigo else LightGreyText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun FAQItem(question: String, answer: String) {
    Column {
        Text("• $question", color = CoreDarkText, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text(answer, color = MutedGreyText, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionPromptDialog(
    permissionsState: MultiplePermissionsState,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .animateContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(SoftIndigoBG, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Authorization Required",
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Authorization Required",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = CoreDarkText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "OffScreen requires camera and microphone permissions before initiating a background recording.",
                    fontSize = 13.sp,
                    color = MutedGreyText,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Camera Access Card
                val cameraGranted = permissionsState.permissions.any {
                    it.permission == android.Manifest.permission.CAMERA && 
                    it.status is com.google.accompanist.permissions.PermissionStatus.Granted
                }
                PermissionCheckItem(
                    title = "Camera Access",
                    description = "Required to capture background raw frame buffer.",
                    isGranted = cameraGranted
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Audio Access Card
                val audioGranted = permissionsState.permissions.any {
                    it.permission == android.Manifest.permission.RECORD_AUDIO && 
                    it.status is com.google.accompanist.permissions.PermissionStatus.Granted
                }
                PermissionCheckItem(
                    title = "Microphone Access",
                    description = "Required to save audio streams during record.",
                    isGranted = audioGranted
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MutedGreyText),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Text("Later", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            permissionsState.launchMultiplePermissionRequest()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.2f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = "Grant Icon", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Grant Access", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionCheckItem(title: String, description: String, isGranted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isGranted) Color(0xFFE8F5E9) else Color(0xFFFFF3E0))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = "Status",
            tint = if (isGranted) Color(0xFF2E7D32) else Color(0xFFEF6C00),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CoreDarkText)
            Text(description, fontSize = 11.sp, color = MutedGreyText)
        }
    }
}
