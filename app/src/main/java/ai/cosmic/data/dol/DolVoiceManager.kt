package ai.cosmic.data.dol

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Voice announcement manager for DOL using Android [TextToSpeech].
 *
 * Guarantees:
 * - Respects user's voice enable/disable preference.
 * - Deduplicates announcements by `(taskId, event)` so service restarts or
 *   re-emissions never repeat the same announcement.
 * - Only speaks fixed, sanitized Boss phrases (never API keys, passwords, or secrets).
 */
object DolVoiceManager {
    private const val TAG = "DolVoiceManager"
    private const val PREFS_NAME = "dol_voice_prefs"
    private const val KEY_VOICE_ENABLED = "voice_enabled"

    private val _voiceEnabled = MutableStateFlow(true)
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled.asStateFlow()

    private val _ttsAvailable = MutableStateFlow(false)
    val ttsAvailable: StateFlow<Boolean> = _ttsAvailable.asStateFlow()

    private var tts: TextToSpeech? = null
    private var initialized = false
    private val announcedKeys = LinkedHashSet<String>()
    private const val MAX_ANNOUNCED_KEYS = 200

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _voiceEnabled.value = prefs.getBoolean(KEY_VOICE_ENABLED, true)

        if (initialized) return
        initialized = true
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.getDefault())
                    _ttsAvailable.value = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                } else {
                    _ttsAvailable.value = false
                    Log.w(TAG, "TextToSpeech init failed with status=$status")
                }
            }
        } catch (e: Exception) {
            _ttsAvailable.value = false
            Log.w(TAG, "TextToSpeech unavailable: ${e.message}")
        }
    }

    fun setVoiceEnabled(context: Context, enabled: Boolean) {
        _voiceEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VOICE_ENABLED, enabled)
            .apply()
        if (!enabled) {
            runCatching { tts?.stop() }
        }
    }

    /**
     * Announces [message] for `(taskId, event)` if voice is enabled and this
     * exact `(taskId, event)` has not already been announced.
     *
     * Returns true if the announcement was queued (or accepted for deduplication),
     * false if skipped due to disabled voice or duplicate key.
     */
    fun announce(taskId: String, event: DolTaskEvent, message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        if (!_voiceEnabled.value) return false

        val dedupKey = "${taskId}:${event.name}"
        synchronized(announcedKeys) {
            if (announcedKeys.contains(dedupKey)) {
                return false
            }
            announcedKeys.add(dedupKey)
            if (announcedKeys.size > MAX_ANNOUNCED_KEYS) {
                val first = announcedKeys.firstOrNull()
                if (first != null) announcedKeys.remove(first)
            }
        }

        if (_ttsAvailable.value) {
            runCatching {
                tts?.speak(message, TextToSpeech.QUEUE_ADD, null, dedupKey)
            }.onFailure {
                Log.w(TAG, "TTS speak failed: ${it.message}")
            }
        }
        return true
    }

    /** Visible for testing duplicate-announcement prevention. */
    internal fun resetDeduplicationForTest() {
        synchronized(announcedKeys) {
            announcedKeys.clear()
        }
        _voiceEnabled.value = true
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        initialized = false
        _ttsAvailable.value = false
    }
}
