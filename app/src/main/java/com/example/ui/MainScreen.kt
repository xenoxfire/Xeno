package com.example.ui

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.model.ThemeStyle
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.WarningOrange
import com.example.util.PermissionManager
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val settings by viewModel.settings.collectAsState()
    val isRunning by viewModel.isServiceRunning.collectAsState()
    val permissionsState by viewModel.permissionsState.collectAsState()
    val feedbackMessage by viewModel.userFeedbackMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var currentInputText by remember(settings.savedText) { mutableStateOf(settings.savedText) }
    var showOemGuidance by remember { mutableStateOf(false) }

    // Auto-refresh permissions on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedbackMessage()
        }
    }

    // Permission launcher for Android 13+ Notifications
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissions()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Section
            item {
                HeaderSection(isRunning = isRunning)
            }

            // Big START / STOP Button
            item {
                StartStopButton(
                    isRunning = isRunning,
                    canStart = permissionsState.areAllGranted,
                    onToggle = { viewModel.toggleService() }
                )
            }

            // Saved Text Card
            item {
                SavedTextCard(
                    inputText = currentInputText,
                    onTextChanged = { currentInputText = it },
                    onSave = { viewModel.saveText(currentInputText) }
                )
            }

            // Permissions Status Card
            item {
                PermissionsCard(
                    permissionsState = permissionsState,
                    onRequestOverlay = { PermissionManager.openOverlaySettings(context) },
                    onRequestAccessibility = { PermissionManager.openAccessibilitySettings(context) },
                    onRequestNotification = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            PermissionManager.openAppNotificationSettings(context)
                        }
                    },
                    onRequestBattery = { PermissionManager.requestIgnoreBatteryOptimizations(context) },
                    onOpenShizuku = { PermissionManager.openShizukuApp(context) },
                    onRequestShizukuPerm = { viewModel.requestShizukuPermission() },
                    onRefresh = { viewModel.refreshPermissions() },
                    showOemGuidance = showOemGuidance,
                    onToggleOemGuidance = { showOemGuidance = !showOemGuidance }
                )
            }

            // Restore Auto Time Safety Net Card
            item {
                RestoreAutoTimeCard(
                    onRestore = { viewModel.restoreAutoTime() }
                )
            }

            // Settings & Live Bubble Preview Card
            item {
                SettingsCard(
                    settings = settings,
                    onSizeChange = { viewModel.updateBubbleSize(it) },
                    onOpacityChange = { viewModel.updateBubbleOpacity(it) },
                    onThemeChange = { viewModel.updateThemeStyle(it) },
                    onHapticChange = { viewModel.updateHaptic(it) },
                    onCollapseChange = { viewModel.updateCollapseAfterPaste(it) },
                    onSnapChange = { viewModel.updateSnapToEdge(it) }
                )
            }

            // Footer info
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Xeno v1.0 • Floating Precision Time & Paste Engine",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun HeaderSection(isRunning: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                letterSpacing = 1.sp
            )
            Text(
                text = stringResource(R.string.app_tagline),
                fontSize = 13.sp,
                color = TextSecondary
            )
        }

        // Active Status Pill
        val statusBg = if (isRunning) SuccessGreen.copy(alpha = 0.15f) else DarkCard
        val statusBorder = if (isRunning) SuccessGreen.copy(alpha = 0.5f) else DarkCardBorder
        val statusDotColor = if (isRunning) SuccessGreen else TextTertiary

        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(statusBg)
                .border(1.dp, statusBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isRunning) statusDotColor.copy(alpha = pulseAlpha) else statusDotColor)
            )
            Text(
                text = if (isRunning) stringResource(R.string.status_running) else stringResource(R.string.status_stopped),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isRunning) SuccessGreen else TextSecondary
            )
        }
    }
}

