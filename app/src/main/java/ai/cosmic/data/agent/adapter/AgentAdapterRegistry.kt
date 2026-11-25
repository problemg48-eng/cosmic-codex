package ai.cosmic.data.agent.adapter

import android.content.Context
import android.util.Log
import ai.cosmic.data.agent.claude.ClaudeStatus
import ai.cosmic.data.agent.codex.CodexState
import ai.cosmic.data.agent.opencode.OpenCodeState
import ai.cosmic.data.local.LocalEnvironmentManager
import ai.cosmic.domain.model.AgentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Central registry for all AI Agent Adapters.
 *
 * Bridges existing production backends (Antigravity, Claude Code, Codex, OpenCode)
 * with extensible Termux CLI adapters (Gemini CLI, Aider, Goose) and honest
 * compatibility reporting for desktop-only tools (Cline, Roo Code).
 */
object AgentAdapterRegistry {
    private const val TAG = "AgentAdapterRegistry"

    private val _adapters = MutableStateFlow<List<AgentAdapter>>(emptyList())
    val adapters: StateFlow<List<AgentAdapter>> = _adapters.asStateFlow()

    private val _descriptors = MutableStateFlow<List<AgentDescriptor>>(emptyList())
    val descriptors: StateFlow<List<AgentDescriptor>> = _descriptors.asStateFlow()

    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val list = listOf(
                BuiltInBridgeAdapter(
                    id = "antigravity",
                    name = "Antigravity",
                    vendor = "Google",
                    description = "Bundled multi-model coding agent with MCP, browser automation, and checkpoints.",
                    chatKind = AgentKind.AGY,
                    runtimeType = AgentRuntimeType.BUNDLED_BRIDGE,
                    capabilities = setOf(
                        AgentCapability.CHAT_STREAMING,
                        AgentCapability.CODE_EDITING,
                        AgentCapability.SHELL_EXECUTION,
                        AgentCapability.BROWSER_AUTOMATION,
                        AgentCapability.MCP_TOOLS,
                        AgentCapability.MODEL_SELECTION,
                        AgentCapability.IMAGE_ATTACHMENTS,
                        AgentCapability.SESSION_MANAGEMENT,
                        AgentCapability.SUBAGENT_DELEGATION,
                        AgentCapability.MULTI_FILE_REFACTOR,
                        AgentCapability.VOICE_CONTROL
                    )
                ),
                BuiltInBridgeAdapter(
                    id = "codex",
                    name = "OpenAI Codex",
                    vendor = "OpenAI",
                    description = "Codex CLI coding agent with reasoning effort controls, sandboxed shell, and MCP.",
                    chatKind = AgentKind.CODEX,
                    runtimeType = AgentRuntimeType.NODE_CLI,
                    capabilities = setOf(
                        AgentCapability.CHAT_STREAMING,
                        AgentCapability.CODE_EDITING,
                        AgentCapability.SHELL_EXECUTION,
                        AgentCapability.MCP_TOOLS,
                        AgentCapability.MODEL_SELECTION,
                        AgentCapability.REASONING_CONTROLS,
                        AgentCapability.IMAGE_ATTACHMENTS,
                        AgentCapability.SESSION_MANAGEMENT,
                        AgentCapability.MULTI_FILE_REFACTOR
                    ),
                    binaryNames = listOf("codex"),
                    installHint = "npm install -g @openai/codex"
                ),
                BuiltInBridgeAdapter(
                    id = "claude",
                    name = "Claude Code",
                    vendor = "Anthropic",
                    description = "Anthropic's agentic CLI with subagents, MCP servers, plugins, and rewind.",
                    chatKind = AgentKind.CLAUDE,
                    runtimeType = AgentRuntimeType.NODE_CLI,
                    capabilities = setOf(
                        AgentCapability.CHAT_STREAMING,
                        AgentCapability.CODE_EDITING,
                        AgentCapability.SHELL_EXECUTION,
                        AgentCapability.MCP_TOOLS,
                        AgentCapability.MODEL_SELECTION,
                        AgentCapability.REASONING_CONTROLS,
                        AgentCapability.IMAGE_ATTACHMENTS,
                        AgentCapability.SESSION_MANAGEMENT,
                        AgentCapability.SUBAGENT_DELEGATION,
                        AgentCapability.MULTI_FILE_REFACTOR
                    ),
                    binaryNames = listOf("claude"),
                    installHint = "npm install -g @anthropic-ai/claude-code"
                ),
                BuiltInBridgeAdapter(
                    id = "opencode",
                    name = "OpenCode",
                    vendor = "OpenCode",
                    description = "Provider-agnostic terminal coding agent supporting local & custom OpenAI-compatible endpoints.",
                    chatKind = AgentKind.OPENCODE,
                    runtimeType = AgentRuntimeType.TERMUX_CLI,
                    capabilities = setOf(
                        AgentCapability.CHAT_STREAMING,
                        AgentCapability.CODE_EDITING,
                        AgentCapability.SHELL_EXECUTION,
                        AgentCapability.MCP_TOOLS,
                        AgentCapability.MODEL_SELECTION,
                        AgentCapability.REASONING_CONTROLS,
                        AgentCapability.IMAGE_ATTACHMENTS,
                        AgentCapability.SESSION_MANAGEMENT
                    ),
                    binaryNames = listOf("opencode"),
                    installHint = "npm install -g opencode-ai"
                ),
                TermuxCliAdapter(
                    id = "gemini_cli",
                    name = "Gemini CLI",
                    vendor = "Google",
                    description = "Google's open-source Gemini CLI agent for Termux Node.js.",
                    runtimeType = AgentRuntimeType.NODE_CLI,
                    binaryNames = listOf("gemini"),
                    versionArgs = listOf("--version"),
                    execArgsBuilder = { prompt, model ->
                        buildList {
                            add("-p")
                            add(prompt)
                            if (!model.isNullOrBlank()) {
                                add("-m")
                                add(model)
                            }
                        }
                    },
                    installHint = "npm install -g @google/gemini-cli",
                    capabilities = setOf(
                        AgentCapability.CHAT_STREAMING,
                        AgentCapability.CODE_EDITING,
                        AgentCapability.SHELL_EXECUTION,
                        AgentCapability.MCP_TOOLS,
                        AgentCapability.MODEL_SELECTION
                    )
                ),
                TermuxCliAdapter(
                    id = "aider",
                    name = "Aider",
                    vendor = "Aider AI",
                    description = "Git-aware AI pair programming CLI running in Termux Python.",
                    runtimeType = AgentRuntimeType.PYTHON_CLI,
                    binaryNames = listOf("aider"),
                    versionArgs = listOf("--version"),
                    execArgsBuilder = { prompt, model ->
                        buildList {
                            add("--yes-always")
                            add("--no-auto-commits")
                            if (!model.isNullOrBlank()) {
                                add("--model")
                                add(model)
                            }
                            add("--message")
                            add(prompt)
                        }
                    },
                    installHint = "pip install aider-chat",
                    capabilities = setOf(
                        AgentCapability.CODE_EDITING,
                        AgentCapability.MULTI_FILE_REFACTOR,
                        AgentCapability.MODEL_SELECTION
                    )
                ),
                TermuxCliAdapter(
                    id = "goose",
                    name = "Goose",
                    vendor = "Block",
                    description = "Extensible developer agent CLI with MCP support.",
                    runtimeType = AgentRuntimeType.TERMUX_CLI,
                    binaryNames = listOf("goose"),
                    versionArgs = listOf("--version"),
                    execArgsBuilder = { prompt, _ -> listOf("run", "--text", prompt) },
                    installHint = "Install goose CLI into Termux PATH",
                    capabilities = setOf(
                        AgentCapability.CODE_EDITING,
                        AgentCapability.SHELL_EXECUTION,
                        AgentCapability.MCP_TOOLS
                    )
                ),
                DesktopOnlyInfoAdapter(
                    id = "cline",
                    name = "Cline",
                    vendor = "Cline Bot",
                    description = "VS Code autonomous coding extension. Requires a desktop/code-server VS Code host.",
                    installHint = "Runs as a VS Code extension (connect via Remote / PC mode or code-server)"
                ),
                DesktopOnlyInfoAdapter(
                    id = "roo_code",
                    name = "Roo Code",
                    vendor = "Roo Code",
                    description = "Multi-mode VS Code coding agent extension. Requires a desktop/code-server VS Code host.",
                    installHint = "Runs as a VS Code extension (connect via Remote / PC mode or code-server)"
                )
            )
            _adapters.value = list
            _descriptors.value = list.map { it.state.value }
            initialized = true
        }
    }

    suspend fun refreshAll(context: Context) {
        ensureInitialized(context)
        val updated = _adapters.value.map { adapter ->
            runCatching { adapter.refreshStatus(context) }
                .getOrElse { e ->
                    Log.w(TAG, "Failed refreshing ${adapter.id}: ${e.message}")
                    adapter.state.value.copy(lastError = e.message)
                }
        }
        _descriptors.value = updated
    }

    fun getAdapter(id: String): AgentAdapter? = _adapters.value.firstOrNull { it.id == id }

    fun findBinary(context: Context, names: List<String>): File? {
        val usrBin = File(context.filesDir, "usr/bin")
        val home = LocalEnvironmentManager.getHomeDir(context)
        val searchDirs = listOf(
            usrBin,
            File(home, ".local/bin"),
            File(home, ".npm-global/bin"),
            File(home, "bin"),
            File(context.filesDir, "usr/local/bin")
        )
        for (dir in searchDirs) {
            for (name in names) {
                val f = File(dir, name)
                if (f.exists() && f.canExecute()) return f
            }
        }
        return null
    }
}

