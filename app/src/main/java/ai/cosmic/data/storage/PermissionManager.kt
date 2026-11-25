package ai.cosmic.data.storage

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Unified permission manager for the application.
 *
 * Tracks the state of all required and optional Android permissions:
 * - Storage (MANAGE_EXTERNAL_STORAGE on Android 11+, READ/WRITE on older)
 * - Notifications (POST_NOTIFICATIONS on Android 13+)
 * - Background execution (foreground service, battery optimization)
 * - Microphone (RECORD_AUDIO)
 *
 * Detects permission revocation and provides real Android states, never hardcoded values.
 */
object PermissionManager {
    private const val TAG = "PermissionManager"

    private val _state = MutableStateFlow(PermissionState())
    val state: StateFlow<PermissionState> = _state.asStateFlow()

    data class PermissionState(
        val storage: PermissionStatus = PermissionStatus.NOT_CHECKED,
        val notifications: PermissionStatus = PermissionStatus.NOT_CHECKED,
        val microphone: PermissionStatus = PermissionStatus.NOT_CHECKED,
        val batteryOptimizationExempt: Boolean = false,
        /** True if foreground service is currently running. */
        val foregroundServiceRunning: Boolean = false,
        /** Detailed explanation when a permission is missing. */
        val storageExplanation: String = "",
        val notificationExplanation: String = "",
    )

    enum class PermissionStatus {
        NOT_CHECKED,
        GRANTED,
        DENIED,
        /** Permission was previously granted but has been revoked (detected on refresh). */
        REVOKED,
        /** Not applicable on this Android version. */
        NOT_APPLICABLE,
    }

    private var previousStorage: PermissionStatus = PermissionStatus.NOT_CHECKED
    private var previousNotification: PermissionStatus = PermissionStatus.NOT_CHECKED
    private var previousMicrophone: PermissionStatus = PermissionStatus.NOT_CHECKED

    /**
     * Refreshes all permission states from the real Android system.
     * Call on ON_RESUME, after returning from system settings, and periodically.
     */
    fun refresh(context: Context) {
        val storageGranted = checkStorage(context)
        val notifGranted = checkNotifications(context)
        val micGranted = checkMicrophone(context)
        val batteryExempt = checkBatteryOptimization(context)

        // Detect revocation: was granted, now denied
        val storageStatus = when {
            storageGranted -> PermissionStatus.GRANTED
            previousStorage == PermissionStatus.GRANTED -> {
                Log.w(TAG, "Storage permission was revoked!")
                PermissionStatus.REVOKED
            }
            else -> PermissionStatus.DENIED
        }
        val notifStatus = when {
            notifGranted -> PermissionStatus.GRANTED
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> PermissionStatus.NOT_APPLICABLE
            previousNotification == PermissionStatus.GRANTED -> {
                Log.w(TAG, "Notification permission was revoked!")
                PermissionStatus.REVOKED
            }
            else -> PermissionStatus.DENIED
        }
        val micStatus = when {
            micGranted -> PermissionStatus.GRANTED
            previousMicrophone == PermissionStatus.GRANTED -> PermissionStatus.REVOKED
            else -> PermissionStatus.DENIED
        }

        // Update previous states
        previousStorage = storageStatus
        previousNotification = notifStatus
        previousMicrophone = micStatus

        val storageExplanation = when {
            storageStatus == PermissionStatus.GRANTED -> "Full shared storage access"
            storageStatus == PermissionStatus.REVOKED -> "Storage permission was revoked — agents cannot access files outside the app"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                "Grant \"All Files Access\" in system settings so agents can work with your project files"
            else ->
                "Grant storage permission so agents can read and write project files"
        }

        val notifExplanation = when {
            notifStatus == PermissionStatus.GRANTED -> "Notifications enabled"
            notifStatus == PermissionStatus.NOT_APPLICABLE -> "Notifications always available on this Android version"
            notifStatus == PermissionStatus.REVOKED -> "Notification permission was revoked — agent progress won't be shown"
            else -> "Allow notifications for agent task progress, completion, and approvals"
        }

        _state.value = PermissionState(
            storage = storageStatus,
            notifications = notifStatus,
            microphone = micStatus,
            batteryOptimizationExempt = batteryExempt,
            storageExplanation = storageExplanation,
            notificationExplanation = notifExplanation,
        )

        // Sync storage gateway state
        StorageGateway.refreshState(context)
    }

    // --- Individual permission checks ---

    private fun checkStorage(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    private fun checkMicrophone(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkBatteryOptimization(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } catch (_: Exception) {
            false
        }
    }

    // --- Permission request intents ---

    /**
     * Launches the system settings screen for MANAGE_EXTERNAL_STORAGE.
     * On Android < 11, this is a no-op (use runtime permission request instead).
     */
    fun requestStoragePermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Log.w(TAG, "Failed to open storage permission settings")
                }
            }
        } else {
            // For pre-Android 11, use the app info page (runtime permission dialog is better,
            // but this is the fallback for when the dialog has been dismissed permanently)
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Log.w(TAG, "Failed to open app settings")
            }
        }
    }

    /**
     * Launches the system settings for notification permission.
     */
    fun requestNotificationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Log.w(TAG, "Failed to open notification settings")
            }
        }
    }

    /**
     * Requests battery optimization exemption.
     */
    fun requestBatteryOptimizationExemption(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request battery optimization exemption: ${e.message}")
        }
    }

    /**
     * Opens the app's details page in system settings.
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Log.w(TAG, "Failed to open app settings")
        }
    }
}