@Composable
fun StartStopButton(
    isRunning: Boolean,
    canStart: Boolean,
    onToggle: () -> Unit
) {
    val buttonBrush = if (isRunning) {
        Brush.horizontalGradient(listOf(Color(0xFFDC2626), Color(0xFFEF4444)))
    } else if (canStart) {
        Brush.horizontalGradient(listOf(NeonCyan, NeonPurple))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(buttonBrush)
                .clickable(enabled = isRunning || canStart) { onToggle() }
                .testTag(if (isRunning) "stop_service_button" else "start_service_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isRunning) {
                    stringResource(R.string.btn_stop_service)
                } else if (canStart) {
                    stringResource(R.string.btn_start_service)
                } else {
                    "GRANT ALL PERMISSIONS TO START"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (!isRunning && !canStart) TextTertiary else TextPrimary,
                letterSpacing = 1.sp
            )
        }

        if (!isRunning && !canStart) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "⚠️ All 5 required permissions must be granted before starting Xeno overlay.",
                color = WarningOrange,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun SavedTextCard(
    inputText: String,
    onTextChanged: (String) -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(18.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(DarkCardBorder, Color.Transparent)))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.saved_text_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Text(
                    text = stringResource(R.string.saved_text_char_count, inputText.length),
                    fontSize = 12.sp,
                    color = TextTertiary
                )
            }

            Text(
                text = stringResource(R.string.saved_text_subtitle),
                fontSize = 12.sp,
                color = TextSecondary
            )

            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("saved_text_input"),
                placeholder = {
                    Text(stringResource(R.string.saved_text_placeholder), color = TextTertiary)
                },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp)
            )

            // Quick preset chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf("Order #", "Code: ", "Hello!", "Approved ✓")
                presets.forEach { preset ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkCard)
                            .clickable {
                                onTextChanged(if (inputText.isEmpty()) preset else "$inputText $preset")
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = preset, fontSize = 11.sp, color = NeonBlue)
                    }
                }
            }

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("save_text_button"),
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.saved_text_save_btn),
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
fun PermissionsCard(
    permissionsState: PermissionsState,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestBattery: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRequestShizukuPerm: () -> Unit,
    onRefresh: () -> Unit,
    showOemGuidance: Boolean,
    onToggleOemGuidance: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(18.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(DarkCardBorder, Color.Transparent)))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = NeonPurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.permissions_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh permissions",
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.permissions_subtitle),
                fontSize = 12.sp,
                color = TextSecondary
            )

            // 1. Display Over Other Apps
            PermissionItem(
                icon = Icons.Default.Layers,
                title = stringResource(R.string.perm_overlay_title),
                desc = stringResource(R.string.perm_overlay_desc),
                isGranted = permissionsState.isOverlayGranted,
                actionLabel = stringResource(R.string.perm_grant_btn),
                onAction = onRequestOverlay
            )

            // 2. Accessibility Service
            PermissionItem(
                icon = Icons.Default.ContentPaste,
                title = stringResource(R.string.perm_accessibility_title),
                desc = stringResource(R.string.perm_accessibility_desc),
                isGranted = permissionsState.isAccessibilityGranted,
                actionLabel = stringResource(R.string.perm_grant_btn),
                onAction = onRequestAccessibility
            )

            // 3. Notification
            PermissionItem(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.perm_notifications_title),
                desc = stringResource(R.string.perm_notifications_desc),
                isGranted = permissionsState.isNotificationGranted,
                actionLabel = stringResource(R.string.perm_grant_btn),
                onAction = onRequestNotification
            )

            // 4. Ignore Battery Optimization
            PermissionItem(
                icon = Icons.Default.BatteryAlert,
                title = stringResource(R.string.perm_battery_title),
                desc = stringResource(R.string.perm_battery_desc),
                isGranted = permissionsState.isBatteryOptimizationIgnored,
                actionLabel = stringResource(R.string.perm_grant_btn),
                onAction = onRequestBattery
            )

            // 5. Shizuku Service & Permission
            ShizukuPermissionItem(
                isShizukuRunning = permissionsState.isShizukuRunning,
                isShizukuPermGranted = permissionsState.isShizukuPermissionGranted,
                onOpenShizuku = onOpenShizuku,
                onRequestPerm = onRequestShizukuPerm
            )

            // OEM Guidance Expander
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCard)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleOemGuidance() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = NeonBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.oem_guidance_title),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonBlue
                        )
                    }
                    Text(
                        text = if (showOemGuidance) "▲" else "▼",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                AnimatedVisibility(visible = showOemGuidance) {
                    Column(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(stringResource(R.string.oem_guidance_step1), fontSize = 12.sp, color = TextSecondary)
                        Text(stringResource(R.string.oem_guidance_step2), fontSize = 12.sp, color = TextSecondary)
                        Text(stringResource(R.string.oem_guidance_step3), fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionItem(
    icon: ImageVector,
    title: String,
    desc: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) SuccessGreen else TextTertiary,
                modifier = Modifier
                    .size(22.dp)
                    .padding(top = 2.dp)
            )
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = desc,
                    fontSize = 11.sp,
                    color = TextTertiary,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isGranted) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SuccessGreen.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.perm_granted),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen
                )
            }
        } else {
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.height(34.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text(text = actionLabel, fontSize = 12.sp, color = NeonCyan)
            }
        }
    }
}

