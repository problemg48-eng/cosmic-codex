package ai.cosmic.data.dol

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the optional Floating DOL overlay preference and `SYSTEM_ALERT_WINDOW` permission.
 *
 * Guarantees:
 * - Never assumes overlay permission was granted just because Settings was opened.
 * - Rechecks [Settings.canDrawOverlays] on resume and revokes/stops overlay if denied.
 * - Never forces the user to grant overlay permission; in-app DOL, notifications, and voice
 *   continue working normally if denied.
 */
object DolOverlayManager {
    private const val TAG = "DolOverlayManager"
    private const val PREFS_NAME = "dol_overlay_prefs"
    private const val KEY_FLOATING_ENABLED = "floating_dol_enabled"

    const val PERMISSION_EXPLANATION =
        "Floating DOL allows your assistant mascot to appear above other apps, so you can see task updates without opening ANTIGEM."

    private val _floatingEnabled = MutableStateFlow(false)
    val floatingEnabled: StateFlow<Boolean> = _floatingEnabled.asStateFlow()

    private val _hasOverlayPermission = MutableStateFlow(false)
    val hasOverlayPermission: StateFlow<Boolean> = _hasOverlayPermission.asStateFlow()

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _floatingEnabled.value = prefs.getBoolean(KEY_FLOATING_ENABLED, false)
        refresh(context)
    }

    /**
     * Rechecks actual Android overlay permission state and starts/stops [DolOverlayService]
     * accordingly. Call in `onResume()`.
     */
    fun refresh(context: Context) {
        val granted = canDrawOverlays(context)
        _hasOverlayPermission.value = granted

        if (_floatingEnabled.value && granted) {
            DolOverlayService.start(context)
        } else {
            // If permission was revoked or feature disabled, remove overlay immediately
            if (_floatingEnabled.value && !granted) {
                Log.w(TAG, "Overlay permission not granted; keeping Floating DOL inactive")
            }
            DolOverlayService.stop(context)
        }
    }

    /**
     * Explicit user toggle for Floating DOL.
     * Returns `true` if overlay is now active, `false` if permission is needed (caller may
     * offer to open Settings) or if disabled.
     */
    fun setFloatingEnabled(context: Context, enabled: Boolean): Boolean {
        _floatingEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FLOATING_ENABLED, enabled)
            .apply()

        if (!enabled) {
            DolOverlayService.stop(context)
            return false
        }

        val granted = canDrawOverlays(context)
        _hasOverlayPermission.value = granted
        return if (granted) {
            DolOverlayService.start(context)
            true
        } else {
            false
        }
    }

    /**
     * Opens Android's "Display over other apps" settings screen for this package.
     */
    fun openOverlayPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed opening package overlay settings, trying generic: ${e.message}")
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
    }

    /** Pure helper for unit testing state transitions. */
    internal fun computeOverlayActive(enabled: Boolean, permissionGranted: Boolean): Boolean =
        enabled && permissionGranted
}
