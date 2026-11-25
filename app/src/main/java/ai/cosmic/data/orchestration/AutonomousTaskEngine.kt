package ai.cosmic.data.orchestration

import android.content.Context
import android.util.Log
import ai.cosmic.data.agent.adapter.AgentAdapterRegistry
import ai.cosmic.data.agent.adapter.AgentInstallStatus
import ai.cosmic.data.agent.adapter.AgentStreamEvent
import ai.cosmic.data.security.UserConfirm
import ai.cosmic.data.storage.AgentNotificationManager
import ai.cosmic.data.storage.StorageGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Central Autonomous Task Engine & Multi-Agent Orchestrator.
 *
 * Coordinates planning, role-based agent assignment, file-lock conflict prevention,
 * user approval gates, real-time notifications, persistence across process death,
 * and post-execution verification.
 */
object AutonomousTaskEngine {
    private const val TAG = "AutonomousTaskEngine"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _tasks = MutableStateFlow<List<AutonomousTask>>(emptyList())
    val tasks: StateFlow<List<AutonomousTask>> = _tasks.asStateFlow()

    private val _activeLocks = MutableStateFlow<Map<String, FileLock>>(emptyMap())
    val activeLocks: StateFlow<Map<String, FileLock>> = _activeLocks.asStateFlow()