@Composable
fun ShizukuPermissionItem(
    isShizukuRunning: Boolean,
    isShizukuPermGranted: Boolean,
    onOpenShizuku: () -> Unit,
    onRequestPerm: () -> Unit
) {
    val isReady = isShizukuRunning && isShizukuPermGranted

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = null,
                tint = if (isReady) SuccessGreen else if (isShizukuRunning) WarningOrange else ErrorRed,
                modifier = Modifier
                    .size(22.dp)
                    .padding(top = 2.dp)
            )
            Column {
                Text(
                    text = stringResource(R.string.perm_shizuku_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                val statusDesc = if (isReady) {
                    stringResource(R.string.shizuku_status_running)
                } else if (!isShizukuRunning) {
                    stringResource(R.string.shizuku_status_not_running)
                } else {
                    stringResource(R.string.shizuku_status_need_perm)
                }
                Text(
                    text = statusDesc,
                    fontSize = 11.sp,
                    color = if (isReady) TextTertiary else if (isShizukuRunning) WarningOrange else ErrorRed,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isReady) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SuccessGreen.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.perm_granted),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen
                )
            }
        } else if (!isShizukuRunning) {
            Button(
                onClick = onOpenShizuku,
                modifier = Modifier.height(34.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkCardBorder),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text(text = stringResource(R.string.shizuku_open_app), fontSize = 11.sp, color = TextPrimary)
            }
        } else {
            Button(
                onClick = onRequestPerm,
                modifier = Modifier.height(34.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text(text = stringResource(R.string.shizuku_req_perm), fontSize = 11.sp, color = TextPrimary)
            }
        }
    }
}

@Composable
fun RestoreAutoTimeCard(onRestore: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(18.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(DarkCardBorder, Color.Transparent)))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.btn_restore_auto_time),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = stringResource(R.string.btn_restore_auto_time_desc),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onRestore,
                modifier = Modifier
                    .height(38.dp)
                    .testTag("restore_auto_time_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Restore ⏱", fontSize = 12.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SettingsCard(
    settings: com.example.model.SettingsModel,
    onSizeChange: (Int) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onThemeChange: (ThemeStyle) -> Unit,
    onHapticChange: (Boolean) -> Unit,
    onCollapseChange: (Boolean) -> Unit,
    onSnapChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(18.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(DarkCardBorder, Color.Transparent)))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.settings_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            var localSize by remember(settings.bubbleSizeDp) { mutableFloatStateOf(settings.bubbleSizeDp.toFloat()) }
            var localOpacity by remember(settings.bubbleOpacity) { mutableFloatStateOf(settings.bubbleOpacity) }

            // Live Bubble Preview Stage
            BubblePreviewBox(
                sizeDp = localSize.roundToInt(),
                opacity = localOpacity,
                theme = settings.themeStyle
            )

            // Bubble Size Slider
            Column {
                Text(
                    text = stringResource(R.string.settings_size_label, localSize.roundToInt()),
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Slider(
                    value = localSize,
                    onValueChange = { localSize = it },
                    onValueChangeFinished = { onSizeChange(localSize.roundToInt()) },
                    valueRange = 28f..96f,
                    steps = 16,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = DarkCardBorder
                    ),
                    modifier = Modifier.testTag("bubble_size_slider")
                )
            }

            // Bubble Opacity Slider
            Column {
                val percent = (localOpacity * 100).roundToInt()
                Text(
                    text = stringResource(R.string.settings_opacity_label, percent),
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Slider(
                    value = localOpacity,
                    onValueChange = { localOpacity = it },
                    onValueChangeFinished = { onOpacityChange(localOpacity) },
                    valueRange = 0.08f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonPurple,
                        activeTrackColor = NeonPurple,
                        inactiveTrackColor = DarkCardBorder
                    ),
                    modifier = Modifier.testTag("bubble_opacity_slider")
                )
            }

            // UI Theme Picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_theme_title),
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeStyle.entries.forEach { style ->
                        val isSelected = settings.themeStyle == style
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) NeonCyan.copy(alpha = 0.15f) else DarkCard)
                                .border(
                                    1.dp,
                                    if (isSelected) NeonCyan else DarkCardBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onThemeChange(style) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = style.displayName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) NeonCyan else TextSecondary
                            )
                        }
                    }
                }
            }

            // Toggles
            SettingSwitchRow(
                title = stringResource(R.string.setting_haptic_title),
                desc = stringResource(R.string.setting_haptic_desc),
                checked = settings.hapticFeedback,
                onCheckedChange = onHapticChange
            )

            SettingSwitchRow(
                title = stringResource(R.string.setting_collapse_title),
                desc = stringResource(R.string.setting_collapse_desc),
                checked = settings.collapseAfterPaste,
                onCheckedChange = onCollapseChange
            )

            SettingSwitchRow(
                title = stringResource(R.string.setting_snap_title),
                desc = stringResource(R.string.setting_snap_desc),
                checked = settings.snapToEdge,
                onCheckedChange = onSnapChange
            )
        }
    }
}