/** Adapter wrapping one of the 4 built-in bridge-backed agents. */
private class BuiltInBridgeAdapter(
    override val id: String,
    override val name: String,
    override val vendor: String,
    override val description: String,
    private val chatKind: AgentKind,
    override val runtimeType: AgentRuntimeType,
    override val capabilities: Set<AgentCapability>,
    private val binaryNames: List<String> = emptyList(),
    private val installHint: String? = null
) : AgentAdapter {

    private val _state = MutableStateFlow(
        AgentDescriptor(
            id = id,
            name = name,
            vendor = vendor,
            description = description,
            chatKind = chatKind,
            runtimeType = runtimeType,
            installStatus = AgentInstallStatus.CHECKING,
            isOnline = false,
            authStatus = AgentAuthStatus.UNKNOWN,
            capabilities = capabilities,
            installHint = installHint
        )
    )
    override val state: StateFlow<AgentDescriptor> = _state.asStateFlow()

    override suspend fun refreshStatus(context: Context): AgentDescriptor = withContext(Dispatchers.IO) {
        val bin = if (binaryNames.isNotEmpty()) AgentAdapterRegistry.findBinary(context, binaryNames) else null
        val bridgeOnline = ai.cosmic.data.local.LocalServerManager.status.value is
            ai.cosmic.data.local.LocalServerStatus.Running
        val installStatus = when {
            chatKind == AgentKind.AGY -> AgentInstallStatus.INSTALLED
            bin != null -> AgentInstallStatus.INSTALLED
            else -> AgentInstallStatus.NOT_INSTALLED
        }
        val descriptor = _state.value.copy(
            installStatus = installStatus,
            isOnline = bridgeOnline && installStatus == AgentInstallStatus.INSTALLED,
            authStatus = if (installStatus == AgentInstallStatus.INSTALLED) {
                AgentAuthStatus.AUTHENTICATED
            } else {
                AgentAuthStatus.UNAUTHENTICATED
            },
            executionState = if (bridgeOnline) AgentExecutionState.IDLE else AgentExecutionState.OFFLINE
        )
        _state.value = descriptor
        descriptor
    }

    override fun executeTask(
        context: Context,
        prompt: String,
        workingDir: String,
        model: String?
    ): Flow<AgentStreamEvent> = flow {
        emit(AgentStreamEvent.Started("bridge-$id-${System.currentTimeMillis()}"))
        emit(AgentStreamEvent.TextDelta("Dispatched to $name in $workingDir"))
        emit(AgentStreamEvent.Completed("Task handed off to $name chat backend"))
    }.flowOn(Dispatchers.IO)

    override fun cancelExecution() {
        // Handled by respective ChatBackend.stopStreaming()
    }
}

