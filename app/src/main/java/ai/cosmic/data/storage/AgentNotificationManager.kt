package ai.cosmic.data.storage

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import ai.cosmic.MainActivity
import ai.cosmic.R

/**
 * Professional notification channels and helpers for agent task lifecycle.
 *
 * Channels:
 *  1. Agent Activity — currently working agent status
 *  2. Task Progress — ongoing task progress updates
 *  3. Task Completed — task finished successfully
 *  4. Task Failed — task encountered an error
 *  5. Security & Permissions — permission requests, approval prompts
 */
object AgentNotificationManager {
    private const val TAG = "AgentNotifyMgr"

    // Channel IDs
    const val CHANNEL_AGENT_ACTIVITY = "agent_activity"
    const val CHANNEL_TASK_PROGRESS = "task_progress"
    const val CHANNEL_TASK_COMPLETED = "task_completed"
    const val CHANNEL_TASK_FAILED = "task_failed"
    const val CHANNEL_SECURITY = "security_permissions"

    // Group ID
    private const val GROUP_AGENTS = "agent_tasks"

    // Notification IDs (ranges to avoid conflicts with existing IDs like 1337)
    private const val NOTIFICATION_BASE_AGENT = 5000
    private const val NOTIFICATION_BASE_TASK = 6000
    private const val NOTIFICATION_BASE_SECURITY = 7000

    private var initialized = false
    private var nextTaskNotificationId = NOTIFICATION_BASE_TASK

    /**
     * Creates all notification channels. Idempotent — safe to call multiple times.
     * Call from Application.onCreate or Service.onCreate.
     */
    fun ensureChannels(context: Context) {
        if (initialized) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            initialized = true
            return
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // Create the group
        nm.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_AGENTS, "AI Agent Tasks")
        )

        // 1. Agent Activity — low importance, ongoing
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_AGENT_ACTIVITY, "Agent Activity", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows which AI agent is currently working"
                group = GROUP_AGENTS
                setShowBadge(false)
            }
        )

        // 2. Task Progress — low importance, ongoing, updatable
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TASK_PROGRESS, "Task Progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows progress of running agent tasks"
                group = GROUP_AGENTS
                setShowBadge(false)
            }
        )

        // 3. Task Completed — default importance, one-shot
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TASK_COMPLETED, "Task Completed", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifies when an agent task finishes successfully"
                group = GROUP_AGENTS
            }
        )

        // 4. Task Failed — high importance, one-shot
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TASK_FAILED, "Task Failed", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifies when an agent task fails or encounters an error"
                group = GROUP_AGENTS
            }
        )

        // 5. Security & Permissions — high importance
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SECURITY, "Security & Permissions", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Permission approval requests from agents and security alerts"
                group = GROUP_AGENTS
            }
        )

        initialized = true
        Log.i(TAG, "Agent notification channels created")
    }

    // --- Notification builders ---

    /**
     * Shows a notification that an agent is actively working.
     */
    fun notifyAgentWorking(
        context: Context,
        agentName: String,
        taskSummary: String,
        agentIndex: Int = 0
    ) {
        ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, NOTIFICATION_BASE_AGENT + agentIndex, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_AGENT_ACTIVITY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$agentName is working")
            .setContentText(taskSummary)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

        try {
            nm.notify(NOTIFICATION_BASE_AGENT + agentIndex, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot post agent notification: ${e.message}")
        }
    }

    /**
     * Shows/updates a task progress notification.
     * Returns the notification ID for later updating or cancellation.
     */
    fun notifyTaskProgress(
        context: Context,
        taskName: String,
        progress: Int,
        maxProgress: Int = 100,
        indeterminate: Boolean = false
    ): Int {
        ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return -1
        val notifId = synchronized(this) { nextTaskNotificationId++ }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notifId, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_TASK_PROGRESS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(taskName)
            .setContentText(if (indeterminate) "Working…" else "$progress%")
            .setProgress(maxProgress, progress, indeterminate)
            .setOngoing(true)
            .setShowWhen(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

        try {
            nm.notify(notifId, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot post task progress notification: ${e.message}")
        }
        return notifId
    }

    /**
     * Updates an existing task progress notification.
     */
    fun updateTaskProgress(
        context: Context,
        notificationId: Int,
        taskName: String,
        progress: Int,
        maxProgress: Int = 100
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val notification = NotificationCompat.Builder(context, CHANNEL_TASK_PROGRESS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(taskName)
            .setContentText("$progress%")
            .setProgress(maxProgress, progress, false)
            .setOngoing(true)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            nm.notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot update task notification: ${e.message}")
        }
    }

    /**
     * Shows a task completed notification and cancels the progress one.
     */
    fun notifyTaskCompleted(
        context: Context,
        taskName: String,
        summary: String,
        progressNotificationId: Int = -1
    ) {
        ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (progressNotificationId >= 0) {
            nm.cancel(progressNotificationId)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, NOTIFICATION_BASE_TASK + 999, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_TASK_COMPLETED)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("✓ $taskName")
            .setContentText(summary)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        try {
            nm.notify(synchronized(this) { nextTaskNotificationId++ }, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot post task completed notification: ${e.message}")
        }
    }

    /**
     * Shows a task failed notification.
     */
    fun notifyTaskFailed(
        context: Context,
        taskName: String,
        errorMessage: String,
        progressNotificationId: Int = -1
    ) {
        ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (progressNotificationId >= 0) {
            nm.cancel(progressNotificationId)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, NOTIFICATION_BASE_TASK + 998, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_TASK_FAILED)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("✗ $taskName")
            .setContentText(errorMessage)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()

        try {
            nm.notify(synchronized(this) { nextTaskNotificationId++ }, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot post task failed notification: ${e.message}")
        }
    }

    /**
     * Shows a security/permission approval request notification.
     */
    fun notifyPermissionRequest(
        context: Context,
        title: String,
        description: String
    ) {
        ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, NOTIFICATION_BASE_SECURITY, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SECURITY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(description)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        try {
            nm.notify(NOTIFICATION_BASE_SECURITY, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot post security notification: ${e.message}")
        }
    }

    /**
     * Cancels an agent working notification.
     */
    fun cancelAgentNotification(context: Context, agentIndex: Int = 0) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.cancel(NOTIFICATION_BASE_AGENT + agentIndex)
    }

    /**
     * Cancels a task notification by ID.
     */
    fun cancelTaskNotification(context: Context, notificationId: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.cancel(notificationId)
    }
}