@Composable
fun BubblePreviewBox(
    sizeDp: Int,
    opacity: Float,
    theme: ThemeStyle
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCard)
            .border(1.dp, DarkCardBorder, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        val previewSize = sizeDp.coerceIn(28, 96).dp

        val bubbleBorder = when (theme) {
            ThemeStyle.GLASS -> 2.dp to Color.White.copy(alpha = opacity * 0.7f)
            ThemeStyle.NEON_DARK -> 2.5.dp to NeonCyan.copy(alpha = opacity)
            ThemeStyle.MINIMAL_MONO -> 1.5.dp to Color.Gray.copy(alpha = opacity)
            ThemeStyle.AURORA -> 2.dp to NeonPink.copy(alpha = opacity)
        }

        val bubbleBackground = when (theme) {
            ThemeStyle.GLASS -> Brush.radialGradient(listOf(Color.White.copy(alpha = opacity * 0.25f), Color.LightGray.copy(alpha = opacity * 0.15f)))
            ThemeStyle.NEON_DARK -> Brush.verticalGradient(listOf(Color(0xFF0B0E14).copy(alpha = opacity), Color(0xFF101726).copy(alpha = opacity)))
            ThemeStyle.MINIMAL_MONO -> Brush.verticalGradient(listOf(Color(0xFF1E293B).copy(alpha = opacity), Color(0xFF0F172A).copy(alpha = opacity)))
            ThemeStyle.AURORA -> Brush.linearGradient(listOf(Color(0xFFEC4899).copy(alpha = opacity), Color(0xFF7C3AED).copy(alpha = opacity)))
        }

        Box(
            modifier = Modifier
                .size(previewSize)
                .clip(CircleShape)
                .background(bubbleBackground)
                .border(bubbleBorder.first, bubbleBorder.second, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Inner icon
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = null,
                tint = when (theme) {
                    ThemeStyle.GLASS -> Color.White.copy(alpha = opacity)
                    ThemeStyle.NEON_DARK -> NeonCyan.copy(alpha = opacity)
                    ThemeStyle.MINIMAL_MONO -> Color.White.copy(alpha = opacity)
                    ThemeStyle.AURORA -> Color.White.copy(alpha = opacity)
                },
                modifier = Modifier.size((previewSize.value * 0.42f).dp)
            )
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(text = desc, fontSize = 11.sp, color = TextTertiary)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextPrimary,
                checkedTrackColor = NeonPurple,
                uncheckedTrackColor = DarkCardBorder
            )
        )
    }
}