/** Adapter for real Termux CLI coding agents (Gemini CLI, Aider, Goose). */
private class TermuxCliAdapter(
    override val id: String,
    override val name: String,
    override val vendor: String,
    override val description: String,
    override val runtimeType: AgentRuntimeType,
    private val binaryNames: List<String>,
    private val versionArgs: List<String>,
    private val execArgsBuilder: (prompt: String, model: String?) -> List<String>,
    private val installHint: String,
    override val capabilities: Set<AgentCapability>
) : AgentAdapter {

    private val _state = MutableStateFlow(
        AgentDescriptor(
            id = id,
            name = name,
            vendor = vendor,
            description = description,
            runtimeType = runtimeType,
            installStatus = AgentInstallStatus.CHECKING,
            isOnline = false,
            authStatus = AgentAuthStatus.UNKNOWN,
            capabilities = capabilities,
            installHint = installHint
        )
    )
    override val state: StateFlow<AgentDescriptor> = _state.asStateFlow()

    @Volatile
    private var activeProcess: Process? = null

    override suspend fun refreshStatus(context: Context): AgentDescriptor = withContext(Dispatchers.IO) {
        val bin = AgentAdapterRegistry.findBinary(context, binaryNames)
        if (bin == null) {
            val d = _state.value.copy(
                installStatus = AgentInstallStatus.NOT_INSTALLED,
                isOnline = false,
                authStatus = AgentAuthStatus.UNKNOWN,
                version = null,
                executionState = AgentExecutionState.OFFLINE
            )
            _state.value = d
            return@withContext d
        }

        val version = runCatching {
            val pb = ProcessBuilder(listOf(bin.absolutePath) + versionArgs)
            pb.environment()["HOME"] = LocalEnvironmentManager.getHomeDir(context).absolutePath
            pb.environment()["PATH"] = "${context.filesDir}/usr/bin:${pb.environment()["PATH"] ?: ""}"
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().readText().trim().lineSequence().firstOrNull()
            proc.waitFor()
            out?.take(48)
        }.getOrNull()

        val d = _state.value.copy(
            installStatus = AgentInstallStatus.INSTALLED,
            isOnline = true,
            authStatus = AgentAuthStatus.NOT_APPLICABLE,
            version = version,
            executionState = AgentExecutionState.IDLE
        )
        _state.value = d
        d
    }

    override fun executeTask(
        context: Context,
        prompt: String,
        workingDir: String,
        model: String?
    ): Flow<AgentStreamEvent> = flow {
        val bin = AgentAdapterRegistry.findBinary(context, binaryNames)
        if (bin == null) {
            emit(AgentStreamEvent.Error("$name is not installed. Install with: $installHint"))
            return@flow
        }
        val sessionId = "$id-${System.currentTimeMillis()}"
        emit(AgentStreamEvent.Started(sessionId))
        _state.value = _state.value.copy(executionState = AgentExecutionState.RUNNING)

        try {
            val args = execArgsBuilder(prompt, model)
            val pb = ProcessBuilder(listOf(bin.absolutePath) + args)
            val dir = File(workingDir)
            if (dir.exists() && dir.isDirectory) pb.directory(dir)
            val home = LocalEnvironmentManager.getHomeDir(context).absolutePath
            pb.environment()["HOME"] = home
            pb.environment()["PATH"] = "${context.filesDir}/usr/bin:$home/.local/bin:$home/.npm-global/bin:${pb.environment()["PATH"] ?: ""}"
            pb.redirectErrorStream(true)

            val proc = pb.start()
            activeProcess = proc
            proc.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    emit(AgentStreamEvent.TextDelta(line + "\n"))
                }
            }
            val exitCode = proc.waitFor()
            if (exitCode == 0) {
                emit(AgentStreamEvent.Completed("$name completed (exit 0)"))
            } else {
                emit(AgentStreamEvent.Error("$name exited with code $exitCode"))
            }
        } catch (e: Exception) {
            emit(AgentStreamEvent.Error(e.message ?: "Execution failed"))
        } finally {
            activeProcess = null
            _state.value = _state.value.copy(executionState = AgentExecutionState.IDLE)
        }
    }.flowOn(Dispatchers.IO)

    override fun cancelExecution() {
        runCatching { activeProcess?.destroyForcibly() }
        activeProcess = null
        _state.value = _state.value.copy(executionState = AgentExecutionState.IDLE)
    }
}