    private val runningJobs = ConcurrentHashMap<String, Job>()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            loadFromDisk(context)
            recoverInterruptedTasks(context)
            initialized = true
        }
    }

    /**
     * Recover tasks that were RUNNING when the process was killed by Android.
     * Marks them PAUSED so the user can inspect and resume safely.
     */
    private fun recoverInterruptedTasks(context: Context) {
        var changed = false
        val recovered = _tasks.value.map { task ->
            if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.PLANNING) {
                changed = true
                task.copy(
                    status = TaskStatus.PAUSED,
                    updatedAt = System.currentTimeMillis(),
                    steps = task.steps.map { step ->
                        if (step.status == TaskStatus.RUNNING) step.copy(status = TaskStatus.PAUSED) else step
                    }
                )
            } else {
                task
            }
        }
        if (changed) {
            _tasks.value = recovered
            saveToDisk(context)
            Log.i(TAG, "Recovered interrupted tasks into PAUSED state")
        }
    }

    /**
     * Creates a structured multi-step execution plan for [goal] in [projectPath],
     * assigning roles to real available agents from [AgentAdapterRegistry].
     */
    suspend fun createTask(context: Context, goal: String, projectPath: String): AutonomousTask {
        init(context)
        AgentAdapterRegistry.refreshAll(context)
        val availableAgents = AgentAdapterRegistry.descriptors.value
            .filter { it.installStatus == AgentInstallStatus.INSTALLED }
        val primaryAgentId = availableAgents.firstOrNull()?.id ?: "antigravity"
        val devAgentId = availableAgents.firstOrNull { it.id in listOf("codex", "claude", "opencode", "aider") }?.id
            ?: primaryAgentId

        val taskId = "task_${UUID.randomUUID().toString().take(8)}"
        val title = goal.trim().lineSequence().firstOrNull()?.take(60)?.ifBlank { "Autonomous Mission" }
            ?: "Autonomous Mission"

        val steps = listOf(
            TaskStep(
                id = "${taskId}_s1",
                index = 1,
                title = "Analyze requirements & workspace",
                description = "Inspect $projectPath and plan implementation for: $goal",
                role = AgentRole.ARCHITECT,
                assignedAgentId = primaryAgentId
            ),
            TaskStep(
                id = "${taskId}_s2",
                index = 2,
                title = "Implement solution",
                description = "Apply code changes and create required files in $projectPath",
                role = AgentRole.DEVELOPER,
                assignedAgentId = devAgentId,
                requiresApproval = true
            ),
            TaskStep(
                id = "${taskId}_s3",
                index = 3,
                title = "Verify & test output",
                description = "Run checks/tests and verify workspace integrity in $projectPath",
                role = AgentRole.TESTER,
                assignedAgentId = primaryAgentId
            )
        )

        val task = AutonomousTask(
            id = taskId,
            title = title,
            goal = goal,
            projectPath = projectPath,
            status = TaskStatus.PENDING,
            steps = steps
        )
        _tasks.value = listOf(task) + _tasks.value
        saveToDisk(context)
        return task
    }

    /** Starts or resumes execution of [taskId] in the background. */
    fun startTask(context: Context, taskId: String) {
        init(context)
        if (runningJobs.containsKey(taskId)) return
        val appContext = context.applicationContext

        val job = scope.launch {
            val notifId = AgentNotificationManager.notifyTaskProgress(
                appContext,
                _tasks.value.firstOrNull { it.id == taskId }?.title ?: "Mission",
                0,
                indeterminate = true
            )
            try {
                updateTask(appContext, taskId) {
                    it.copy(status = TaskStatus.RUNNING, updatedAt = System.currentTimeMillis())
                }

                val taskSnapshot = _tasks.value.firstOrNull { it.id == taskId } ?: return@launch

                // Pre-check storage access if targeting shared storage
                val pathError = StorageGateway.validatePath(appContext, taskSnapshot.projectPath)
                if (pathError != null) {
                    failTask(appContext, taskId, pathError, notifId)
                    return@launch
                }

                for (step in taskSnapshot.steps) {
                    val currentTask = _tasks.value.firstOrNull { it.id == taskId } ?: break
                    if (currentTask.status == TaskStatus.CANCELLED || currentTask.status == TaskStatus.PAUSED) {
                        AgentNotificationManager.cancelTaskNotification(appContext, notifId)
                        return@launch
                    }
                    if (step.status == TaskStatus.COMPLETED) continue

                    // Conflict detection: acquire locks on target files
                    if (!acquireFileLocks(taskId, step)) {
                        failTask(appContext, taskId, "File lock conflict on ${step.targetFiles.joinToString()}", notifId)
                        return@launch
                    }

                    try {
                        if (step.requiresApproval) {
                            updateStep(appContext, taskId, step.id) { it.copy(status = TaskStatus.AWAITING_APPROVAL) }
                            AgentNotificationManager.notifyPermissionRequest(
                                appContext,
                                "Approval needed: ${step.title}",
                                "Agent ${step.assignedAgentId} requests permission to execute step ${step.index}"
                            )
                            val approved = UserConfirm.ask(
                                context = appContext,
                                title = "Approve ${step.role.label} Step",
                                message = "${step.title}\n\nAgent: ${step.assignedAgentId}\nWorkspace: ${currentTask.projectPath}",
                                confirmLabel = "Approve Step"
                            )
                            if (!approved) {
                                cancelTask(appContext, taskId)
                                AgentNotificationManager.cancelTaskNotification(appContext, notifId)
                                return@launch
                            }
                        }

                        updateStep(appContext, taskId, step.id) { it.copy(status = TaskStatus.RUNNING, error = null) }
                        AgentNotificationManager.notifyAgentWorking(appContext, step.assignedAgentId, step.title)

                        val adapter = AgentAdapterRegistry.getAdapter(step.assignedAgentId)
                        if (adapter == null) {
                            updateStep(appContext, taskId, step.id) {
                                it.copy(status = TaskStatus.FAILED, error = "Agent adapter '${step.assignedAgentId}' not found")
                            }
                            failTask(appContext, taskId, "Missing agent adapter: ${step.assignedAgentId}", notifId)
                            return@launch
                        }

                        val outputBuilder = StringBuilder()
                        var stepError: String? = null
                        adapter.executeTask(appContext, step.description, currentTask.projectPath).collect { ev ->
                            when (ev) {
                                is AgentStreamEvent.TextDelta -> outputBuilder.append(ev.text)
                                is AgentStreamEvent.ToolCall -> outputBuilder.append("[tool:${ev.toolName}] ${ev.summary}\n")
                                is AgentStreamEvent.Error -> stepError = ev.message
                                is AgentStreamEvent.Completed -> outputBuilder.append("\n${ev.summary}")
                                is AgentStreamEvent.Started -> {}
                            }
                        }

                        if (stepError != null) {
                            updateStep(appContext, taskId, step.id) {
                                it.copy(status = TaskStatus.FAILED, output = outputBuilder.toString(), error = stepError)
                            }
                            failTask(appContext, taskId, stepError!!, notifId)
                            return@launch
                        }

                        updateStep(appContext, taskId, step.id) {
                            it.copy(status = TaskStatus.COMPLETED, output = outputBuilder.toString())
                        }
                        val updated = _tasks.value.firstOrNull { it.id == taskId }
                        if (updated != null) {
                            AgentNotificationManager.updateTaskProgress(
                                appContext, notifId, updated.title, updated.progressPercent
                            )
                        }
                    } finally {
                        releaseFileLocks(taskId, step.id)
                        AgentNotificationManager.cancelAgentNotification(appContext)
                    }
                }

                // Verification gate before marking COMPLETED
                val finalTask = _tasks.value.firstOrNull { it.id == taskId } ?: return@launch
                val allStepsDone = finalTask.steps.isNotEmpty() && finalTask.steps.all { it.status == TaskStatus.COMPLETED }
                val workspaceExists = File(finalTask.projectPath).exists()
                if (allStepsDone && workspaceExists) {
                    updateTask(appContext, taskId) {
                        it.copy(
                            status = TaskStatus.COMPLETED,
                            verified = true,
                            verificationSummary = "All ${it.steps.size} steps completed and workspace verified.",
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    AgentNotificationManager.notifyTaskCompleted(
                        appContext,
                        finalTask.title,
                        "Completed ${finalTask.steps.size} steps",
                        notifId
                    )
                } else {
                    failTask(appContext, taskId, "Verification failed: workspace or step incomplete", notifId)
                }
            } catch (_: CancellationException) {
                AgentNotificationManager.cancelTaskNotification(appContext, notifId)
            } catch (e: Exception) {
                failTask(appContext, taskId, e.message ?: "Unexpected engine error", notifId)
            } finally {
                runningJobs.remove(taskId)
            }
        }
        runningJobs[taskId] = job
    }

    fun pauseTask(context: Context, taskId: String) {
        runningJobs.remove(taskId)?.cancel()
        updateTask(context, taskId) {
            it.copy(
                status = TaskStatus.PAUSED,
                updatedAt = System.currentTimeMillis(),
                steps = it.steps.map { s -> if (s.status == TaskStatus.RUNNING) s.copy(status = TaskStatus.PAUSED) else s }
            )
        }
    }

    fun cancelTask(context: Context, taskId: String) {
        runningJobs.remove(taskId)?.cancel()
        val task = _tasks.value.firstOrNull { it.id == taskId }
        task?.steps?.forEach { releaseFileLocks(taskId, it.id) }
        updateTask(context, taskId) {
            it.copy(
                status = TaskStatus.CANCELLED,
                updatedAt = System.currentTimeMillis(),
                steps = it.steps.map { s ->
                    if (s.status == TaskStatus.RUNNING || s.status == TaskStatus.AWAITING_APPROVAL) {
                        s.copy(status = TaskStatus.CANCELLED)
                    } else s
                }
            )
        }
    }

    private fun failTask(context: Context, taskId: String, reason: String, notifId: Int) {
        val title = _tasks.value.firstOrNull { it.id == taskId }?.title ?: "Mission"
        updateTask(context, taskId) {
            it.copy(
                status = TaskStatus.FAILED,
                verificationSummary = reason,
                updatedAt = System.currentTimeMillis()
            )
        }
        AgentNotificationManager.notifyTaskFailed(context, title, reason, notifId)
    }

    @Synchronized
    private fun acquireFileLocks(taskId: String, step: TaskStep): Boolean {
        if (step.targetFiles.isEmpty()) return true
        val current = _activeLocks.value.toMutableMap()
        for (file in step.targetFiles) {
            val canonical = runCatching { File(file).canonicalPath }.getOrDefault(file)
            val existing = current[canonical]
            if (existing != null && existing.taskId != taskId) {
                return false
            }
        }
        for (file in step.targetFiles) {
            val canonical = runCatching { File(file).canonicalPath }.getOrDefault(file)
            current[canonical] = FileLock(canonical, taskId, step.id, step.assignedAgentId)
        }
        _activeLocks.value = current
        return true
    }

    @Synchronized
    private fun releaseFileLocks(taskId: String, stepId: String) {
        val current = _activeLocks.value.filterValues { !(it.taskId == taskId && it.stepId == stepId) }
        _activeLocks.value = current
    }

    private fun updateTask(context: Context, taskId: String, transform: (AutonomousTask) -> AutonomousTask) {
        _tasks.value = _tasks.value.map { if (it.id == taskId) transform(it) else it }
        saveToDisk(context)
    }

    private fun updateStep(
        context: Context,
        taskId: String,
        stepId: String,
        transform: (TaskStep) -> TaskStep
    ) {
        updateTask(context, taskId) { task ->
            task.copy(
                updatedAt = System.currentTimeMillis(),
                steps = task.steps.map { if (it.id == stepId) transform(it) else it }
            )
        }
    }

    private fun storageFile(context: Context): File =
        File(File(context.filesDir, "tasks").apply { mkdirs() }, "tasks.json")

    private fun saveToDisk(context: Context) {
        runCatching {
            storageFile(context).writeText(json.encodeToString(_tasks.value))
        }.onFailure {
            Log.w(TAG, "Failed persisting tasks: ${it.message}")
        }
    }

    private fun loadFromDisk(context: Context) {
        runCatching {
            val f = storageFile(context)
            if (f.exists()) {
                _tasks.value = json.decodeFromString<List<AutonomousTask>>(f.readText())
            }
        }.onFailure {
            Log.w(TAG, "Failed loading persisted tasks: ${it.message}")
        }
    }
}
