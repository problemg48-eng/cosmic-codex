package ai.cosmic.data.orchestration

import kotlinx.serialization.Serializable

@Serializable
enum class AgentRole(val label: String) {
    PLANNER("Planner"),
    ARCHITECT("Architect"),
    DEVELOPER("Developer"),
    RESEARCHER("Researcher"),
    TESTER("Tester"),
    DEBUGGER("Debugger"),
    REVIEWER("Reviewer")
}

@Serializable
enum class TaskStatus(val label: String) {
    PENDING("Pending"),
    PLANNING("Planning"),
    AWAITING_APPROVAL("Awaiting Approval"),
    RUNNING("Running"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

@Serializable
data class TaskStep(
    val id: String,
    val index: Int,
    val title: String,
    val description: String,
    val role: AgentRole,
    val assignedAgentId: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val targetFiles: List<String> = emptyList(),
    val requiresApproval: Boolean = false,
    val output: String = "",
    val error: String? = null
)

@Serializable
data class AutonomousTask(
    val id: String,
    val title: String,
    val goal: String,
    val projectPath: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val steps: List<TaskStep> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val verified: Boolean = false,
    val verificationSummary: String? = null
) {
    val progressPercent: Int
        get() {
            if (steps.isEmpty()) return 0
            val done = steps.count { it.status == TaskStatus.COMPLETED }
            return ((done * 100) / steps.size).coerceIn(0, 100)
        }
}

data class FileLock(
    val path: String,
    val taskId: String,
    val stepId: String,
    val agentId: String,
    val acquiredAt: Long = System.currentTimeMillis()
)
