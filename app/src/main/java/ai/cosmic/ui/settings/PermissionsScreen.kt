package ai.cosmic.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.cosmic.data.service.AntiGemService
import ai.cosmic.data.storage.PermissionManager
import ai.cosmic.data.storage.PermissionManager.PermissionStatus
import ai.cosmic.data.storage.StorageGateway
import ai.cosmic.theme.AppColors
import ai.cosmic.ui.components.bouncyVerticalScroll

/**
 * Settings -> Permissions Management Center.
 *
 * Displays real Android permission states (never hardcoded), accessible storage roots,
 * background service / battery optimization status, diagnostics, and quick actions
 * to open system settings or refresh status.
 */
@Composable
fun PermissionsScreen(cardBg: Color, cardBorder: Color) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        PermissionManager.refresh(context)
        ai.cosmic.data.dol.DolOverlayManager.init(context)
        ai.cosmic.data.dol.DolVoiceManager.init(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PermissionManager.refresh(context)
                ai.cosmic.data.dol.DolOverlayManager.refresh(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permState by PermissionManager.state.collectAsState()
    val storageState by StorageGateway.accessState.collectAsState()
    val isServiceRunning by AntiGemService.isRunning.collectAsState()
    val hasOverlayPerm by ai.cosmic.data.dol.DolOverlayManager.hasOverlayPermission.collectAsState()
    val floatingDolEnabled by ai.cosmic.data.dol.DolOverlayManager.floatingEnabled.collectAsState()
    val voiceEnabled by ai.cosmic.data.dol.DolVoiceManager.voiceEnabled.collectAsState()
    val scrollState = rememberScrollState()

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        PermissionManager.refresh(context)
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        PermissionManager.refresh(context)
    }

    val legacyStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        StorageGateway.setupAndRefresh(context)
        PermissionManager.refresh(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .bouncyVerticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header + Refresh action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "System Permissions & Storage",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.onBackground
                )
                Text(
                    text = "Live Android API ${Build.VERSION.SDK_INT} (Android ${Build.VERSION.RELEASE}) status",
                    fontSize = 12.sp,
                    color = AppColors.textSecondary
                )
            }
            FilledTonalButton(
                onClick = { PermissionManager.refresh(context) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Refresh", fontSize = 12.5.sp)
            }
        }

        // Core Permissions Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Column {
                PermissionStatusRow(
                    icon = Icons.Outlined.Folder,
                    tint = AppColors.info,
                    title = "Shared Storage Access",
                    subtitle = permState.storageExplanation,
                    statusLabel = statusLabel(permState.storage),
                    statusColor = statusColor(permState.storage),
                    actionLabel = if (permState.storage == PermissionStatus.GRANTED) "Settings" else "Grant",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            PermissionManager.requestStoragePermission(context)
                        } else if (permState.storage != PermissionStatus.GRANTED) {
                            legacyStorageLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                )
                            )
                        } else {
                            PermissionManager.openAppSettings(context)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 60.dp), color = cardBorder)

                PermissionStatusRow(
                    icon = Icons.Outlined.NotificationsActive,
                    tint = AppColors.primary,
                    title = "Notifications",
                    subtitle = permState.notificationExplanation,
                    statusLabel = statusLabel(permState.notifications),
                    statusColor = statusColor(permState.notifications),
                    actionLabel = if (permState.notifications == PermissionStatus.GRANTED) "Channels" else "Allow",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            permState.notifications == PermissionStatus.DENIED
                        ) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            PermissionManager.requestNotificationSettings(context)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 60.dp), color = cardBorder)

                PermissionStatusRow(
                    icon = Icons.Outlined.PlayCircleOutline,
                    tint = AppColors.success,
                    title = "Background Execution Service",
                    subtitle = if (isServiceRunning)
                        "Foreground service active — agents continue running when minimized"
                    else
                        "Service idle — starts automatically during agent or terminal tasks",
                    statusLabel = if (isServiceRunning) "Active" else "Idle",
                    statusColor = if (isServiceRunning) AppColors.success else AppColors.textSecondary,
                    actionLabel = if (isServiceRunning) "Running" else "Start",
                    onAction = {
                        if (!isServiceRunning) {
                            AntiGemService.start(context, fromForeground = true)
                            PermissionManager.refresh(context)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 60.dp), color = cardBorder)

                PermissionStatusRow(
                    icon = Icons.Outlined.BatteryChargingFull,
                    tint = AppColors.warning,
                    title = "Battery Optimization",
                    subtitle = if (permState.batteryOptimizationExempt)
                        "Unrestricted — OS will not throttle background agent execution"
                    else
                        "Optimized — long-running agent tasks may be paused by Android Doze",
                    statusLabel = if (permState.batteryOptimizationExempt) "Unrestricted" else "Optimized",
                    statusColor = if (permState.batteryOptimizationExempt) AppColors.success else AppColors.warning,
                    actionLabel = if (permState.batteryOptimizationExempt) "Settings" else "Exempt",
                    onAction = {
                        if (!permState.batteryOptimizationExempt) {
                            PermissionManager.requestBatteryOptimizationExemption(context)
                        } else {
                            PermissionManager.openAppSettings(context)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 60.dp), color = cardBorder)

                PermissionStatusRow(
                    icon = Icons.Outlined.Mic,
                    tint = AppColors.accentAlt,
                    title = "Microphone (Voice & Jarvis)",
                    subtitle = if (permState.microphone == PermissionStatus.GRANTED)
                        "Granted for voice dictation and hands-free Jarvis mode"
                    else
                        "Optional — required only for voice input and Jarvis",
                    statusLabel = statusLabel(permState.microphone),
                    statusColor = statusColor(permState.microphone),
                    actionLabel = if (permState.microphone == PermissionStatus.GRANTED) "Settings" else "Allow",
                    onAction = {
                        if (permState.microphone != PermissionStatus.GRANTED) {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            PermissionManager.openAppSettings(context)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 60.dp), color = cardBorder)

                PermissionStatusRow(
                    icon = Icons.Outlined.Layers,
                    tint = Color(0xFF7C3AED),
                    title = "Display Over Other Apps",
                    subtitle = ai.cosmic.data.dol.DolOverlayManager.PERMISSION_EXPLANATION,
                    statusLabel = if (hasOverlayPerm) "Granted" else "Not Granted",
                    statusColor = if (hasOverlayPerm) AppColors.success else AppColors.warning,
                    actionLabel = if (hasOverlayPerm) "Settings" else "Grant",
                    onAction = {
                        ai.cosmic.data.dol.DolOverlayManager.openOverlayPermissionSettings(context)
                    }
                )
            }
        }

        // DOL Voice & Floating DOL Controls
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "DOL Assistant & Floating Overlay",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.onBackground
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Voice Announcements", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = AppColors.onBackground)
                        Text("Speak task updates using Android TextToSpeech", fontSize = 11.5.sp, color = AppColors.textSecondary)
                    }
                    Switch(
                        checked = voiceEnabled,
                        onCheckedChange = { ai.cosmic.data.dol.DolVoiceManager.setVoiceEnabled(context, it) }
                    )
                }
                HorizontalDivider(color = cardBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Floating DOL", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = AppColors.onBackground)
                        Text(
                            if (floatingDolEnabled && !hasOverlayPerm) "Waiting for Display over other apps permission"
                            else "Show draggable DOL mascot above other apps",
                            fontSize = 11.5.sp,
                            color = if (floatingDolEnabled && !hasOverlayPerm) AppColors.warning else AppColors.textSecondary
                        )
                    }
                    Switch(
                        checked = floatingDolEnabled,
                        onCheckedChange = { enabled ->
                            val active = ai.cosmic.data.dol.DolOverlayManager.setFloatingEnabled(context, enabled)
                            if (enabled && !active) {
                                ai.cosmic.data.dol.DolOverlayManager.openOverlayPermissionSettings(context)
                            }
                        }
                    )
                }
            }
        }

        // Storage Gateway Roots & Diagnostics
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Accessible Storage Roots",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.onBackground
                    )
                    if (storageState.hasSharedStorageAccess && !storageState.symlinksMounted) {
                        TextButton(
                            onClick = { StorageGateway.setupAndRefresh(context) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Mount Symlinks", fontSize = 12.sp)
                        }
                    }
                }
                Text(
                    text = storageState.statusMessage,
                    fontSize = 12.sp,
                    color = AppColors.textSecondary
                )
                storageState.accessibleRoots.forEach { root ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.fill.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = root.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppColors.onBackground
                            )
                            Text(
                                text = root.path,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AppColors.textSecondary
                            )
                        }
                        Text(
                            text = if (root.writable) "RW" else "RO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (root.writable) AppColors.success else AppColors.warning
                        )
                    }
                }
            }
        }

        // Diagnostics & Audit Summary
        val recentAudit = remember(permState, storageState) { StorageGateway.auditEntries.takeLast(8).reversed() }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Storage Gateway Audit (${StorageGateway.auditEntries.size} events)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.onBackground
                )
                if (recentAudit.isEmpty()) {
                    Text(
                        text = "No agent storage operations recorded in this session yet.",
                        fontSize = 12.sp,
                        color = AppColors.textSecondary
                    )
                } else {
                    recentAudit.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (entry.success) AppColors.success else AppColors.danger)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${entry.operation} ${entry.path}",
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AppColors.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { PermissionManager.openAppSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Settings, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Android App Info", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    statusLabel: String,
    statusColor: Color,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.onBackground
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = statusLabel,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = AppColors.textSecondary
            )
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(
            onClick = onAction,
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun statusLabel(status: PermissionStatus): String = when (status) {
    PermissionStatus.GRANTED -> "Granted"
    PermissionStatus.DENIED -> "Denied"
    PermissionStatus.REVOKED -> "Revoked"
    PermissionStatus.NOT_APPLICABLE -> "Auto"
    PermissionStatus.NOT_CHECKED -> "Checking"
}

private fun statusColor(status: PermissionStatus): Color = when (status) {
    PermissionStatus.GRANTED, PermissionStatus.NOT_APPLICABLE -> AppColors.success
    PermissionStatus.REVOKED -> AppColors.danger
    PermissionStatus.DENIED -> AppColors.warning
    PermissionStatus.NOT_CHECKED -> AppColors.textSecondary
}
