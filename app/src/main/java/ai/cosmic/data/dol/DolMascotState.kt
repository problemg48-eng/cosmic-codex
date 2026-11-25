package ai.cosmic.data.dol

import ai.cosmic.data.orchestration.AutonomousTask
import ai.cosmic.data.orchestration.TaskStatus

/**
 * Expressions supported by the original ANTIGEM mascot, DOL.
 */
enum class DolExpression {
    IDLE,
    WELCOME,
    FOCUSED,
    THINKING,
    ATTENTIVE,
    HAPPY,
    CELEBRATING,
    SAD
}

/**
 * Lifecycle events emitted or observed from [ai.cosmic.data.orchestration.AutonomousTaskEngine].
 */
enum class DolTaskEvent {
    TASK_CREATED,
    TASK_RUNNING,
    TASK_PROGRESS,
    TASK_WAITING_FOR_APPROVAL,
    TASK_COMPLETED,
    TASK_FAILED,
    TASK_CANCELLED
}

/**
 * UI-observable state for DOL.
 */
data class DolUiState(
    val expression: DolExpression = DolExpression.IDLE,
    val displayMessage: String = "Ready when you are, boss.",
    val activeTaskId: String? = null,
    val activeTaskTitle: String? = null,
    val lastEvent: DolTaskEvent? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

data class DolReaction(
    val expression: DolExpression,
    val displayMessage: String,
    val voiceMessage: String?
)

/**
 * Pure, deterministic mapper from task lifecycle events/states to DOL's expression and messages.
 *
 * Guarantees:
 * - Never shows success before the task engine confirms completion.
 * - Never shows failure for a task that is still running or waiting for approval.
 */
object DolTaskMapper {

    const val MSG_COMPLETED_DISPLAY =
        "Boss, your task is complete. I've finished the work. You can review the results now."
    const val MSG_COMPLETED_VOICE =
        "Boss, your task has been completed successfully. The results are ready for your review."

    const val MSG_FAILED_DISPLAY =
        "Boss, I couldn't complete this task. I've recorded the error details. Let's review what went wrong."
    const val MSG_FAILED_VOICE =
        "Boss, the task could not be completed. Please check the error report."

    const val MSG_RUNNING_DISPLAY =
        "I'm working on it, boss. I'll let you know when it's ready."
    const val MSG_RUNNING_VOICE =
        "Boss, I'm working on your task."

    const val MSG_APPROVAL_DISPLAY =
        "Boss, I need your approval before continuing."
    const val MSG_APPROVAL_VOICE =
        "Boss, your approval is required before I continue."

    fun mapEvent(event: DolTaskEvent, isMajorCompletion: Boolean = false): DolReaction = when (event) {
        DolTaskEvent.TASK_CREATED -> DolReaction(
            expression = DolExpression.WELCOME,
            displayMessage = "Hello boss! I'm ready to start this task.",
            voiceMessage = null
        )
        DolTaskEvent.TASK_RUNNING -> DolReaction(
            expression = DolExpression.FOCUSED,
            displayMessage = MSG_RUNNING_DISPLAY,
            voiceMessage = MSG_RUNNING_VOICE
        )
        DolTaskEvent.TASK_PROGRESS -> DolReaction(
            expression = DolExpression.THINKING,
            displayMessage = MSG_RUNNING_DISPLAY,
            voiceMessage = null
        )
        DolTaskEvent.TASK_WAITING_FOR_APPROVAL -> DolReaction(
            expression = DolExpression.ATTENTIVE,
            displayMessage = MSG_APPROVAL_DISPLAY,
            voiceMessage = MSG_APPROVAL_VOICE
        )
        DolTaskEvent.TASK_COMPLETED -> DolReaction(
            expression = if (isMajorCompletion) DolExpression.CELEBRATING else DolExpression.HAPPY,
            displayMessage = MSG_COMPLETED_DISPLAY,
            voiceMessage = MSG_COMPLETED_VOICE
        )
        DolTaskEvent.TASK_FAILED -> DolReaction(
            expression = DolExpression.SAD,
            displayMessage = MSG_FAILED_DISPLAY,
            voiceMessage = MSG_FAILED_VOICE
        )
        DolTaskEvent.TASK_CANCELLED -> DolReaction(
            expression = DolExpression.IDLE,
            displayMessage = "Task cancelled, boss. Standing by.",
            voiceMessage = null
        )
    }

    /**
     * Maps an [AutonomousTask] snapshot to a [DolTaskEvent].
     * Never returns [DolTaskEvent.TASK_COMPLETED] unless `status == TaskStatus.COMPLETED && verified`.
     */
    fun eventForTask(task: AutonomousTask): DolTaskEvent = when (task.status) {
        TaskStatus.PENDING, TaskStatus.PLANNING -> DolTaskEvent.TASK_CREATED
        TaskStatus.RUNNING -> if (task.progressPercent > 0) DolTaskEvent.TASK_PROGRESS else DolTaskEvent.TASK_RUNNING
        TaskStatus.AWAITING_APPROVAL -> DolTaskEvent.TASK_WAITING_FOR_APPROVAL
        TaskStatus.COMPLETED -> if (task.verified) DolTaskEvent.TASK_COMPLETED else DolTaskEvent.TASK_PROGRESS
        TaskStatus.FAILED -> DolTaskEvent.TASK_FAILED
        TaskStatus.CANCELLED, TaskStatus.PAUSED -> DolTaskEvent.TASK_CANCELLED
    }
}
