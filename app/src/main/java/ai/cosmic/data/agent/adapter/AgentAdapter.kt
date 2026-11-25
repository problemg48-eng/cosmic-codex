package ai.cosmic.data.agent.adapter

import android.content.Context
import ai.cosmic.domain.model.AgentKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Standardized capability flags for an AI coding agent.
 * Adapters must only report capabilities they genuinely support.
 */
enum class AgentCapability(val label: String) {
    CHAT_STREAMING("Streaming Chat"),
    CODE_EDITING("Code Editing"),
    SHELL_EXECUTION("Shell Execution"),
    BROWSER_AUTOMATION("Browser Automation"),
    MCP_TOOLS("MCP Tools"),
    MODEL_SELECTION("Model Selection"),
    REASONING_CONTROLS("Reasoning Controls"),
    IMAGE_ATTACHMENTS("Image Attachments"),
    SESSION_MANAGEMENT("Session Management"),
    SUBAGENT_DELEGATION("Subagent Delegation"),
    MULTI_FILE_REFACTOR("Multi-File Refactor"),
    VOICE_CONTROL("Voice Control")
}

/** How an agent executes on Android. */
enum class AgentRuntimeType(val label: String) {
    BUNDLED_BRIDGE("Bundled Go Bridge"),
    TERMUX_CLI("Termux Linux CLI"),
    NODE_CLI("Termux Node.js CLI"),
    PYTHON_CLI("Termux Python CLI"),
    IDE_EXTENSION_ONLY("Requires Desktop/VS Code IDE"),
    CUSTOM_ENDPOINT("Custom CLI / HTTP Endpoint")
}

/** Real installation and availability status. */
enum class AgentInstallStatus(val label: String) {
    INSTALLED("Installed"),
    NOT_INSTALLED("Not Installed"),
    UNSUPPORTED_ON_ANDROID("Desktop IDE Required"),
    CHECKING("Checking…")
}

/** Authentication state for an agent. */
enum class AgentAuthStatus(val label: String) {
    AUTHENTICATED("Authenticated"),
    UNAUTHENTICATED("Sign-in Required"),
    API_KEY_CONFIGURED("API Key Configured"),
    NOT_APPLICABLE("No Auth Required"),
    UNKNOWN("Unknown")
}

/** Live execution state. */
enum class AgentExecutionState(val label: String) {
    IDLE("Idle"),
    STARTING("Starting"),
    RUNNING("Running"),
    STREAMING("Streaming"),
    WAITING_APPROVAL("Awaiting Approval"),
    ERROR("Error"),
    OFFLINE("Offline")
}

/** Snapshot of an agent adapter's real status. */
data class AgentDescriptor(
    val id: String,
    val name: String,
    val vendor: String,
    val description: String,
    val chatKind: AgentKind? = null,
    val runtimeType: AgentRuntimeType,
    val installStatus: AgentInstallStatus,
    val isOnline: Boolean,
    val authStatus: AgentAuthStatus,
    val version: String? = null,
    val activeModel: String? = null,
    val availableModels: List<String> = emptyList(),
    val executionState: AgentExecutionState = AgentExecutionState.IDLE,
    val capabilities: Set<AgentCapability> = emptySet(),
    val installHint: String? = null,
    val lastError: String? = null
)

/** Streaming output event from an agent task execution. */
sealed class AgentStreamEvent {
    data class Started(val sessionId: String) : AgentStreamEvent()
    data class TextDelta(val text: String) : AgentStreamEvent()
    data class ToolCall(val toolName: String, val summary: String) : AgentStreamEvent()
    data class Error(val message: String) : AgentStreamEvent()
    data class Completed(val summary: String) : AgentStreamEvent()
}

/**
 * Standardized Agent Adapter interface.
 * Every integrated agent (built-in or CLI-based) implements this contract.
 */
interface AgentAdapter {
    val id: String
    val name: String
    val vendor: String
    val description: String
    val runtimeType: AgentRuntimeType
    val capabilities: Set<AgentCapability>
    val state: StateFlow<AgentDescriptor>

    /** Probes real installation, version, auth, and online status. */
    suspend fun refreshStatus(context: Context): AgentDescriptor

    /** Executes a task in [workingDir] and emits streaming events. */
    fun executeTask(
        context: Context,
        prompt: String,
        workingDir: String,
        model: String? = null
    ): Flow<AgentStreamEvent>

    /** Cancels any active execution. */
    fun cancelExecution()
}