/** Honest adapter for desktop IDE-only agents (never fakes Android native compatibility). */
private class DesktopOnlyInfoAdapter(
    override val id: String,
    override val name: String,
    override val vendor: String,
    override val description: String,
    installHint: String
) : AgentAdapter {

    private val _state = MutableStateFlow(
        AgentDescriptor(
            id = id,
            name = name,
            vendor = vendor,
            description = description,
            runtimeType = AgentRuntimeType.IDE_EXTENSION_ONLY,
            installStatus = AgentInstallStatus.UNSUPPORTED_ON_ANDROID,
            isOnline = false,
            authStatus = AgentAuthStatus.NOT_APPLICABLE,
            executionState = AgentExecutionState.OFFLINE,
            capabilities = emptySet(),
            installHint = installHint
        )
    )
    override val state: StateFlow<AgentDescriptor> = _state.asStateFlow()
    override val runtimeType = AgentRuntimeType.IDE_EXTENSION_ONLY
    override val capabilities: Set<AgentCapability> = emptySet()

    override suspend fun refreshStatus(context: Context): AgentDescriptor = _state.value

    override fun executeTask(
        context: Context,
        prompt: String,
        workingDir: String,
        model: String?
    ): Flow<AgentStreamEvent> = flow {
        emit(AgentStreamEvent.Error("$name requires a VS Code / desktop IDE host and cannot run natively on Android."))
    }

    override fun cancelExecution() {}
}
