package ai.cosmic.data.dol

import android.content.Context
import ai.cosmic.data.orchestration.AutonomousTaskEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Connects [AutonomousTaskEngine] lifecycle updates to DOL's UI state and [DolVoiceManager].
 */
object DolCoordinator {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _uiState = MutableStateFlow(DolUiState())
    val uiState: StateFlow<DolUiState> = _uiState.asStateFlow()

    @Volatile
    private var started = false
    private val lastTaskEvents = mutableMapOf<String, DolTaskEvent>()

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            val appContext = context.applicationContext
            DolVoiceManager.init(appContext)
            AutonomousTaskEngine.init(appContext)

            scope.launch {
                AutonomousTaskEngine.tasks.collect { tasks ->
                    val latest = tasks.firstOrNull() ?: return@collect
                    val event = DolTaskMapper.eventForTask(latest)
                    val previousEvent = lastTaskEvents[latest.id]
                    val isNewTransition = previousEvent != event
                    lastTaskEvents[latest.id] = event

                    val reaction = DolTaskMapper.mapEvent(
                        event = event,
                        isMajorCompletion = latest.steps.size >= 3
                    )

                    _uiState.value = DolUiState(
                        expression = reaction.expression,
                        displayMessage = reaction.displayMessage,
                        activeTaskId = latest.id,
                        activeTaskTitle = latest.title,
                        lastEvent = event,
                        updatedAt = System.currentTimeMillis()
                    )

                    if (isNewTransition) {
                        DolVoiceManager.announce(latest.id, event, reaction.voiceMessage)
                    }
                }
            }
        }
    }
}
